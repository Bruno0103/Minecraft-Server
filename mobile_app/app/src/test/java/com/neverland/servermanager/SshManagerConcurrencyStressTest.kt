package com.neverland.servermanager

import com.neverland.servermanager.data.ssh.SshManager
import com.neverland.servermanager.domain.model.AuthType
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.SshConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * ==============================================================================
 * Never Land Server Manager - Suíte de Estresse Concorrencial Adversarial
 * (SshManagerConcurrencyStressTest.kt)
 * ==============================================================================
 *
 * Suíte de testes empíricos adversariais desenvolvida pelo Challenger M2.1
 * (Concurrency & Connection Stress Challenger) para desafiar rigorosamente:
 *
 * 1. Serialização estrita via Mutex sob concorrência massiva de comandos assíncronos.
 * 2. Ciclos rápidos e concorrentes de conexão/desconexão e integridade do StateFlow.
 * 3. Cancelamento cooperativo de corrotinas (CancellationException) e liberação do Mutex.
 * 4. Tratamento de timeouts de comandos com withTimeout vs TimeoutCancellationException.
 * 5. Consistência do activeConfig após falhas de conexão ou cancelamentos.
 * 6. Condições de corrida em chamadas compostas como testConnection.
 * ==============================================================================
 */
class SshManagerConcurrencyStressTest {

    private lateinit var validConfig: SshConfig

    @Before
    fun setUp() {
        validConfig = SshConfig(
            host = "157.151.2.91",
            port = 22,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "test_password_123",
            connectionTimeoutSeconds = 5
        )
    }

    /**
     * Cria uma instância de [Session] simulada via Dynamic Proxy da JVM.
     * Permite injetar atrasos, códigos de saída e exceções sem implementar
     * dezenas de stubs voláteis de interface.
     */
    private fun createFakeSession(
        output: String = "NEVERLAND_OK\n",
        exitCode: Int = 0,
        delayMs: Long = 0L,
        throwable: Exception? = null,
        onClose: () -> Unit = {}
    ): Session {
        val commandProxy = Proxy.newProxyInstance(
            Session.Command::class.java.classLoader,
            arrayOf(Session.Command::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getInputStream" -> ByteArrayInputStream(output.toByteArray(Charsets.UTF_8))
                "getErrorStream" -> ByteArrayInputStream(ByteArray(0))
                "getExitStatus" -> exitCode
                "getExitSignal" -> null
                "join" -> {
                    if (delayMs > 0) Thread.sleep(delayMs)
                    null
                }
                "close" -> null
                "isOpen" -> true
                "toString" -> "FakeCommandProxy"
                "hashCode" -> 12345
                "equals" -> args?.getOrNull(0) === this
                else -> null
            }
        } as Session.Command

        return Proxy.newProxyInstance(
            Session::class.java.classLoader,
            arrayOf(Session::class.java)
        ) { _, method, args ->
            when (method.name) {
                "exec" -> {
                    if (throwable != null) throw throwable
                    commandProxy
                }
                "close" -> {
                    onClose()
                    null
                }
                "isOpen" -> true
                "toString" -> "FakeSessionProxy"
                "hashCode" -> 54321
                "equals" -> args?.getOrNull(0) === this
                else -> null
            }
        } as Session
    }

