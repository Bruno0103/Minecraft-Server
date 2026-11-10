package com.neverland.servermanager.data.repository

import com.neverland.servermanager.data.parser.OutputParsers
import com.neverland.servermanager.data.parser.StatusParser
import com.neverland.servermanager.domain.model.BackupFileEntry
import com.neverland.servermanager.domain.model.BackupResult
import com.neverland.servermanager.domain.model.CommandResult
import com.neverland.servermanager.domain.model.ConsoleEntry
import com.neverland.servermanager.domain.model.OnlinePlayer
import com.neverland.servermanager.domain.model.PlayerConnectionEvent
import com.neverland.servermanager.domain.model.ServerStatus
import com.neverland.servermanager.domain.model.WhitelistEntry
import com.neverland.servermanager.domain.repository.IServerRepository
import com.neverland.servermanager.domain.repository.ISshManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * ==============================================================================
 * Never Land Server Manager - Implementação do Repositório (ServerRepositoryImpl.kt)
 * ==============================================================================
 *
 * Implementação completa de produção do contrato [IServerRepository].
 *
 * Responsável por coordenar a execução de comandos remotos via [ISshManager],
 * acionar o [StatusParser] e [OutputParsers] para transformar saídas textuais
 * em modelos de domínio imutáveis e aplicar estratégias de contingência e fallback
 * caso o utilitário Python `mc.py` (`/usr/local/bin/mc`) não esteja no PATH padrão
 * da sessão SSH não-interativa.
 *
 * Princípios de Resiliência:
 * 1. Fallback Inteligente de Caminho (`mc` -> `python3 /home/opc/minecraft/mc.py` -> comandos nativos):
 *    Em sessões SSH exec (`non-login shell`), `/usr/local/bin` pode estar presente ou o
 *    script pode residir diretamente em `/home/opc/minecraft/mc.py`. O repositório
 *    garante execução transparente em ambos os cenários.
 * 2. Sanitização Contra Command Injection: Argumentos fornecidos pelo usuário (como
 *    Gamertags, mensagens de chat ou comandos de console) têm aspas e caracteres
 *    especiais escapados antes da interpolação no shell remoto.
 * 3. Timeouts Diferenciados e Seguros:
 *    - Status, logs e consultas rápidas: 15 segundos.
 *    - Inicialização (Start): 30 segundos.
 *    - Parada graciosa (Stop): 65 segundos (permite que `bedrock_stop.sh` salve chunks no LevelDB).
 *    - Reinicialização (Restart): 75 segundos.
 *    - Hot-Backup Consistente (`mc backup`): 90 segundos.
 * ==============================================================================
 */
