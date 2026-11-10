package com.neverland.servermanager.e2e.model

import java.io.IOException

// ==============================================================================
// Never Land Server Manager - Modelos de Dados, Construtores e Parsers de Teste
// ==============================================================================
// Este arquivo define as estruturas de dados fundamentais, validadores de entrada,
// construtores de comandos SSH canônicos e parsers de saída do BDS (Bedrock Dedicated
// Server) e do utilitário mc.py (/usr/local/bin/mc).
// Todos os métodos são documentados em Português do Brasil.
// ==============================================================================

/**
 * Tipo de autenticação suportado para a conexão SSH.
 */
enum class AuthType {
    PASSWORD,
    PRIVATE_KEY
}

/**
 * Configuração de parâmetros de rede e autenticação para conexão SSH.
 *
 * @property host Endereço IP ou domínio FQDN da VM (padrão: "157.151.2.91" / "never-land.rocks")
 * @property port Porta TCP do serviço OpenSSH (padrão: 22)
 * @property username Usuário de login no sistema operacional Linux (padrão: "opc")
 * @property authType Método de autenticação (PRIVATE_KEY ou PASSWORD)
 * @property privateKeyPem Conteúdo da chave privada em formato PEM ou OpenSSH
 * @property passphrase Senha opcional para decifrar a chave privada
 * @property password Senha de login caso authType seja PASSWORD
 */
data class SshConfig(
    val host: String = "157.151.2.91",
    val port: Int = 22,
    val username: String = "opc",
    val authType: AuthType = AuthType.PRIVATE_KEY,
    val privateKeyPem: String? = null,
    val passphrase: String? = null,
    val password: String? = null
)

/**
 * Estado operacional detalhado do servidor Bedrock obtido via 'mc status'.
 *
 * @property isActive Indica se a unit systemd está ativa/operacional
 * @property substate Subestado do systemd (ex: "running", "failed", "inactive")
 * @property pid Identificador do processo bedrock_server (null se inativo)
 * @property uptimeText Texto com data/hora de início ou tempo de atividade
 * @property restartsCount Número de reinícios automáticos registrados pelo systemd
 * @property memoryUsedMib Memória física consumida pelo processo BDS em MiB
 * @property memoryLimitMib Limite máximo configurado no cgroup em MiB (padrão: 750 MiB)
 * @property isFifoAvailable Indica se o named pipe /run/bedrock.console está aberto para escrita
 * @property onlinePlayersCount Quantidade de jogadores atualmente conectados
 * @property maxPlayersCount Quantidade máxima de jogadores suportados no servidor (padrão: 10)
 */
data class ServerStatus(
    val isActive: Boolean,
    val substate: String,
    val pid: Int?,
    val uptimeText: String?,
    val restartsCount: Int,
    val memoryUsedMib: Float?,
    val memoryLimitMib: Float?,
    val isFifoAvailable: Boolean,
    val onlinePlayersCount: Int,
    val maxPlayersCount: Int
)

/**
 * Resultado da execução de uma rotina de hot-backup consistente via 'mc backup'.
 *
 * @property success Indica se o backup foi concluído com sucesso
 * @property elapsedSeconds Tempo total decorrido na compactação do mundo e arquivos de config
 * @property filename Nome do arquivo compactado gerado (ex: "backup_never_land_20261109_204000.tar.gz")
 * @property sizeText Tamanho legível do arquivo gerado (ex: "7.5M")
 * @property errorMessage Mensagem de falha caso ocorra erro (ex: espaço insuficiente, LevelDB travado)
 */
data class BackupResult(
    val success: Boolean,
    val elapsedSeconds: Float,
    val filename: String,
    val sizeText: String? = null,
    val errorMessage: String? = null
)

/**
 * Representação de um arquivo de backup preservado na VM (/home/opc/minecraft/backups/).
 */
data class BackupItem(
    val filename: String,
    val sizeText: String,
    val timestamp: String
)

/**
 * Evento de conexão/desconexão de jogador extraído do histórico do journald ('mc jogadores').
 */