    /**
     * Test Double / Fake de [SSHClient] para controle fino do comportamento SSH.
     */
    open class FakeSSHClient(
        defaultConfig: DefaultConfig = DefaultConfig()
    ) : SSHClient(defaultConfig) {
        var simulateConnected = false
        var simulateAuthenticated = false
        var connectDelayMs = 0L
        var commandDelayMs = 0L
        var simulateCommandOutput = "NEVERLAND_OK\n"
        var simulateExitCode = 0
        var simulateCommandException: Exception? = null
        var simulateConnectException: Exception? = null

        val connectCallCount = AtomicInteger(0)
        val disconnectCallCount = AtomicInteger(0)
        val startSessionCallCount = AtomicInteger(0)
        val activeSessionsCount = AtomicInteger(0)
        val maxConcurrentSessions = AtomicInteger(0)

        var sessionFactory: (() -> Session)? = null

        override fun isConnected(): Boolean = simulateConnected
        override fun isAuthenticated(): Boolean = simulateAuthenticated

        override fun connect(hostname: String?, port: Int) {
            connectCallCount.incrementAndGet()
            if (connectDelayMs > 0) {
                Thread.sleep(connectDelayMs)
            }
            if (simulateConnectException != null) {
                throw simulateConnectException!!
            }
            simulateConnected = true
        }

        override fun authPassword(username: String?, password: String?) {
            simulateAuthenticated = true
        }

        override fun authPublickey(username: String?, vararg keyProviders: KeyProvider?) {
            simulateAuthenticated = true
        }

        override fun authPublickey(username: String?, keyProviders: Iterable<KeyProvider>?) {
            simulateAuthenticated = true
        }

        override fun disconnect() {
            disconnectCallCount.incrementAndGet()
            simulateConnected = false
            simulateAuthenticated = false
        }

        override fun startSession(): Session {
            startSessionCallCount.incrementAndGet()
            val current = activeSessionsCount.incrementAndGet()
            maxConcurrentSessions.updateAndGet { max -> maxOf(max, current) }

            return sessionFactory?.invoke() ?: run {
                val commandProxy = Proxy.newProxyInstance(
                    Session.Command::class.java.classLoader,
                    arrayOf(Session.Command::class.java)
                ) { _, method, args ->
                    when (method.name) {
                        "getInputStream" -> ByteArrayInputStream(simulateCommandOutput.toByteArray(Charsets.UTF_8))
                        "getErrorStream" -> ByteArrayInputStream(ByteArray(0))
                        "getExitStatus" -> simulateExitCode
                        "getExitSignal" -> null
                        "join" -> {
                            if (commandDelayMs > 0) Thread.sleep(commandDelayMs)
                            null
                        }
                        "close" -> null
                        "isOpen" -> true
                        else -> null
                    }
                } as Session.Command

                Proxy.newProxyInstance(
                    Session::class.java.classLoader,
                    arrayOf(Session::class.java)
                ) { _, method, args ->
                    when (method.name) {
                        "exec" -> {
                            if (simulateCommandException != null) throw simulateCommandException!!
                            commandProxy
                        }
                        "close" -> {
                            activeSessionsCount.decrementAndGet()
                            null
                        }
                        "isOpen" -> true
                        else -> null
                    }
                } as Session
            }
        }
    }

    // ==============================================================================
    // 1. DESAFIO EMPÍRICO: Serialização Estrita de Comandos Concorrentes via Mutex
    // ==============================================================================

    /**
     * Valida que 50 corrotinas disparando comandos assíncronos simultâneos são
     * estritamente serializadas pelo Mutex do SshManager, garantindo que
     * maxConcurrentSessions nunca exceda 1 (zero entrelaçamento no socket).
     */
    @Test
    fun testMassiveConcurrentCommandsStrictlySerializedByMutex() = runBlocking {
        val fakeClient = FakeSSHClient()
        fakeClient.commandDelayMs = 25L // Introduz janela de concorrência artificial de 25ms por comando
        fakeClient.simulateCommandOutput = "PLAYER_LIST_OK\n"

        val manager = SshManager(clientFactory = { fakeClient })
        val connectResult = manager.connect(validConfig)
        assertTrue("Conexão inicial deve ser bem-sucedida", connectResult.isSuccess)

        val coroutinesCount = 50
        val results = (1..coroutinesCount).map { index ->
            async(Dispatchers.IO) {
                manager.executeCommand("mc cmd \"list_$index\"", timeoutSeconds = 10)
            }
        }.awaitAll()

        // 1. Todos os 50 comandos devem ser concluídos com sucesso
        assertEquals("Todos os comandos concorrentes devem retornar", coroutinesCount, results.size)
        results.forEachIndexed { i, res ->
            assertTrue("Comando $i deve ter retornado sucesso", res.isSuccess)
            assertEquals("PLAYER_LIST_OK\n", res.getOrNull()!!.output)
        }

        // 2. CRÍTICO: Em nenhum momento mais de 1 sessão esteve aberta concorrentemente!
        assertEquals(
            "O Mutex deve assegurar concorrência máxima de exatamente 1 sessão por vez",
            1,
            fakeClient.maxConcurrentSessions.get()
        )

        // 3. Ao final, todas as sessões devem ter sido devidamente fechadas
        assertEquals(
            "Todas as sessões abertas devem ter sido encerradas após a execução",
            0,
            fakeClient.activeSessionsCount.get()
        )
        assertEquals(
            "O total de sessões criadas deve ser exatamente igual ao número de comandos",
            coroutinesCount,
            fakeClient.startSessionCallCount.get()
        )
    }

