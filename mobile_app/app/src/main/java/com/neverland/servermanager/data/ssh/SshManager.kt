package com.neverland.servermanager.data.ssh

import android.util.Log
import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.CommandResult
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.SshConfig
import com.neverland.servermanager.domain.repository.ISshManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * ==============================================================================
 * Never Land Server Manager - Gerenciador de Comunicação SSH (SshManager.kt)
 * ==============================================================================
 *
 * Implementação de produção do contrato [ISshManager], responsável pela comunicação
 * de rede SSHv2 direta com a máquina virtual Oracle Linux 9.7 na OCI onde reside
 * o servidor de Minecraft Bedrock (Never Land).
 *
 * Funcionalidades e Características Arquiteturais:
 * 1. Protocolo SSHv2 Robusto: Utiliza a biblioteca SSHJ 0.39.0 respaldada pelo provedor
 *    criptográfico Bouncy Castle 1.78.1 (com suporte a chaves Ed25519 e RSA OpenSSH v1).
 * 2. Cifragem em Memória: As chaves privadas PEM são decodificadas diretamente na RAM
 *    via [SSHClient.loadKeys], sem persistir material sensível no disco do dispositivo móvel.
 * 3. Heartbeat Ativo (Keep-Alive): Emite pacotes periódicos a cada 15 segundos prevenindo
 *    quedas de conexão por inatividade em redes móveis (4G/5G) ou firewalls NAT da OCI.
 * 4. Máquina de Estados Reativa: Expõe [ConnectionState] em tempo real via [StateFlow],
 *    assegurando sincronização atômica com a interface gráfica do Jetpack Compose.
 * 5. Serialização com Mutex: Comandos concorrentes no socket são serializados através
 *    de [Mutex], prevenindo colisões no named pipe do servidor (`/run/bedrock.console`).
 * 6. Cancelamento Cooperativo: Respeita o cancelamento de Coroutines propagando
 *    [CancellationException] e fechando canais de sessão no bloco finally.
 * 7. Timeouts Híbridos: 15s para conexões e comandos regulares, e até 90s para rotinas
 *    pesadas de backup do BDS.
 * 8. Log Defensivo: Emissão de logs com proteção contra exceções em testes locais na JVM.
 * ==============================================================================
 */
