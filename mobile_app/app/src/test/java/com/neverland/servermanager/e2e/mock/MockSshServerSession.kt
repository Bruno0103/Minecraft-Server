package com.neverland.servermanager.e2e.mock

import com.neverland.servermanager.e2e.model.CommandResult
import com.neverland.servermanager.e2e.model.SshConfig
import java.io.IOException

// ==============================================================================
// Never Land Server Manager - Mock de Sessão SSH e Subsistema BDS
// ==============================================================================
// Este mock simula com absoluta fidelidade as respostas do servidor Oracle Linux,
// do utilitário canônico /usr/local/bin/mc (mc.py) e do daemon Bedrock Dedicated
// Server (BDS), possibilitando testes E2E opacos e determinísticos.
// ==============================================================================

/**
 * Simulador do subsistema de servidor SSH do Never Land.
 */
class MockSshServerSession(
    private var config: SshConfig = SshConfig()
) {
    // Estados do Servidor Simulado
    var isConnected: Boolean = false
        private set

    var serverActive: Boolean = true
    var substate: String = "running"
    var pid: Int? = 68773
    var memoryUsedMib: Float = 528.5f
    var memoryLimitMib: Float = 750.0f
    var fifoAvailable: Boolean = true
    var onlinePlayers: MutableList<String> = mutableListOf()
    var whitelistPlayers: MutableList<String> = mutableListOf("Bruno0103V", "Alex", "Steve")
    var backupFiles: MutableList<String> = mutableListOf(
        "-rw-r--r-- 1 opc opc 6.8M Nov  9 04:00 backup_never_land_20261109_040004.tar.gz",
        "-rw-r--r-- 1 opc opc 7.6M Nov  8 04:00 backup_never_land_20261108_040001.tar.gz"
    )

    // Cenários de Simulação Adversarial
    var simulateTimeout: Boolean = false // Compatibilidade reversa: simula timeout genérico
    var simulateConnectTimeout: Boolean = false // Simula timeout de conexão durante handshake TCP/SSH (15s)
    var simulateCommandTimeout: Boolean = false // Simula timeout de execução de comando no servidor (ex: backup 60s)
    var simulateAuthFailure: Boolean = false
    var simulateNetworkDrop: Boolean = false
    var simulateFifoEnxio: Boolean = false
    var simulateDiskFull: Boolean = false
    var simulateLevelDbLocked: Boolean = false
    var simulatedLatencyMs: Long = 10L

    // Histórico de comandos recebidos para verificação em testes
    val executedCommandsHistory = mutableListOf<String>()

    /**
     * Simula o handshake de conexão e autenticação SSH.
     * Retorna falha caso as flags simulateAuthFailure, simulateConnectTimeout ou simulateTimeout (sem simulateCommandTimeout)
     * estejam ativadas, reproduzindo o comportamento de timeout de socket ou rejeição de credenciais.
     */
    fun connect(): Result<Unit> {
        if (simulateAuthFailure) {
            return Result.failure(SecurityException("SSH Authentication failed for user ${config.username}@${config.host}: invalid credentials or rejected key."))
        }
        // Timeout explícito de conexão
        if (simulateConnectTimeout) {
            return Result.failure(IOException("Connection timed out after 15000ms connecting to ${config.host}:${config.port}."))
        }
        // Se simulateTimeout estiver ativo e simulateCommandTimeout NÃO for o alvo pretendido,
        // simula falha no handshake (mantém suporte a testSsh_TimeoutHandling_ThrowsOn15Seconds)
        if (simulateTimeout && !simulateCommandTimeout) {
            return Result.failure(IOException("Connection timed out after 15000ms connecting to ${config.host}:${config.port}."))
        }
        isConnected = true
        return Result.success(Unit)
    }

    /**
     * Encerra a sessão SSH simulada.
     */
    fun disconnect() {
        isConnected = false
    }

    /**
     * Executa um comando remoto na sessão SSH simulada e retorna a saída textual padrão.
     * Trata timeouts específicos de comando, validação de conectividade e quedas de rede simuladas.
     */
    fun execute(command: String, timeoutSeconds: Int = 15): Result<CommandResult> {
        // Se simulateCommandTimeout estiver explicitamente ativo, simula o timeout durante a execução
        if (simulateCommandTimeout) {
            return Result.failure(IOException("Tempo limite de execução atingido (${timeoutSeconds}s) para o comando: $command"))
        }

        // Validação defensiva de conexão
        if (!isConnected) {
            // Fallback de compatibilidade: se simulateTimeout estiver ativo e não foi especificado connect timeout,
            // atende o teste que pretendia disparar o timeout do comando mesmo após connect() ter retornado erro
            if (simulateTimeout && !simulateConnectTimeout) {
                return Result.failure(IOException("Tempo limite de execução atingido (${timeoutSeconds}s) para o comando: $command"))
            }
            return Result.failure(IllegalStateException("Sessão SSH desconectada. Chame connect() antes de executar comandos."))
        }

        if (simulateNetworkDrop) {
            isConnected = false
            return Result.failure(IOException("Conexão SSH interrompida abruptamente pelo host remoto (Socket closed)."))
        }

        // Simulação genérica de timeout em comando com sessão conectada
        if (simulateTimeout) {
            return Result.failure(IOException("Tempo limite de execução atingido (${timeoutSeconds}s) para o comando: $command"))
        }

        executedCommandsHistory.add(command)

        val trimmed = command.trim()

        // 1. Comando de Teste Rápido
        if (trimmed == "echo \"NEVERLAND_OK\"") {
            return Result.success(CommandResult(0, "NEVERLAND_OK\n", simulatedLatencyMs))
        }

        // 2. Status Geral do Servidor ('mc status')
        if (trimmed == "mc status") {
            return Result.success(CommandResult(0, buildStatusOutput(), simulatedLatencyMs))
        }

        // 3. Status rápido do systemd ('systemctl is-active bedrock.service')
        if (trimmed == "systemctl is-active bedrock.service") {
            val out = if (serverActive) "active\n" else "inactive\n"
            val code = if (serverActive) 0 else 3
            return Result.success(CommandResult(code, out, simulatedLatencyMs))
        }

        // 4. Iniciar Servidor ('mc start')
        if (trimmed == "mc start") {
            serverActive = true
            substate = "running"
            pid = 68773
            fifoAvailable = !simulateFifoEnxio
            val out = """
                [OK] Iniciando serviço bedrock.service via systemd...
                [OK] Servidor Minecraft Bedrock iniciado com sucesso (PID: $pid).
            """.trimIndent()
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 5. Parar Servidor ('mc stop -f' ou 'mc stop')
        if (trimmed.startsWith("mc stop")) {
            if (!trimmed.contains("-f") && !trimmed.contains("--force")) {
                // Simula que o script esperou confirmação interativa do stdin e sofreu timeout
                return Result.failure(IOException("mc stop bloqueado aguardando entrada interativa (s/N). Utilize a flag -f em sessões não-TTY."))
            }
            serverActive = false
            substate = "inactive"
            pid = null
            memoryUsedMib = 0.0f
            val out = """
                [AVISO] Parando servidor Bedrock com salvamento do mundo LevelDB...
                [OK] Servidor parado com sucesso.
            """.trimIndent()
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 6. Reiniciar Servidor ('mc restart' ou 'mc restart --now')
        if (trimmed.startsWith("mc restart")) {
            serverActive = true
            substate = "running"
            pid = (pid ?: 68770) + 1
            fifoAvailable = !simulateFifoEnxio
            val out = if (trimmed.contains("--now")) {
                "[OK] Servidor reiniciado imediatamente com sucesso (PID: $pid)."
            } else {
                "[OK] Transmitido aviso de 10s no chat. Servidor reiniciado com sucesso (PID: $pid)."
            }
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 7. Listagem de Jogadores Online ('mc list')
        if (trimmed == "mc list") {
            val out = if (onlinePlayers.isEmpty()) {
                "[2026-11-09 23:41:13:199 INFO] There are 0/10 players online:\n"
            } else {
                val listStr = onlinePlayers.joinToString(", ")
                "[2026-11-09 23:41:13:199 INFO] There are ${onlinePlayers.size}/10 players online: $listStr\n"
            }
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 8. Expulsão de Jogador ('mc kick "<p>" "<m>"')
        if (trimmed.startsWith("mc kick")) {
            val regex = Regex("""mc kick "(.*?)"(?:\s+"(.*?)")?""")
            val match = regex.find(trimmed)
            val player = match?.groupValues?.get(1) ?: ""
            val reason = match?.groupValues?.getOrNull(2) ?: ""

            return if (onlinePlayers.contains(player)) {
                onlinePlayers.remove(player)
                val out = "[OK] Jogador '$player' expulso com sucesso do servidor (Motivo: '$reason')."
                Result.success(CommandResult(0, out, simulatedLatencyMs))
            } else {
                val out = "[AVISO] Player not found: $player"
                Result.success(CommandResult(1, out, simulatedLatencyMs))
            }
        }

        // 9. Comandos Arbitrários de Console ('mc cmd "..."')
        if (trimmed.startsWith("mc cmd \"")) {
            if (simulateFifoEnxio || !fifoAvailable) {
                return Result.failure(IOException("Erro ao escrever no console: FIFO indisponível (ENXIO - nenhum processo leitor conectado)."))
            }

            val innerCmd = trimmed.removePrefix("mc cmd \"").removeSuffix("\"").replace("\\\"", "\"")

            // Whitelist Add
            if (innerCmd.startsWith("allowlist add") || innerCmd.startsWith("whitelist add")) {
                val targetPlayer = innerCmd.substringAfter("add").trim().trim('"', '\\')
                if (!whitelistPlayers.contains(targetPlayer)) {
                    whitelistPlayers.add(targetPlayer)
                }
                val out = "Player added to allowlist: $targetPlayer"
                return Result.success(CommandResult(0, out, simulatedLatencyMs))
            }

            // Whitelist Remove
            if (innerCmd.startsWith("allowlist remove") || innerCmd.startsWith("whitelist remove")) {
                val targetPlayer = innerCmd.substringAfter("remove").trim().trim('"', '\\')
                whitelistPlayers.remove(targetPlayer)
                val out = "Player removed from allowlist: $targetPlayer"
                return Result.success(CommandResult(0, out, simulatedLatencyMs))
            }

            // Whitelist List
            if (innerCmd == "allowlist list" || innerCmd == "whitelist list") {
                val listFormatted = whitelistPlayers.joinToString(", ")
                val out = "There are ${whitelistPlayers.size} allowlisted players: $listFormatted"
                return Result.success(CommandResult(0, out, simulatedLatencyMs))
            }

            // Whitelist Reload
            if (innerCmd == "allowlist reload" || innerCmd == "whitelist reload") {
                val out = "Allowlist reloaded from allowlist.json."
                return Result.success(CommandResult(0, out, simulatedLatencyMs))
            }

            // Time query
            if (innerCmd == "time query day") {
                return Result.success(CommandResult(0, "Day is 42", simulatedLatencyMs))
            }

            // Resposta genérica
            return Result.success(CommandResult(0, "Command executed: $innerCmd\n[2026-11-09 23:41:20 INFO] OK", simulatedLatencyMs))
        }

        // 10. Chat Broadcast ('mc say "..."')
        if (trimmed.startsWith("mc say \"")) {
            val message = trimmed.removePrefix("mc say \"").removeSuffix("\"").replace("\\\"", "\"")
            val out = "[Server] $message"
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 11. Disparo de Hot-Backup ('mc backup')
        if (trimmed == "mc backup") {
            if (simulateDiskFull) {
                return Result.success(CommandResult(1, "ERRO: Espaço em disco insuficiente na partição /home/opc para gerar o backup.", simulatedLatencyMs))
            }
            if (simulateLevelDbLocked) {
                return Result.success(CommandResult(1, "ERRO: Banco de dados LevelDB bloqueado por outro processo. Não foi possível criar snapshot consistente.", simulatedLatencyMs))
            }
            val filename = "backup_never_land_20261109_204000.tar.gz"
            val out = "[OK] Backup concluído em 4.2s: $filename (7.5M)"
            backupFiles.add(0, "-rw-r--r-- 1 opc opc 7.5M Nov  9 20:40 $filename")
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 12. Histórico de Backups ('mc backups')
        if (trimmed == "mc backups") {
            if (backupFiles.isEmpty()) {
                return Result.success(CommandResult(0, "Nenhum backup encontrado em /home/opc/minecraft/backups/.\n", simulatedLatencyMs))
            }
            val out = backupFiles.joinToString("\n") + "\n"
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 13. Histórico de Jogadores e XUIDs ('mc jogadores')
        if (trimmed == "mc jogadores") {
            val out = """
                Player connected: Bruno0103V, xuid: 2535456249590930
                Player Spawned: Mauriceliof xuid: 2535429455144583, pfid: 98F58B068055
                Player connected: jvdemonio, xuid: 2535472257251668
                Player connected: Um Boy Atoa, xuid: 2535413344930108
                Player disconnected: Mauriceliof
            """.trimIndent()
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 14. Diagnósticos de Recursos, Rede e Configurações
        if (trimmed == "mc recursos") {
            val out = """
                === RECURSOS DO SISTEMA ===
                RAM Total: 0.94 GiB | Usada: 0.52 GiB | Livre: 0.12 GiB
                Swap Total: 5.00 GiB | Usada: 0.15 GiB
                Disco (/): Usado 4.2G de 46G (10%)
            """.trimIndent()
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        if (trimmed == "mc rede") {
            val out = """
                === PORTAS E FIREWALL ===
                19132/udp (RakNet Bedrock): ABERTO
                19133/udp (Telemetria): ABERTO
                19132/tcp (NetherNet WebRTC): ESCUTANDO
            """.trimIndent()
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        if (trimmed == "mc config") {
            val out = """
                server-name=Never Land
                gamemode=survival
                difficulty=hard
                max-players=10
                server-port=19132
                white-list=true
            """.trimIndent()
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        // 15. Leitura de Logs ('mc log 50')
        if (trimmed.startsWith("mc log")) {
            val out = """
                [2026-11-09 23:40:00:100 INFO] Server started.
                [2026-11-09 23:40:01:200 INFO] LevelDB world loaded.
                [2026-11-09 23:40:02:300 INFO] IPv4 supported, port: 19132
            """.trimIndent()
            return Result.success(CommandResult(0, out, simulatedLatencyMs))
        }

        return Result.success(CommandResult(0, "Executed: $trimmed\n", simulatedLatencyMs))
    }

    /**
     * Monta a saída canônica idêntica à emitida por 'mc status' em produção.
     */
    private fun buildStatusOutput(): String {
        val statusHeader = if (serverActive) {
            "• Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: $substate)"
        } else {
            "• Serviço Systemd:   [PARADO / INATIVO] (Substate: $substate)"
        }
        val pidStr = pid?.toString() ?: "Nenhum"
        val fifoStr = if (fifoAvailable && !simulateFifoEnxio) {
            "• Canal Console:     /run/bedrock.console (FIFO operacional.)"
        } else {
            "• Canal Console:     /run/bedrock.console (FIFO indisponível - ENXIO)"
        }
        val playersStr = if (onlinePlayers.isEmpty()) {
            "  [2026-11-09 23:41:13:199 INFO] There are 0/10 players online:"
        } else {
            "  [2026-11-09 23:41:13:199 INFO] There are ${onlinePlayers.size}/10 players online: ${onlinePlayers.joinToString(", ")}"
        }

        return """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===
              $statusHeader
              • PID do Processo:   $pidStr
              • Data de Início:    Mon 2026-11-09 22:44:39 GMT
              • Contagem Restarts: 0
              • Memória RAM (BDS): $memoryUsedMib MiB (Alerta: 600 MiB, Máx: $memoryLimitMib MiB)
              $fifoStr
            --- Jogadores Conectados ---
            $playersStr
        """.trimIndent()
    }
}