    // ==============================================================================
    // 2. DESAFIO EMPÍRICO: Ciclos Rápidos e Concorrentes de Conectar / Desconectar
    // ==============================================================================

    /**
     * Submete a máquina de estados reativa a 40 corrotinas simultâneas disputando
     * conexões e desconexões sucessivas. Valida que o Mutex impede corrupção de
     * estado e que o StateFlow permanece íntegro e determinístico.
     */
    @Test
    fun testRapidConcurrentConnectDisconnectLifecycleStress() = runBlocking {
        val fakeClient = FakeSSHClient()
        fakeClient.connectDelayMs = 5L // Pequeno delay de handshake

        val manager = SshManager(clientFactory = { fakeClient })

        val stateTransitions = ConcurrentLinkedQueue<ConnectionState>()
        val collectorJob = launch(Dispatchers.Default) {
            manager.connectionState.collect { state ->
                stateTransitions.add(state)
            }
        }

        val iterations = 40
        val jobs = (1..iterations).map { i ->
            async(Dispatchers.IO) {
                if (i % 2 == 0) {
                    manager.connect(validConfig)
                } else {
                    manager.disconnect()
                }
            }
        }

        jobs.awaitAll()

        // Força desconexão final controlada
        manager.disconnect()
        collectorJob.cancelAndJoin()

        // O estado final DEVE ser Disconnected
        val finalState = manager.connectionState.value
        assertTrue("Estado final após estresse deve ser Disconnected", finalState.isDisconnected)
        assertFalse("Cliente não pode constar como conectado", manager.isConnected())
        assertNull("activeConfig deve ser nulo após desconexão final", manager.activeConfig.value)

        // Verifica que nenhuma transição de erro desconhecido ocorreu
        val errors = stateTransitions.filterIsInstance<ConnectionState.Error>()
        assertEquals("Não deve haver erros de concorrência ou colisão interna", 0, errors.size)
    }

    // ==============================================================================
    // 3. DESAFIO EMPÍRICO: Cancelamento Cooperativo e Desbloqueio do Mutex
    // ==============================================================================

    /**
     * Valida que o cancelamento de corrotina (CancellationException) durante um
     * comando em execução libera o Mutex imediatamente, permitindo que requisições
     * subsequentes prossigam sem deadlock.
     */
    @Test
    fun testCoroutineCancellationDuringExecuteCommandReleasesMutex() = runBlocking {
        val fakeClient = FakeSSHClient()
        // Comando lento simulado (espera 5 segundos)
        fakeClient.commandDelayMs = 5000L

        val manager = SshManager(clientFactory = { fakeClient })
        manager.connect(validConfig)

        var cancellationCaught = false

        // Inicia um comando lento em background
        val slowJob = launch(Dispatchers.IO) {
            try {
                manager.executeCommand("mc backup", timeoutSeconds = 10)
            } catch (e: CancellationException) {
                cancellationCaught = true
                throw e
            }
        }

        // Aguarda a corrotina adquirir o Mutex e entrar no join
        delay(100)

        // Cancela cooperativamente a corrotina lenta
        slowJob.cancelAndJoin()

        assertTrue("A CancellationException deve ter sido propagada", cancellationCaught)

        // CRUCIAL: Agora dispara um comando rápido para provar empiricamente
        // que o Mutex NÃO ficou bloqueado pelo cancelamento do job anterior!
        fakeClient.commandDelayMs = 0L
        fakeClient.simulateCommandOutput = "QUICK_COMMAND_SUCCESS\n"

        val followUpResult = withTimeout(3000L) {
            manager.executeCommand("echo test", timeoutSeconds = 2)
        }

        assertTrue("O comando subsequente deve executar com sucesso", followUpResult.isSuccess)
        assertEquals("QUICK_COMMAND_SUCCESS\n", followUpResult.getOrNull()!!.output)
    }