data class PlayerConnectionEvent(
    val eventType: String, // "CONNECTED", "DISCONNECTED", "SPAWNED"
    val gamertag: String,
    val xuid: String?,
    val rawLine: String
)

/**
 * Resultado genérico de execução de comando SSH remoto.
 */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val executionTimeMs: Long
)

// ==============================================================================
// Construtor Canônico de Comandos SSH (SshCommandBuilder)
// ==============================================================================

/**
 * Construtor responsável por formatar rigorosamente as strings de comandos SSH
 * que serão despachadas para o utilitário /usr/local/bin/mc no servidor Linux.
 */
object SshCommandBuilder {

    /** Constrói o comando de consulta do status detalhado */
    fun buildStatus(): String = "mc status"

    /** Constrói o comando de verificação rápida de estado do systemd */
    fun buildSystemctlActive(): String = "systemctl is-active bedrock.service"

    /** Constrói o comando de inicialização do serviço BDS */
    fun buildStart(): String = "mc start"

    /**
     * Constrói o comando de parada segura do servidor BDS.
     * OBRIGATÓRIO: A flag -f (--force) DEVE estar sempre presente em chamadas não-TTY
     * para impedir que o script aguarde confirmação interativa do stdin.
     */
    fun buildStop(force: Boolean = true): String {
        return if (force) "mc stop -f" else "mc stop"
    }

    /**
     * Constrói o comando de reinicialização do servidor BDS.
     * @param now Se verdadeiro, passa --now para reinício sem contagem de aviso prévio
     */
    fun buildRestart(now: Boolean = false): String {
        return if (now) "mc restart --now" else "mc restart"
    }

    /** Constrói comando para envio de instrução arbitrária ao console via 'mc cmd' */
    fun buildCmd(command: String): String {
        val sanitized = command.replace("\"", "\\\"")
        return "mc cmd \"$sanitized\""
    }

    /** Constrói comando para transmissão de mensagem pública no chat do jogo */
    fun buildSay(message: String): String {
        val sanitized = message.replace("\"", "\\\"")
        return "mc say \"$sanitized\""
    }

    /** Constrói comando de listagem de jogadores online */
    fun buildList(): String = "mc list"

    /** Constrói comando de consulta do histórico de conexões e XUIDs */
    fun buildJogadores(): String = "mc jogadores"

    /**
     * Constrói comando de expulsão (kick) de um jogador com motivo opcional.
     * Nomes com espaços são obrigatoriamente encapsulados entre aspas.
     */
    fun buildKick(gamertag: String, reason: String? = null): String {
        val cleanGamertag = gamertag.trim().replace("\"", "\\\"")
        return if (reason.isNullOrBlank()) {
            "mc kick \"$cleanGamertag\""
        } else {
            val cleanReason = reason.trim().replace("\"", "\\\"")
            "mc kick \"$cleanGamertag\" \"$cleanReason\""
        }
    }

    /** Constrói comando para adicionar jogador à lista de permissão (allowlist) */
    fun buildWhitelistAdd(gamertag: String): String {
        val cleanGamertag = gamertag.trim().replace("\"", "\\\"")
        return "mc cmd \"allowlist add \\\"$cleanGamertag\\\"\""
    }

    /** Constrói comando para remover jogador da lista de permissão (allowlist) */
    fun buildWhitelistRemove(gamertag: String): String {
        val cleanGamertag = gamertag.trim().replace("\"", "\\\"")
        return "mc cmd \"allowlist remove \\\"$cleanGamertag\\\"\""
    }

    /** Constrói comando para listar jogadores na lista de permissão */
    fun buildWhitelistList(): String = "mc cmd \"allowlist list\""

    /** Constrói comando para recarregar o arquivo allowlist.json no disco */
    fun buildWhitelistReload(): String = "mc cmd \"allowlist reload\""

    /** Constrói comando de disparo manual do hot-backup */
    fun buildBackup(): String = "mc backup"

    /** Constrói comando para listar os backups gravados no disco da VM */
    fun buildBackupsList(): String = "mc backups"