class ServerRepositoryImpl(
    private val sshManager: ISshManager
) : IServerRepository {

    /**
     * Escapa aspas duplas, barras invertidas, crases e cifrões para uso seguro dentro de aspas duplas no Bash.
     */
    private fun escapeForDoubleQuotes(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("`", "\\`")
    }

    // ==========================================================================
    // Marco 3: Telemetria de Status, Processador (CPU) e Ciclo de Vida
    // ==========================================================================

    /**
     * Constrói um comando composto de shell que executa [baseCommand] enquanto mede,
     * em janela diferencial real no kernel (`/proc/stat`), a utilização percentual global
     * da CPU da máquina virtual, o consumo específico de CPU do processo `bedrock_server`,
     * a carga média (`Load Average` de 1m/5m/15m), o total de vCPUs (`nproc`) e o modelo
     * do processador da instância OCI.
     *
     * Estratégia de Latência Zero:
     * A primeira leitura de `/proc/stat` ocorre ANTES do [baseCommand] (`mc status`) e a
     * segunda leitura ocorre IMEDIATAMENTE APÓS, aproveitando o tempo natural de execução do
     * próprio `mc status` (~1s) como janela de amostragem de CPU sem adicionar atraso extra.
     */
    private fun buildStatusWithCpuTelemetryCommand(baseCommand: String): String {
        return buildString {
            append("read -r _ u1 n1 s1 i1 w1 x1 y1 z1 _ < /proc/stat 2>/dev/null || true ; ")
            append(baseCommand)
            append(" ; echo '' ; ")
            append("read -r _ u2 n2 s2 i2 w2 x2 y2 z2 _ < /proc/stat 2>/dev/null || true ; ")
            append("t1=$((u1+n1+s1+i1+w1+x1+y1+z1)) ; t2=$((u2+n2+s2+i2+w2+x2+y2+z2)) ; ")
            append("if [ \"\$t2\" -le \"\$t1\" ]; then sleep 0.15 ; read -r _ u2 n2 s2 i2 w2 x2 y2 z2 _ < /proc/stat 2>/dev/null || true ; fi ; ")
            append("awk -v u1=\"\$((u1+n1+s1+x1+y1+z1))\" -v t1=\"\$((u1+n1+s1+i1+w1+x1+y1+z1))\" ")
            append("-v u2=\"\$((u2+n2+s2+x2+y2+z2))\" -v t2=\"\$((u2+n2+s2+i2+w2+x2+y2+z2))\" ")
            append("'BEGIN { dt=t2-t1; if(dt>0) printf \"CPU_TOTAL=%.1f\\n\", (u2-u1)*100.0/dt; else print \"CPU_TOTAL=0.0\" }' 2>/dev/null ; ")
            append("ps -C bedrock_server -o %cpu= 2>/dev/null | awk '{s+=\$1} END {printf \"CPU_BDS=%.1f\\n\", s+0}' ; ")
            append("awk '{printf \"CPU_LOAD=%s / %s / %s\\n\", \$1, \$2, \$3}' /proc/loadavg 2>/dev/null ; ")
            append("echo \"CPU_CORES=\$(nproc 2>/dev/null || echo 2)\" ; ")
            append("cpu_mod=\$(awk -F: '/model name/{gsub(/^[ \\t]+/, \"\", \$2); print \$2; exit}' /proc/cpuinfo 2>/dev/null) ; ")
            append("if [ -z \"\$cpu_mod\" ]; then cpu_mod=\$(lscpu 2>/dev/null | awk -F: '/Model name:/{gsub(/^[ \\t]+/, \"\", \$2); print \$2; exit}'); fi ; ")
            append("if [ -n \"\$cpu_mod\" ]; then echo \"CPU_MODEL=\$cpu_mod\"; fi")
        }
    }

    /**
     * Consulta atômica do status do servidor Never Land (incluindo telemetria de RAM e CPU).
     */
    override suspend fun fetchStatus(): Result<ServerStatus> {
        if (!sshManager.isConnected()) {
            return Result.failure(
                IllegalStateException("Não é possível consultar o status: Conexão SSH inativa ou desconectada.")
            )
        }

        // Tentativa primária (Nível 1): Executa 'mc status' enriquecido com telemetria de CPU do kernel
        val mcResult = sshManager.executeCommand(
            buildStatusWithCpuTelemetryCommand("mc status"),
            timeoutSeconds = 15
        )

        if (mcResult.isSuccess) {
            val commandResult = mcResult.getOrThrow()
            val rawOutput = commandResult.output

            if (rawOutput.contains("STATUS OPERACIONAL") || rawOutput.contains("• Serviço Systemd:")) {
                return try {
                    val status = StatusParser.parseServerStatus(rawOutput)
                    Result.success(status)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        // Tentativa de contingência (Nível 2): Consulta descritores do systemd via systemctl show + telemetria de CPU
        val fallbackShowResult = sshManager.executeCommand(
            buildStatusWithCpuTelemetryCommand(
                "systemctl show bedrock.service -p ActiveState -p SubState -p MainPID -p ExecMainStartTimestamp -p MemoryCurrent"
            ),
            timeoutSeconds = 10
        )

        if (fallbackShowResult.isSuccess) {
            val showOutput = fallbackShowResult.getOrThrow().output
            if (showOutput.contains("ActiveState=")) {
                val status = StatusParser.parseFallbackStatus(showOutput)
                return Result.success(status)
            }
        }

        // Tentativa final de contingência (Nível 3): Consulta simples com systemctl is-active
        val fallbackActiveResult = sshManager.executeCommand(
            "systemctl is-active bedrock.service",
            timeoutSeconds = 10
        )

        return if (fallbackActiveResult.isSuccess) {
            val activeOutput = fallbackActiveResult.getOrThrow().output.trim()
            val status = StatusParser.parseFallbackStatus(activeOutput)
            Result.success(status)
        } else {
            Result.failure(
                mcResult.exceptionOrNull()
                    ?: IllegalStateException("Falha catastrófica na obtenção de telemetria do servidor.")
            )
        }
    }

    /**
     * Fluxo reativo e contínuo de observação do status com polling periódico.
     */
    override fun observeStatus(pollIntervalSeconds: Int): Flow<Result<ServerStatus>> = flow {
        val intervalMs = (if (pollIntervalSeconds > 0) pollIntervalSeconds else 30) * 1000L

        while (currentCoroutineContext().isActive) {
            if (sshManager.isConnected()) {
                val statusResult = fetchStatus()
                emit(statusResult)
            } else {
                emit(
                    Result.failure(
                        IllegalStateException("Sessão SSH desconectada. Polling de status pausado.")
                    )
                )
            }

            delay(intervalMs)
        }
    }

    /**
     * Inicia o servidor Bedrock Dedicated Server via 'mc start'.
     */
    override suspend fun startServer(): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada. Impossível iniciar servidor."))
        }

        val primaryResult = sshManager.executeCommand("mc start", timeoutSeconds = 30)
        if (primaryResult.isSuccess && primaryResult.getOrThrow().isSuccess) {
            return primaryResult
        }

        val fallbackResult = sshManager.executeCommand("sudo systemctl start bedrock.service", timeoutSeconds = 30)
        return if (fallbackResult.isSuccess && fallbackResult.getOrThrow().isSuccess) {
            fallbackResult
        } else {
            primaryResult
        }
    }

    /**
     * Para o servidor Bedrock Dedicated Server com salvamento ordenado via 'mc stop -f'.
     */
    override suspend fun stopServer(force: Boolean): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada. Impossível parar servidor."))
        }

        val command = if (force) "mc stop -f" else "mc stop -f"
        val primaryResult = sshManager.executeCommand(command, timeoutSeconds = 65)

        if (primaryResult.isSuccess && primaryResult.getOrThrow().isSuccess) {
            return primaryResult
        }

        val fallbackResult = sshManager.executeCommand("sudo systemctl stop bedrock.service", timeoutSeconds = 65)
        return if (fallbackResult.isSuccess && fallbackResult.getOrThrow().isSuccess) {
            fallbackResult
        } else {
            primaryResult
        }
    }

    /**
     * Reinicia o servidor Bedrock Dedicated Server via 'mc restart --now'.
     */
    override suspend fun restartServer(): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada. Impossível reiniciar servidor."))
        }

        val primaryResult = sshManager.executeCommand("mc restart --now", timeoutSeconds = 75)

        if (primaryResult.isSuccess && primaryResult.getOrThrow().isSuccess) {
            return primaryResult
        }

        val fallbackResult = sshManager.executeCommand("sudo systemctl restart bedrock.service", timeoutSeconds = 75)
        return if (fallbackResult.isSuccess && fallbackResult.getOrThrow().isSuccess) {
            fallbackResult
        } else {
            primaryResult
        }
    }

    // ==========================================================================
    // Marco 4: Console Interativo (FIFO / journald) e Diagnósticos da VM
    // ==========================================================================

    /**
     * Busca as últimas [lines] linhas do log do servidor Bedrock (`mc log` ou `journalctl`).
     */
    override suspend fun fetchConsoleLogs(lines: Int): Result<List<ConsoleEntry>> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val safeLines = lines.coerceIn(10, 300)
        val cmdResult = sshManager.executeCommand(
            "mc log $safeLines || sudo journalctl -u bedrock.service -n $safeLines --no-pager -o cat",
            timeoutSeconds = 15
        )

        return cmdResult.map { result ->
            OutputParsers.parseConsoleLogs(result.output)
        }
    }

    /**
     * Envia um comando ao console do Bedrock ou executa um subcomando do utilitário `mc`.
     *
     * Regras inteligentes de roteamento:
     * - Se o usuário digitar um comando iniciado por `/` (ex.: `/list`, `/time set day`),
     *   a barra inicial é removida automaticamente pois o console FIFO do BDS espera
     *   comandos sem `/` (ex.: `list`, `time set day`).
     * - Se o usuário digitar um subcomando administrativo do próprio `mc` (ex.: `mc recursos`,
     *   `mc rede`, `mc config`, `mc boot`, `mc status`), executa o utilitário `mc` diretamente.
     * - Se o comando começar com `say `, roteia para `mc say "..."` para retorno imediato sem bloqueio.
     * - Caso contrário, despacha via `mc cmd "<comando>"` no socket FIFO `/run/bedrock.console`.
     */
    override suspend fun sendConsoleCommand(command: String): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("O comando não pode estar vazio."))
        }

        // Caso 1: Subcomando direto do utilitário mc (ex: "mc recursos", "mc rede", "mc config")
        if (trimmed.startsWith("mc ", ignoreCase = true) || trimmed.equals("mc", ignoreCase = true)) {
            return sshManager.executeCommand(trimmed, timeoutSeconds = 20)
        }

        // Remove a barra '/' inicial típica de comandos in-game do Minecraft
        val normalizedBdsCmd = if (trimmed.startsWith("/")) trimmed.substring(1).trim() else trimmed

        // Caso 2: Comando de chat 'say' usa o atalho não-bloqueante 'mc say'
        if (normalizedBdsCmd.startsWith("say ", ignoreCase = true)) {
            val messagePart = normalizedBdsCmd.substring(4).trim()
            return sendBroadcastMessage(messagePart)
        }

        val escaped = escapeForDoubleQuotes(normalizedBdsCmd)
        return sshManager.executeCommand("mc cmd \"$escaped\"", timeoutSeconds = 15)
    }

    /**
     * Transmite uma mensagem pública no chat do servidor via `mc say "<message>"`.
     */
    override suspend fun sendBroadcastMessage(message: String): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("A mensagem de broadcast não pode estar vazia."))
        }

        val escaped = escapeForDoubleQuotes(trimmed)
        return sshManager.executeCommand("mc say \"$escaped\"", timeoutSeconds = 15)
    }

    /**
     * Obtém diagnóstico completo de RAM física, Swap NVMe, Disco e Cgroups v2 (`mc recursos`).
     */
    override suspend fun fetchResourcesDiagnostic(): Result<String> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val res = sshManager.executeCommand(
            "mc recursos || (free -h && echo '' && df -h / && echo '' && uptime)",
            timeoutSeconds = 15
        )
        return res.map { StatusParser.stripAnsiEscapes(it.output).trim() }
    }

    /**
     * Obtém diagnóstico de portas no firewalld e sockets de rede (`mc rede`).
     */
    override suspend fun fetchNetworkDiagnostic(): Result<String> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val res = sshManager.executeCommand(
            "mc rede || (sudo firewall-cmd --list-ports && sudo ss -ulpn)",
            timeoutSeconds = 15
        )
        return res.map { StatusParser.stripAnsiEscapes(it.output).trim() }
    }

    /**
     * Obtém as configurações ativas do arquivo `server.properties` (`mc config`).
     */
    override suspend fun fetchServerConfigDiagnostic(): Result<String> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val res = sshManager.executeCommand(
            "mc config || grep -v '^#' /home/opc/minecraft/server/server.properties | grep -v '^$'",
            timeoutSeconds = 15
        )
        return res.map { StatusParser.stripAnsiEscapes(it.output).trim() }
    }

    /**
     * Executa auditoria de autostart e serviços de boot da máquina virtual (`mc boot`).
     */
    override suspend fun fetchBootAuditDiagnostic(): Result<String> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val res = sshManager.executeCommand("mc boot", timeoutSeconds = 15)
        return res.map { StatusParser.stripAnsiEscapes(it.output).trim() }
    }

    // ==========================================================================
    // Marco 5: Gestão de Jogadores (Online, Whitelist, Kick, Ban) & Hot-Backup
    // ==========================================================================

    /**
     * Consulta jogadores online no momento (`mc list`) e cruza com o histórico recente
     * para preencher XUID e horário de conexão quando disponível.
     */
    override suspend fun fetchOnlinePlayers(): Result<List<OnlinePlayer>> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val listResult = sshManager.executeCommand("mc list", timeoutSeconds = 15)
        if (listResult.isFailure) {
            return Result.failure(listResult.exceptionOrNull()!!)
        }

        val history = fetchPlayerHistory().getOrDefault(emptyList())
        val rawListOutput = listResult.getOrThrow().output
        val players = OutputParsers.parseOnlinePlayers(rawListOutput, history)
        return Result.success(players)
    }

    /**
     * Lê a lista de permissões (`allowlist.json` ou `whitelist.json`) do diretório do servidor BDS.
     */
    override suspend fun fetchWhitelist(): Result<List<WhitelistEntry>> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val cmd = "cat /home/opc/minecraft/server/allowlist.json 2>/dev/null || cat /home/opc/minecraft/server/whitelist.json 2>/dev/null || echo '[]'"
        val result = sshManager.executeCommand(cmd, timeoutSeconds = 15)
        return result.map { cmdRes ->
            OutputParsers.parseWhitelist(cmdRes.output)
        }
    }

    /**
     * Consulta o histórico de conexões, spawns e desconexões (`mc jogadores`).
     */
    override suspend fun fetchPlayerHistory(): Result<List<PlayerConnectionEvent>> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val result = sshManager.executeCommand("mc jogadores", timeoutSeconds = 15)
        return result.map { cmdRes ->
            OutputParsers.parsePlayerHistory(cmdRes.output)
        }
    }

    /**
     * Adiciona um jogador à Allowlist do BDS e recarrega a lista imediatamente.
     */
    override suspend fun addToWhitelist(gamertag: String): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val cleanTag = gamertag.trim()
        if (cleanTag.isEmpty()) {
            return Result.failure(IllegalArgumentException("Informe a Gamertag do jogador."))
        }

        val escaped = escapeForDoubleQuotes(cleanTag)
        val cmd = "mc cmd \"allowlist add \\\"$escaped\\\"\" && mc cmd \"allowlist reload\""
        return sshManager.executeCommand(cmd, timeoutSeconds = 15)
    }

    /**
     * Remove um jogador da Allowlist do BDS e recarrega a lista imediatamente.
     */
    override suspend fun removeFromWhitelist(gamertag: String): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val cleanTag = gamertag.trim()
        if (cleanTag.isEmpty()) {
            return Result.failure(IllegalArgumentException("Informe a Gamertag do jogador."))
        }

        val escaped = escapeForDoubleQuotes(cleanTag)
        val cmd = "mc cmd \"allowlist remove \\\"$escaped\\\"\" && mc cmd \"allowlist reload\""
        return sshManager.executeCommand(cmd, timeoutSeconds = 15)
    }

    /**
     * Recarrega a Allowlist do servidor em tempo de execução (`allowlist reload`).
     */
    override suspend fun reloadWhitelist(): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        return sshManager.executeCommand("mc cmd \"allowlist reload\"", timeoutSeconds = 15)
    }

    /**
     * Expulsa um jogador conectado com motivo personalizado (`mc kick "<gamertag>" "<reason>"`).
     */
    override suspend fun kickPlayer(gamertag: String, reason: String): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val cleanTag = gamertag.trim()
        if (cleanTag.isEmpty()) {
            return Result.failure(IllegalArgumentException("Gamertag inválida."))
        }

        val escapedTag = escapeForDoubleQuotes(cleanTag)
        val escapedReason = escapeForDoubleQuotes(reason.trim().ifEmpty { "Expulso pelo administrador" })
        return sshManager.executeCommand("mc kick \"$escapedTag\" \"$escapedReason\"", timeoutSeconds = 15)
    }

    /**
     * Bane um jogador do servidor Never Land:
     * Como o Bedrock Dedicated Server Vanilla utiliza `allowlist.json` como barreira de entrada,
     * o banimento executa atomicamente:
     * 1. `mc kick "<gamertag>" "<reason>"` (desconecta imediatamente se estiver online)
     * 2. `mc cmd "allowlist remove \"<gamertag>\""` (revoga permissão de entrada)
     * 3. `mc cmd "allowlist reload"` (aplica a revogação em memória imediatamente)
     */
    override suspend fun banPlayer(gamertag: String, reason: String): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val cleanTag = gamertag.trim()
        if (cleanTag.isEmpty()) {
            return Result.failure(IllegalArgumentException("Gamertag inválida."))
        }

        val escapedTag = escapeForDoubleQuotes(cleanTag)
        val escapedReason = escapeForDoubleQuotes(reason.trim().ifEmpty { "Banido pelo administrador Never Land" })
        val compositeCmd = "mc kick \"$escapedTag\" \"$escapedReason\" ; mc cmd \"allowlist remove \\\"$escapedTag\\\"\" && mc cmd \"allowlist reload\""
        return sshManager.executeCommand(compositeCmd, timeoutSeconds = 20)
    }

    /**
     * Concede (`op`) ou revoga (`deop`) privilégios de operador para um jogador.
     */
    override suspend fun setPlayerOperator(gamertag: String, grantOp: Boolean): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val escapedTag = escapeForDoubleQuotes(gamertag.trim())
        val verb = if (grantOp) "op" else "deop"
        return sshManager.executeCommand("mc cmd \"$verb \\\"$escapedTag\\\"\"", timeoutSeconds = 15)
    }

    /**
     * Altera o modo de jogo de um jogador conectado (`gamemode <mode> "<gamertag>"`).
     */
    override suspend fun setPlayerGameMode(gamertag: String, gameMode: String): Result<CommandResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val escapedTag = escapeForDoubleQuotes(gamertag.trim())
        val escapedMode = escapeForDoubleQuotes(gameMode.trim())
        return sshManager.executeCommand("mc cmd \"gamemode $escapedMode \\\"$escapedTag\\\"\"", timeoutSeconds = 15)
    }

    /**
     * Dispara a rotina de Hot-Backup consistente do servidor (`mc backup` com fallback para `backup.sh`).
     * Utiliza timeout estendido de 90 segundos para permitir o ciclo completo `save hold` -> `tar -czf` -> `save resume`.
     */
    override suspend fun triggerHotBackup(): Result<BackupResult> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada. Impossível iniciar Hot-Backup."))
        }

        val cmdResult = sshManager.executeCommand(
            "mc backup || /home/opc/minecraft/backup.sh || sudo /home/opc/minecraft/hot_backup.sh",
            timeoutSeconds = 90
        )

        return cmdResult.map { res ->
            OutputParsers.parseBackupResult(
                rawOutput = res.output,
                exitCode = res.exitCode,
                executionTimeMs = res.executionTimeMs
            )
        }
    }

    /**
     * Lista todos os arquivos `.tar.gz` salvos em `/home/opc/minecraft/backups` (`mc backups`).
     */
    override suspend fun fetchBackupsList(): Result<List<BackupFileEntry>> {
        if (!sshManager.isConnected()) {
            return Result.failure(IllegalStateException("Sessão SSH desconectada."))
        }

        val res = sshManager.executeCommand(
            "mc backups || ls -lht /home/opc/minecraft/backups",
            timeoutSeconds = 15
        )
        return res.map { cmdRes ->
            OutputParsers.parseBackupsList(cmdRes.output)
        }
    }
}