    /**
     * Valida que o cancelamento de corrotina durante o connect() libera o Mutex
     * e restaura o estado para Disconnected.
     */
    @Test
    fun testCoroutineCancellationDuringConnectReleasesMutexAndResetsState() = runBlocking {
        val fakeClient = FakeSSHClient()
        fakeClient.connectDelayMs = 5000L // Conexão lenta simulada

        val manager = SshManager(clientFactory = { fakeClient })

        var cancellationCaught = false

        val slowConnectJob = launch(Dispatchers.IO) {
            try {
                manager.connect(validConfig)
            } catch (e: CancellationException) {
                cancellationCaught = true
                throw e
            }
        }

        delay(100) // Aguarda o job adquirir o Mutex

        slowConnectJob.cancelAndJoin()

        assertTrue("CancellationException deve ter sido propagada no connect", cancellationCaught)
        assertTrue(
            "Após cancelamento do connect, o estado deve ser Disconnected",
            manager.connectionState.value.isDisconnected
        )

        // Prova que o Mutex está livre para uma nova conexão imediata
        fakeClient.connectDelayMs = 0L
        val retryResult = withTimeout(3000L) {
            manager.connect(validConfig)
        }

        assertTrue("Nova tentativa de conexão deve ter sucesso sem deadlock", retryResult.isSuccess)
        assertTrue("Estado deve ser Connected", manager.connectionState.value.isConnected)
    }

    // ==============================================================================
    // 4. DESAFIO EMPÍRICO ADVERSARIAL: Vazamento de TimeoutCancellationException
    // ==============================================================================

    /**
     * DESAFIO ADVERSARIAL CRÍTICO:
     * O contrato de ISshManager.executeCommand especifica retorno de Result<CommandResult>.
     * Em SshManager.kt, o autor declarou em mapCommandException:
     *   e is kotlinx.coroutines.TimeoutCancellationException -> IOException("Tempo limite...")
     * Porém, colocou catch (e: CancellationException) { throw e } ANTES de catch (e: Exception)!
     * Como TimeoutCancellationException é uma subclasse de CancellationException,
     * este teste desafia se o timeout interno de comando vaza uma exceção não-tratada
     * (cancelando a corrotina chamadora) em vez de retornar Result.failure(IOException).
     */
    @Test
    fun testExecuteCommandTimeoutDoesNotLeakTimeoutCancellationException() = runBlocking {
        val fakeClient = FakeSSHClient()
        // Comando demora 3000ms, mas o timeout configurado é de 1 segundo
        fakeClient.commandDelayMs = 3000L

        val manager = SshManager(clientFactory = { fakeClient })
        manager.connect(validConfig)

        var timeoutExceptionLeaked = false
        var returnedResult: Result<com.neverland.servermanager.domain.model.CommandResult>? = null

        try {
            // Chamada direta do comando com timeout de 1s
            returnedResult = manager.executeCommand("slow_command", timeoutSeconds = 1)
        } catch (e: TimeoutCancellationException) {
            timeoutExceptionLeaked = true
        } catch (e: CancellationException) {
            timeoutExceptionLeaked = true
        }

        // Se vazou exceção para o chamador, o contrato de Result.failure foi violado!
        assertFalse(
            "VULNERABILIDADE DETECTADA: SshManager.executeCommand deixou vazar TimeoutCancellationException " +
                    "em vez de capturar e retornar Result.failure(IOException)! " +
                    "Isso cancela a corrotina do chamador (ex: ViewModel) inesperadamente.",
            timeoutExceptionLeaked
        )

        assertNotNull("Deve retornar um Result", returnedResult)
        assertTrue("O resultado deve ser de falha", returnedResult!!.isFailure)
        val cause = returnedResult.exceptionOrNull()
        assertTrue(
            "A causa deve ser uma IOException informando tempo limite esgotado (Atual: ${cause?.javaClass?.simpleName}: ${cause?.message})",
            cause is IOException && (cause.message?.contains("Tempo limite") == true)
        )
    }