    /** Constrói comando para consultar recursos de hardware da VM (RAM, Swap, Disco) */
    fun buildRecursos(): String = "mc recursos"

    /** Constrói comando para consultar diagnóstico de portas e sockets de rede */
    fun buildRede(): String = "mc rede"

    /** Constrói comando para exibir configurações do server.properties */
    fun buildConfig(): String = "mc config"

    /** Constrói comando para exibir as últimas N linhas de log do journald */
    fun buildLog(lines: Int = 50): String = "mc log $lines"

    /** Constrói comando de teste imediato de conectividade SSH */
    fun buildEchoTest(): String = "echo \"NEVERLAND_OK\""
}

// ==============================================================================
// Validadores de Entrada e Limites (InputValidator)
// ==============================================================================

/**
 * Validador responsável por assegurar a sanidade e segurança dos dados antes
 * de qualquer transmissão via rede ou escrita em canais locais.
 */
object InputValidator {

    /** Valida se a porta TCP está dentro do intervalo permitido (1 a 65535) */
    fun validatePort(port: Int): Result<Int> {
        return if (port in 1..65535) {
            Result.success(port)
        } else {
            Result.failure(IllegalArgumentException("Porta inválida: $port. A porta deve estar entre 1 e 65535."))
        }
    }

    /** Valida se o endereço de host (IP ou FQDN) é válido e não está em branco */
    fun validateHost(host: String): Result<String> {
        val trimmed = host.trim()
        return if (trimmed.isNotEmpty()) {
            Result.success(trimmed)
        } else {
            Result.failure(IllegalArgumentException("O host do servidor não pode estar vazio."))
        }
    }

    /** Valida se o nome de usuário SSH é válido */
    fun validateUsername(username: String): Result<String> {
        val trimmed = username.trim()
        return if (trimmed.isNotEmpty()) {
            Result.success(trimmed)
        } else {
            Result.failure(IllegalArgumentException("O nome de usuário SSH não pode estar vazio."))
        }
    }

    /** Valida o formato da chave privada PEM ou OpenSSH */
    fun validatePrivateKey(pemContent: String): Result<String> {
        val trimmed = pemContent.trim()
        val hasRsaHeader = trimmed.contains("-----BEGIN RSA PRIVATE KEY-----") && trimmed.contains("-----END RSA PRIVATE KEY-----")
        val hasOpenSshHeader = trimmed.contains("-----BEGIN OPENSSH PRIVATE KEY-----") && trimmed.contains("-----END OPENSSH PRIVATE KEY-----")
        val hasGenericHeader = trimmed.contains("-----BEGIN PRIVATE KEY-----") && trimmed.contains("-----END PRIVATE KEY-----")

        return if (hasRsaHeader || hasOpenSshHeader || hasGenericHeader) {
            Result.success(trimmed)
        } else {
            Result.failure(IllegalArgumentException("Formato de chave privada inválido. Esperado formato PEM (PKCS#1/PKCS#8) ou OpenSSH."))
        }
    }

    /**
     * Valida comandos digitados no console.
     * Limita o tamanho a no máximo 4096 bytes (limite POSIX PIPE_BUF para named pipes atômicos).
     */
    fun validateCommand(command: String): Result<String> {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("O comando não pode estar vazio."))
        }
        val byteLength = trimmed.toByteArray(Charsets.UTF_8).size
        if (byteLength > 4096) {
            return Result.failure(IllegalArgumentException("Comando muito longo ($byteLength bytes). O limite atômico do buffer é de 4096 bytes."))
        }
        return Result.success(trimmed)
    }

    /**
     * Sanitiza a Gamertag do jogador, envolvendo-a com aspas se contiver espaços.
     */
    fun sanitizeGamertag(gamertag: String): Result<String> {
        val trimmed = gamertag.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("A Gamertag do jogador não pode estar vazia."))
        }
        return Result.success(trimmed)
    }
}

// ==============================================================================
// Parsers Especializados de Saída Textual
// ==============================================================================

/**
 * Parser para a saída do comando 'mc status' do servidor Never Land.
 */
object ServerStatusParser {