class SshManager(
    private val defaultConfig: DefaultConfig = DefaultConfig(),
    private val clientFactory: () -> SSHClient = { SSHClient(defaultConfig) }
) : ISshManager {

    companion object {
        private const val TAG = "SshManager"

        /** Timeout padrão para handshake de conexão TCP/SSH (em milissegundos) */
        const val CONNECT_TIMEOUT_MS = 15_000

        /** Timeout padrão para operações de socket (em milissegundos) */
        const val SOCKET_TIMEOUT_MS = 15_000

        /** Intervalo padrão do Heartbeat ativo (em segundos) */
        const val HEARTBEAT_INTERVAL_SECONDS = 15

        /** Timeout padrão para execução de comandos regulares (em segundos) */
        const val DEFAULT_COMMAND_TIMEOUT_SECONDS = 15

        /** Timeout estendido para execução de rotinas de hot-backup (em segundos) */
        const val BACKUP_COMMAND_TIMEOUT_SECONDS = 90

        /** Comando canônico de diagnóstico rápido */
        const val ECHO_TEST_COMMAND = "echo \"NEVERLAND_OK\""

        // Helpers de log defensivos para evitar falhas de runtime na JVM de teste
        private fun logD(tag: String, msg: String) {
            try { Log.d(tag, msg) } catch (_: Throwable) {}
        }
        private fun logI(tag: String, msg: String) {
            try { Log.i(tag, msg) } catch (_: Throwable) {}
        }
        private fun logW(tag: String, msg: String) {
            try { Log.w(tag, msg) } catch (_: Throwable) {}
        }
        private fun logE(tag: String, msg: String, tr: Throwable? = null) {
            try { Log.e(tag, msg, tr) } catch (_: Throwable) {}
        }
    }

    // Instância ativa do cliente SSHJ
    private var sshClient: SSHClient? = null

    // Configuração da última conexão estabelecida
    private var currentConfig: SshConfig? = null

    // Mutex para sincronização thread-safe de operações no socket SSH
    private val connectionMutex = Mutex()

    // Fluxo reativo interno de estado de conexão
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Fluxo reativo da configuração SSH ativa
    private val _activeConfig = MutableStateFlow<SshConfig?>(null)
    override val activeConfig: StateFlow<SshConfig?> = _activeConfig.asStateFlow()

    /**
     * Consulta síncrona imediata da conectividade atual da sessão.
     */
    override fun isConnected(): Boolean {
        return sshClient?.let { it.isConnected && it.isAuthenticated } ?: false
    }

    /**
     * Estabelece uma conexão SSHv2 segura com base nos parâmetros fornecidos em [SshConfig].
     *
     * Integração Arquitetural de Concorrência e Ciclo de Vida:
     * 1. Sincronização Thread-Safe: Todas as operações no socket são serializadas via [connectionMutex.withLock],
     *    garantindo que conexões concorrentes ou reconexões não colidam.
     * 2. Rastreamento Seguro de Recursos ([pendingClient]): A nova instância do [SSHClient] é registrada
     *    temporariamente antes da conexão física. Em caso de cancelamento da corrotina ou falha durante o handshake,
     *    o socket pendente é desconectado defensivamente sem deixar descritores de arquivo ou threads órfãs.
     * 3. Cancelamento Cooperativo Rigoroso ([ensureActive]): Como as chamadas de socket do SSHJ são bloqueantes
     *    (Java I/O síncrono), são injetados múltiplos pontos de verificação [ensureActive] antes e depois de cada
     *    etapa bloqueante (validação, teardown, conexão TCP, handshake e autenticação).
     * 4. Integridade da Máquina de Estados: Se a corrotina for cancelada externamente, o estado é restaurado
     *    imediatamente para [ConnectionState.Disconnected], a configuração ativa é resetada para null
     *    e a [CancellationException] é relançada para preservar o Structured Concurrency do Kotlin Coroutines.
     *
     * @param config Parâmetros de host, porta, usuário e credenciais (senha ou chave privada PEM).
     * @return [Result.success] contendo [Unit] em caso de êxito; [Result.failure] com exceção mapeada em falhas.
     */
    override suspend fun connect(config: SshConfig): Result<Unit> = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            // Referência temporária defensiva para rastrear e encerrar a instância em inicialização
            // caso a corrotina seja cancelada ou falhe antes da promoção à propriedade de classe 'sshClient'.
            var pendingClient: SSHClient? = null

            try {
                // Ponto de Cancelamento 1: Valida se a corrotina chamadora já foi cancelada antes de iniciar
                currentCoroutineContext().ensureActive()

                // 1. Validação prévia de integridade dos parâmetros fornecidos
                val validationResult = config.validate()
                if (validationResult.isFailure) {
                    val error = validationResult.exceptionOrNull()!!
                    // Encerra qualquer sessão residual prévia e limpa o activeConfig
                    disconnectInternal()
                    _connectionState.value = ConnectionState.Error(error.message ?: "Configuração inválida", error)
                    return@withContext Result.failure(error)
                }

                // 2. Reaproveitamento inteligente de conexão caso já esteja ativa com os mesmíssimos parâmetros
                if (isConnected() && currentConfig == config) {
                    logD(TAG, "Reaproveitando conexão SSH ativa para ${config.host}:${config.port}")
                    _connectionState.value = ConnectionState.Connected(
                        host = config.host,
                        port = config.port,
                        latencyMs = 0L
                    )
                    return@withContext Result.success(Unit)
                }

                // 3. Atualiza estado para 'Connecting' e encerra qualquer sessão residual anterior
                _connectionState.value = ConnectionState.Connecting
                disconnectInternal()

                // Ponto de Cancelamento 2: Verifica cancelamento imediatamente após a limpeza de sessões antigas
                currentCoroutineContext().ensureActive()

                logI(TAG, "Iniciando conexão SSH com ${config.username}@${config.host}:${config.port}...")

                // 4-8. Estabelece a conexão TCP e realiza o handshake SSHv2 com retentativa automática
                // contra perda transitória de pacotes SYN em redes móveis (4G/5G/Wi-Fi).
                var connectedClient: SSHClient? = null
                var lastConnectError: Exception? = null
                val maxAttempts = 2

                for (attempt in 1..maxAttempts) {
                    currentCoroutineContext().ensureActive()
                    val candidateClient = clientFactory()
                    pendingClient = candidateClient

                    // Timeout ágil por tentativa (8s) para evitar bloqueio longo em perda de pacote SYN
                    val attemptTimeoutMs = (config.connectionTimeoutSeconds.coerceAtMost(8)) * 1000
                    candidateClient.connectTimeout = attemptTimeoutMs
                    candidateClient.timeout = config.connectionTimeoutSeconds * 1000
                    candidateClient.addHostKeyVerifier(PromiscuousVerifier())

                    val keepAliveSec = if (config.keepAliveIntervalSeconds > 0) {
                        config.keepAliveIntervalSeconds
                    } else {
                        HEARTBEAT_INTERVAL_SECONDS
                    }
                    candidateClient.connection.keepAlive.keepAliveInterval = keepAliveSec

                    try {
                        if (attempt > 1) {
                            logI(TAG, "Retentativa de conexão TCP ($attempt/$maxAttempts) para ${config.host}:${config.port}...")
                        }
                        candidateClient.connect(config.host, config.port)
                        connectedClient = candidateClient
                        break
                    } catch (connectEx: Exception) {
                        lastConnectError = connectEx
                        try {
                            candidateClient.disconnect()
                        } catch (_: Exception) {
                        }
                        if (attempt < maxAttempts) {
                            logW(TAG, "Tentativa $attempt falhou (${connectEx.message}). Aguardando 800ms antes de retentar...")
                            kotlinx.coroutines.delay(800L)
                        }
                    }
                }

                val client = connectedClient ?: throw (lastConnectError
                    ?: java.net.SocketTimeoutException("Tempo esgotado ao conectar em ${config.host}:${config.port}"))

                // Ponto de Cancelamento 4 (CRÍTICO): Detecta se o Job chamador foi cancelado durante o handshake
                currentCoroutineContext().ensureActive()

                // 9. Executa a autenticação conforme o método configurado (Operação Bloqueante)
                when (config.authType) {
                    AuthType.PASSWORD -> {
                        val password = config.password ?: ""
                        logD(TAG, "Autenticando via senha textual...")
                        client.authPassword(config.username, password)
                    }
                    AuthType.PRIVATE_KEY -> {
                        val pem = config.privateKeyPem
                            ?: throw IllegalArgumentException("A chave privada PEM não pode estar vazia.")

                        logD(TAG, "Autenticando via chave privada PEM (RSA/Ed25519)...")

                        // Constrói o PasswordFinder para chaves cifradas com passphrase
                        val passwordFinder = if (!config.passphrase.isNullOrEmpty()) {
                            PasswordUtils.createOneOff(config.passphrase.toCharArray())
                        } else {
                            null
                        }

                        // Carrega a chave criptográfica diretamente da memória RAM
                        val keyProvider = client.loadKeys(pem, null, passwordFinder)
                        client.authPublickey(config.username, keyProvider)
                    }
                }

                // Ponto de Cancelamento 5 (CRÍTICO): Detecta cancelamento ocorrido durante a autenticação
                currentCoroutineContext().ensureActive()

                if (!client.isAuthenticated) {
                    throw UserAuthException("Autenticação SSH rejeitada pelo host para o usuário '${config.username}'.")
                }

                // Ponto de Cancelamento 6: Impede transição indevida para Connected se o escopo foi cancelado
                currentCoroutineContext().ensureActive()

                // 10. Promove a instância pendente a cliente ativo e transiciona o estado para Connected
                sshClient = client
                pendingClient = null // Promovido com sucesso, desvincula a referência pendente
                currentConfig = config
                _activeConfig.value = config
                _connectionState.value = ConnectionState.Connected(
                    host = config.host,
                    port = config.port,
                    latencyMs = 0L
                )

                logI(TAG, "Conexão SSH estabelecida e autenticada com sucesso.")
                Result.success(Unit)

            } catch (e: CancellationException) {
                // ==============================================================================
                // TRATAMENTO DE CANCELAMENTO COOPERATIVO DE CORROTINA (Falha 3):
                // 1. Encerra defensivamente o socket pendente para não vazar recursos no sistema.
                // 2. Executa teardown completo via disconnectInternal() e zera activeConfig.
                // 3. Restaura o estado da máquina de estados para Disconnected.
                // 4. Relança a CancellationException para que o Job pai seja informado.
                // 5. Ao desempilhar, o bloco finally do connectionMutex.withLock libera o Mutex.
                // ==============================================================================
                try {
                    pendingClient?.disconnect()
                } catch (cleanupEx: Exception) {
                    logW(TAG, "Aviso ao encerrar socket pendente após cancelamento: ${cleanupEx.message}")
                }
                pendingClient = null

                disconnectInternal()
                _activeConfig.value = null
                _connectionState.value = ConnectionState.Disconnected

                logW(TAG, "Tentativa de conexão SSH cancelada cooperativamente pela corrotina chamadora.")
                throw e

            } catch (e: Exception) {
                // ==============================================================================
                // TRATAMENTO DE FALHAS DE REDE / AUTENTICAÇÃO (Falha 2):
                // 1. Encerra defensivamente qualquer socket intermediário pendente.
                // 2. Desconecta sessões anteriores e zera atomicamente o activeConfig.
                // 3. Mapeia a exceção de rede em mensagem descritiva em Português.
                // 4. Transiciona o estado para Error e retorna Result.failure(mappedException).
                // ==============================================================================
                try {
                    pendingClient?.disconnect()
                } catch (cleanupEx: Exception) {
                    logW(TAG, "Aviso ao encerrar socket pendente após erro: ${cleanupEx.message}")
                }
                pendingClient = null

                disconnectInternal()
                _activeConfig.value = null
                val mappedException = mapNetworkException(e, config)
                _connectionState.value = ConnectionState.Error(
                    message = mappedException.message ?: "Falha na conexão SSH",
                    cause = mappedException
                )
                logE(TAG, "Falha na conexão SSH: ${mappedException.message}", mappedException)
                Result.failure(mappedException)
            }
        }
    }

    /**
     * Encerra a conexão SSH atual e libera os recursos de socket e threads associados.
     */
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            disconnectInternal()
            _connectionState.value = ConnectionState.Disconnected
            _activeConfig.value = null
        }
    }

    /**
     * Desconexão interna direta (executada dentro de blocos sincronizados pelo mutex).
     *
     * PONTO ÚNICO DE TEARDOWN (Falha 2):
     * Encerra a conexão SSH subjacente, libera os sockets e redefine atomicamente
     * todas as referências da sessão ativa. O bloco 'finally' assegura incondicionalmente
     * que [_activeConfig.value] seja resetado para 'null', prevenindo que configurações
     * zumbis ou sessões encerradas persistam como dados válidos na interface gráfica ou
     * em verificações reativas de estado.
     */
    private fun disconnectInternal() {
        try {
            sshClient?.let { client ->
                if (client.isConnected) {
                    logD(TAG, "Desconectando cliente SSH...")
                    client.disconnect()
                }
            }
        } catch (e: Exception) {
            logW(TAG, "Aviso ao desconectar cliente SSH: ${e.message}")
        } finally {
            sshClient = null
            currentConfig = null
            _activeConfig.value = null // Ponto único de teardown: anulação determinística do activeConfig
        }
    }

    /**
     * Executa o teste rápido de conectividade enviando o comando 'echo "NEVERLAND_OK"'.
     * Mede com precisão a latência total de ida e volta (RTT).
     */
    override suspend fun testConnection(config: SshConfig): Result<CommandResult> = withContext(Dispatchers.IO) {
        val connectResult = connect(config)
        if (connectResult.isFailure) {
            return@withContext Result.failure(connectResult.exceptionOrNull()!!)
        }

        val result = executeCommand(ECHO_TEST_COMMAND, config.connectionTimeoutSeconds)
        if (result.isSuccess) {
            val cmdResult = result.getOrNull()!!
            _connectionState.value = ConnectionState.Connected(
                host = config.host,
                port = config.port,
                latencyMs = cmdResult.executionTimeMs
            )
        }
        result
    }

    /**
     * Executa um comando arbitrário no servidor remoto via canal de sessão SSHv2.
     *
     * @param command Linha de comando a ser executada na VM (ex: "mc status", "mc backup").
     * @param timeoutSeconds Tempo limite máximo de espera em segundos (padrão: 15s).
     * @return [Result.success] com [CommandResult] contendo código de saída, stdout e latência.
     */
    override suspend fun executeCommand(
        command: String,
        timeoutSeconds: Int
    ): Result<CommandResult> = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val client = sshClient
            if (client == null || !client.isConnected || !client.isAuthenticated) {
                val error = IllegalStateException("Sessão SSH desconectada. Estabeleça uma conexão antes de executar comandos.")
                return@withContext Result.failure(error)
            }

            val startTime = System.currentTimeMillis()

            try {
                // Executa o comando envolvido no timeout cooperativo de corrotina
                withTimeout(timeoutSeconds * 1000L) {
                    val session = client.startSession()
                    try {
                        val cmd = session.exec(command)

                        // Lê os fluxos de saída (stdout) e erros (stderr)
                        val stdoutText = cmd.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val stderrText = cmd.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                        // Aguarda a conclusão da execução remota no canal (operação bloqueante de I/O)
                        cmd.join(timeoutSeconds.toLong(), TimeUnit.SECONDS)

                        // Ponto de Cancelamento e Timeout (CRÍTICO):
                        // Como cmd.join é uma chamada Java bloqueante síncrona que não suspende corrotinas,
                        // é imperativo verificar explicitamente se o tempo limite estipulado pelo withTimeout
                        // expirou ou se a corrotina chamadora foi cancelada durante a espera da execução remota.
                        currentCoroutineContext().ensureActive()

                        val exitCode = cmd.exitStatus ?: (if (cmd.exitSignal != null) -1 else 0)
                        val executionTimeMs = System.currentTimeMillis() - startTime

                        // Prioriza stdout; se estiver em branco e houver stderr, utiliza stderr
                        val finalOutput = if (stdoutText.isNotBlank()) stdoutText else stderrText

                        logD(
                            TAG,
                            "Comando '$command' executado com código $exitCode em ${executionTimeMs}ms."
                        )

                        Result.success(
                            CommandResult(
                                exitCode = exitCode,
                                output = finalOutput,
                                executionTimeMs = executionTimeMs
                            )
                        )
                    } finally {
                        // Garante o fechamento limpo da sessão SSH após a execução
                        try {
                            session.close()
                        } catch (e: Exception) {
                            logW(TAG, "Aviso ao fechar canal de sessão SSH: ${e.message}")
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // ==============================================================================
                // TIMEOUT INTERNO DE COMANDO (Falha 1):
                // Captura especificamente a [TimeoutCancellationException] disparada pelo [withTimeout].
                //
                // FUNDAMENTO ARQUITETURAL DE HIERARQUIA DE TIPOS:
                // No Kotlin Coroutines, TimeoutCancellationException herda diretamente de
                // CancellationException. Se este bloco não estivesse posicionado ANTES de
                // catch (e: CancellationException), o timeout seria capturado como cancelamento
                // e relançado (throw e), cancelando indevidamente o escopo da corrotina chamadora
                // (ex: viewModelScope no ViewModel do Compose).
                //
                // Aqui nós convertemos a expiração do temporizador em um Result.failure(IOException),
                // cumprindo o contrato formal da interface ISshManager sem mascarar erros reais.
                // ==============================================================================
                val executionTimeMs = System.currentTimeMillis() - startTime
                val mappedException = mapCommandException(e, command, timeoutSeconds)
                logE(
                    TAG,
                    "Tempo limite esgotado ao executar comando '$command' após ${executionTimeMs}ms: ${mappedException.message}"
                )

                // Caso a conexão física tenha sido interrompida durante o timeout, executa teardown limpo
                if (!client.isConnected) {
                    disconnectInternal()
                    _connectionState.value = ConnectionState.Error(
                        message = "Conexão SSH interrompida: ${mappedException.message}",
                        cause = mappedException
                    )
                }

                Result.failure(mappedException)
            } catch (e: CancellationException) {
                // ==============================================================================
                // CANCELAMENTO COOPERATIVO EXTERNO:
                // Propaga cancelamentos autênticos disparados voluntariamente pelo ciclo de vida
                // do chamador ou cancelamento de Job externo. O Mutex é destravado pelo finally do withLock.
                // ==============================================================================
                logW(TAG, "Execução do comando '$command' cancelada cooperativamente.")
                throw e
            } catch (e: Exception) {
                val executionTimeMs = System.currentTimeMillis() - startTime
                val mappedException = mapCommandException(e, command, timeoutSeconds)
                logE(
                    TAG,
                    "Falha ao executar comando '$command' após ${executionTimeMs}ms: ${mappedException.message}"
                )

                // Se houver erro de transporte (queda da conexão), desvincula o cliente
                if (e is TransportException || !client.isConnected) {
                    disconnectInternal()
                    _connectionState.value = ConnectionState.Error(
                        message = "Conexão SSH interrompida: ${mappedException.message}",
                        cause = mappedException
                    )
                }

                Result.failure(mappedException)
            }
        }
    }

    /**
     * Mapeia exceções de rede e autenticação de baixo nível em mensagens amigáveis em Português.
     */
    private fun mapNetworkException(e: Exception, config: SshConfig): Exception {
        return when (e) {
            is ConnectException -> IOException(
                "Não foi possível conectar a ${config.host}:${config.port}. Verifique se o servidor está ligado e a porta SSH está acessível.",
                e
            )
            is SocketTimeoutException -> IOException(
                "Tempo limite de conexão esgotado (timeout ${config.connectionTimeoutSeconds}s) ao conectar a ${config.host}:${config.port}.",
                e
            )
            is UserAuthException -> SecurityException(
                "Falha na autenticação SSH para o usuário '${config.username}'. Credenciais inválidas ou chave rejeitada.",
                e
            )
            is TransportException -> IOException(
                "Falha de transporte SSH: conexão interrompida pelo host remoto ou erro criptográfico.",
                e
            )
            is IllegalArgumentException -> e
            else -> IOException("Erro ao conectar via SSH: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * Mapeia exceções de tempo limite e canal durante a execução de comandos.
     */
    private fun mapCommandException(e: Exception, command: String, timeoutSeconds: Int): Exception {
        return when {
            e is kotlinx.coroutines.TimeoutCancellationException ||
                    (e is ConnectionException && e.message?.contains("Timeout", ignoreCase = true) == true) -> {
                IOException("Tempo limite de execução atingido (${timeoutSeconds}s) para o comando: $command", e)
            }
            e is TransportException -> {
                IOException("Conexão SSH interrompida abruptamente pelo host remoto durante a execução do comando.", e)
            }
            else -> IOException("Erro durante a execução do comando '$command': ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
}