    // ==============================================================================
    // 5. DESAFIO EMPÍRICO: Vazamento de activeConfig em Conexões Rejeitadas
    // ==============================================================================

    /**
     * Desafia o comportamento de activeConfig quando o SshManager já esteve conectado
     * previamente e sofre uma falha subsequente de conexão.
     * Se a conexão falhou e o estado transicionou para Error, activeConfig não pode
     * continuar apontando para a sessão anterior desfeita.
     */
    @Test
    fun testActiveConfigIntegrityOnFailedSubsequentConnect() = runBlocking {
        val fakeClient = FakeSSHClient()
        val manager = SshManager(clientFactory = { fakeClient })

        // 1. Conexão inicial bem-sucedida
        val firstConnect = manager.connect(validConfig)
        assertTrue(firstConnect.isSuccess)
        assertEquals(validConfig, manager.activeConfig.value)

        // 2. Segunda conexão para host inacessível com falha simulada
        val failingConfig = SshConfig(
            host = "10.255.255.1",
            port = 22,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "wrong",
            connectionTimeoutSeconds = 1
        )
        fakeClient.simulateConnectException = IOException("Host Unreachable")

        val secondConnect = manager.connect(failingConfig)
        assertTrue(secondConnect.isFailure)
        assertTrue(manager.connectionState.value.isError)

        // CRÍTICO: activeConfig deve refletir que a sessão ativa foi desfeita!
        assertNull(
            "activeConfig deve ser nulo após falha de conexão subsequente que desfez o cliente anterior",
            manager.activeConfig.value
        )
    }

    // ==============================================================================
    // 6. DESAFIO EMPÍRICO: TestConnection Medição de Latência e Sincronização
    // ==============================================================================

    /**
     * Valida que testConnection estabelece a conexão, executa echo e atualiza
     * a latência no ConnectionState.Connected.
     */
    @Test
    fun testConnectionMeasuresLatencyAndPreservesConnectedState() = runBlocking {
        val fakeClient = FakeSSHClient()
        fakeClient.commandDelayMs = 50L
        fakeClient.simulateCommandOutput = "NEVERLAND_OK\n"

        val manager = SshManager(clientFactory = { fakeClient })

        val result = manager.testConnection(validConfig)
        assertTrue("testConnection deve retornar sucesso", result.isSuccess)

        val state = manager.connectionState.value
        assertTrue("Estado final de testConnection deve ser Connected", state.isConnected)
        val connectedState = state as ConnectionState.Connected
        assertTrue(
            "Latência medida deve ser >= 50ms (Atual: ${connectedState.latencyMs}ms)",
            connectedState.latencyMs >= 50L
        )
    }

    // ==============================================================================
    // 7. DESAFIO EMPÍRICO: Múltiplas Chamadas Concorrentes a testConnection
    // ==============================================================================

    /**
     * Desafia múltiplas corrotinas chamando testConnection simultaneamente.
     * Valida que todas completam sem deadlock e que a máquina de estados não é corrompida.
     */
    @Test
    fun testConcurrentTestConnectionCallsDoNotDeadlock() = runBlocking {
        val fakeClient = FakeSSHClient()
        fakeClient.commandDelayMs = 10L
        fakeClient.simulateCommandOutput = "NEVERLAND_OK\n"

        val manager = SshManager(clientFactory = { fakeClient })

        val coroutines = 10
        val results = withTimeout(10000L) {
            (1..coroutines).map {
                async(Dispatchers.IO) {
                    manager.testConnection(validConfig)
                }
            }.awaitAll()
        }

        assertEquals(coroutines, results.size)
        results.forEach { res ->
            assertTrue("Cada testConnection deve ter sucesso", res.isSuccess)
        }
        assertTrue("Estado final deve ser Connected", manager.connectionState.value.isConnected)
    }
}