    private val ACTIVE_REGEX = Regex("""•\s*Serviço Systemd:\s*\[(ATIVO\s*/\s*OPERACIONAL|PARADO\s*/\s*INATIVO|FALHA\s*/\s*CRASH)\]\s*\(Substate:\s*(\w+)\)""")
    private val PID_REGEX = Regex("""•\s*PID do Processo:\s*(\d+|Nenhum)""")
    private val MEMORY_REGEX = Regex("""•\s*Memória RAM \(BDS\):\s*([\d.]+)\s*MiB(?:\s*\(Alerta:\s*[\d.]+\s*MiB,\s*Máx:\s*([\d.]+)\s*MiB\))?""")
    private val FIFO_REGEX = Regex("""•\s*Canal Console:\s*.*?\((FIFO operacional\.|FIFO indisponível.*?)\)""")
    private val PLAYERS_REGEX = Regex("""There are (\d+)/(\d+) players online""")

    fun parse(rawOutput: String): ServerStatus {
        if (rawOutput.isBlank()) {
            return ServerStatus(
                isActive = false,
                substate = "inactive",
                pid = null,
                uptimeText = null,
                restartsCount = 0,
                memoryUsedMib = null,
                memoryLimitMib = null,
                isFifoAvailable = false,
                onlinePlayersCount = 0,
                maxPlayersCount = 10
            )
        }

        val activeMatch = ACTIVE_REGEX.find(rawOutput)
        val badge = activeMatch?.groupValues?.get(1)

        // Verificação defensiva de status ativo para prevenir o bug morfológico de substring
        // onde "PARADO / INATIVO".contains("ATIVO") avaliava incorretamente como verdadeiro (true).
        // Checamos a negação explícita de "INATIVO" aliada à presença de "ATIVO", além de
        // suportar fallback resiliente para saídas brutas do systemd e comandos SSH diretos.
        val isActive = if (badge != null) {
            !badge.contains("INATIVO") && badge.contains("ATIVO")
        } else {
            !rawOutput.contains("INATIVO") &&
                !rawOutput.contains("inactive", ignoreCase = true) &&
                (rawOutput.contains("[ATIVO / OPERACIONAL]") || rawOutput.contains("active", ignoreCase = true))
        }

        // Determinação do subestado (substate):
        // Previne a colisão em inglês onde "inactive".contains("active") avaliava incorretamente como "running".
        val substate = activeMatch?.groupValues?.get(2)
            ?: if (!rawOutput.contains("inactive", ignoreCase = true) && rawOutput.contains("active", ignoreCase = true)) {
                "running"
            } else {
                "inactive"
            }

        val pidMatch = PID_REGEX.find(rawOutput)
        val pid = pidMatch?.groupValues?.get(1)?.toIntOrNull()

        val memMatch = MEMORY_REGEX.find(rawOutput)
        val memoryUsed = memMatch?.groupValues?.get(1)?.toFloatOrNull()
        val memoryLimit = memMatch?.groupValues?.get(2)?.toFloatOrNull() ?: 750.0f

        val fifoMatch = FIFO_REGEX.find(rawOutput)
        val isFifoAvailable = fifoMatch?.groupValues?.get(1)?.contains("FIFO operacional") == true

        val playersMatch = PLAYERS_REGEX.find(rawOutput)
        val onlinePlayers = playersMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val maxPlayers = playersMatch?.groupValues?.get(2)?.toIntOrNull() ?: 10

        return ServerStatus(
            isActive = isActive,
            substate = substate,
            pid = pid,
            uptimeText = null,
            restartsCount = 0,
            memoryUsedMib = memoryUsed,
            memoryLimitMib = memoryLimit,
            isFifoAvailable = isFifoAvailable,
            onlinePlayersCount = onlinePlayers,
            maxPlayersCount = maxPlayers
        )
    }
}

/**
 * Parser para a saída do comando 'mc backup'.
 */
object BackupOutputParser {

    private val SUCCESS_REGEX = Regex("""\[OK\]\s*Backup concluído em ([\d.]+)s:\s*([^\s]+)(?:\s*\(([^)]+)\))?""")

    fun parse(rawOutput: String): BackupResult {
        val match = SUCCESS_REGEX.find(rawOutput)
        return if (match != null) {
            val elapsed = match.groupValues[1].toFloatOrNull() ?: 0.0f
            val filename = match.groupValues[2]
            val size = match.groupValues.getOrNull(3)
            BackupResult(
                success = true,
                elapsedSeconds = elapsed,
                filename = filename,
                sizeText = size,
                errorMessage = null
            )
        } else {
            BackupResult(
                success = false,
                elapsedSeconds = 0.0f,
                filename = "",
                sizeText = null,
                errorMessage = rawOutput.trim().ifEmpty { "Falha desconhecida no backup." }
            )
        }
    }
}

/**
 * Parser para a saída do comando 'mc list'.
 */
object PlayerListParser {

    private val LIST_REGEX = Regex("""There are \d+/\d+ players online:(.*)""")

    fun parse(rawOutput: String): List<String> {
        val match = LIST_REGEX.find(rawOutput) ?: return emptyList()
        val playersString = match.groupValues[1].trim()
        if (playersString.isEmpty()) return emptyList()

        return playersString.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

/**
 * Parser para a saída do comando 'mc jogadores' (histórico de conexões e XUIDs).
 */
object JogadoresHistoryParser {

    private val CONNECTED_REGEX = Regex("""Player connected:\s*([^,]+),\s*xuid:\s*(\d+)""")
    private val SPAWNED_REGEX = Regex("""Player Spawned:\s*([^\s]+)\s*xuid:\s*(\d+)""")
    private val DISCONNECTED_REGEX = Regex("""Player disconnected:\s*([^,]+)""")

    fun parse(rawOutput: String): List<PlayerConnectionEvent> {
        val events = mutableListOf<PlayerConnectionEvent>()
        val lines = rawOutput.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val connMatch = CONNECTED_REGEX.find(trimmed)
            if (connMatch != null) {
                events.add(
                    PlayerConnectionEvent(
                        eventType = "CONNECTED",
                        gamertag = connMatch.groupValues[1].trim(),
                        xuid = connMatch.groupValues[2].trim(),
                        rawLine = trimmed
                    )
                )
                continue
            }

            val spawnMatch = SPAWNED_REGEX.find(trimmed)
            if (spawnMatch != null) {
                events.add(
                    PlayerConnectionEvent(
                        eventType = "SPAWNED",
                        gamertag = spawnMatch.groupValues[1].trim(),
                        xuid = spawnMatch.groupValues[2].trim(),
                        rawLine = trimmed
                    )
                )
                continue
            }

            val discMatch = DISCONNECTED_REGEX.find(trimmed)
            if (discMatch != null) {
                events.add(
                    PlayerConnectionEvent(
                        eventType = "DISCONNECTED",
                        gamertag = discMatch.groupValues[1].trim(),
                        xuid = null,
                        rawLine = trimmed
                    )
                )
            }
        }
        return events
    }
}

/**
 * Parser para a saída do comando 'mc backups' (listagem de backups em disco).
 */
object BackupsListParser {

    // Linha típica de 'ls -lh': -rw-r--r-- 1 opc opc 6.8M Nov  9 04:00 backup_never_land_20261109_040004.tar.gz
    private val LS_REGEX = Regex("""([^\s]+)\s+([^\s]+)\s+backup_never_land_(\d+_\d+)\.tar\.gz""")

    fun parse(rawOutput: String): List<BackupItem> {
        val items = mutableListOf<BackupItem>()
        for (line in rawOutput.lines()) {
            val trimmed = line.trim()
            if (!trimmed.endsWith(".tar.gz")) continue

            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size >= 5) {
                val filename = parts.last()
                val size = parts[parts.size - 5]
                items.add(
                    BackupItem(
                        filename = filename,
                        sizeText = size,
                        timestamp = filename.removePrefix("backup_never_land_").removeSuffix(".tar.gz")
                    )
                )
            }
        }
        return items
    }
}
