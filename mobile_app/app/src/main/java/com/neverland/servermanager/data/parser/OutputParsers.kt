package com.neverland.servermanager.data.parser

import com.neverland.servermanager.domain.model.BackupFileEntry
import com.neverland.servermanager.domain.model.BackupResult
import com.neverland.servermanager.domain.model.ConsoleEntry
import com.neverland.servermanager.domain.model.ConsoleLineType
import com.neverland.servermanager.domain.model.OnlinePlayer
import com.neverland.servermanager.domain.model.PlayerConnectionEvent
import com.neverland.servermanager.domain.model.PlayerEventType
import com.neverland.servermanager.domain.model.WhitelistEntry

/**
 * ==============================================================================
 * Never Land Server Manager - Parsers de Saídas Operacionais (OutputParsers.kt)
 * ==============================================================================
 *
 * Centraliza a interpretação determinística e segura das saídas textuais dos
 * subcomandos do utilitário administrativo `mc.py` (`/usr/local/bin/mc`),
 * arquivos de configuração JSON do BDS (`allowlist.json`) e logs do `journalctl`.
 *
 * Princípios de Robustez:
 * 1. Sanitização Automática de Cores ANSI: Reutiliza [StatusParser.stripAnsiEscapes]
 *    para garantir que códigos de formatação de terminal não poluam os modelos.
 * 2. Tolerância a Variações de Formato: O Bedrock Dedicated Server varia sua saída
 *    dependendo da versão (ex.: lista de jogadores na mesma linha ou na linha seguinte
 *    ao cabeçalho `There are X/Y players online:`). Todos os formatos são suportados.
 * 3. Zero Exceções Não Tratadas: Regexes defensivas e conversões seguras garantem
 *    funcionamento contínuo mesmo diante de saídas parciais.
 * ==============================================================================
 */
object OutputParsers {

    /**
     * Regex para identificar o cabeçalho de contagem de jogadores do BDS:
     * Ex.: `[2026-11-09 20:31:10:123 INFO] There are 2/10 players online: Steve, Alex`
     */
    private val PLAYERS_HEADER_REGEX = Regex(
        """There are\s+(\d+)/(\d+)\s+players online:(.*)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Regex para remover prefixos de data/hora do log do Bedrock:
     * Ex.: `[2026-11-09 20:31:10:123 INFO] ` ou `[2026-11-09 20:31:10 INFO] `
     */
    private val BDS_LOG_PREFIX_REGEX = Regex(
        """^\[(\d{4}-\d{2}-\d{2}\s+)?(\d{2}:\d{2}:\d{2})(?::\d+)?\s*([A-Z]+)?\]\s*"""
    )

    /**
     * Regex para capturar eventos de conexão, desconexão e spawn no histórico (`mc jogadores`):
     * Exemplos:
     * - `[2026-11-09 20:31:00 INFO] Player connected: Bruno0103V, xuid: 2535412890123456`
     * - `[2026-11-09 20:31:05 INFO] Player Spawned: Bruno0103V xuid: 2535412890123456, pfid: ...`
     * - `[2026-11-09 21:10:00 INFO] Player disconnected: Bruno0103V, xuid: 2535412890123456, pfid: ...`
     */
    private val PLAYER_EVENT_REGEX = Regex(
        """Player\s+(connected|disconnected|Spawned):\s*([^,]+?)(?:,\s*xuid:\s*(\d+)|\s+xuid:\s*(\d+)|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Regex para extrair objetos individuais de um array JSON (`allowlist.json`).
     */
    private val JSON_OBJECT_REGEX = Regex("""\{[^{}]*\}""")
    private val JSON_NAME_REGEX = Regex(""""name"\s*:\s*"([^"]+)"""")
    private val JSON_XUID_REGEX = Regex(""""xuid"\s*:\s*"([^"]*)"""")
    private val JSON_IGNORES_LIMIT_REGEX = Regex(""""ignoresPlayerLimit"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)

    /**
     * Regex para extrair tempo gasto no backup (`[OK] Backup concluído em 4.2s!`).
     */
    private val BACKUP_DURATION_REGEX = Regex("""concluído em\s+([\d.]+)\s*s""", RegexOption.IGNORE_CASE)

    /**
     * Regex para identificar nomes de arquivos `.tar.gz` na saída do backup ou `ls -lh`.
     */
    private val BACKUP_FILENAME_REGEX = Regex("""([\w.-]+\.tar\.gz)""", RegexOption.IGNORE_CASE)

    // ==========================================================================
    // 1. Parser de Jogadores Online (`mc list`)
    // ==========================================================================

    /**
     * Interpreta a saída do comando `mc list` (ou seção de jogadores do `mc status`)
     * e retorna a lista estruturada de jogadores online no momento.
     *
     * Caso o histórico de conexões ([knownHistory]) seja fornecido, cruza automaticamente
     * cada Gamertag para enriquecer o objeto com o respectivo XUID e horário de entrada.
     *
     * @param rawOutput Texto bruto retornado por `mc list`.
     * @param knownHistory Lista opcional de eventos recentes para enriquecimento de XUID.
     * @return Lista imutável de [OnlinePlayer].
     */
    fun parseOnlinePlayers(
        rawOutput: String,
        knownHistory: List<PlayerConnectionEvent> = emptyList()
    ): List<OnlinePlayer> {
        val sanitized = StatusParser.stripAnsiEscapes(rawOutput).trim()
        if (sanitized.isBlank()) return emptyList()

        val lines = sanitized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val gamertags = mutableListOf<String>()

        for (i in lines.indices) {
            val line = lines[i]
            val headerMatch = PLAYERS_HEADER_REGEX.find(line)
            if (headerMatch != null) {
                val count = headerMatch.groupValues[1].toIntOrNull() ?: 0
                if (count <= 0) return emptyList()

                // Verifica se os nomes já vieram na mesma linha após os dois-pontos
                val inlineNames = headerMatch.groupValues[3].trim()
                if (inlineNames.isNotEmpty()) {
                    inlineNames.split(",")
                        .map { cleanGamertagLine(it) }
                        .filter { it.isNotEmpty() }
                        .forEach { gamertags.add(it) }
                } else if (i + 1 < lines.size) {
                    // No BDS 1.21+, os nomes costumam vir na linha imediatamente subsequente
                    val nextLine = cleanGamertagLine(lines[i + 1])
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("===") && !nextLine.startsWith("---")) {
                        nextLine.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { gamertags.add(it) }
                    }
                }
                break
            }
        }

        // Mapa rápido de XUIDs e último horário de conexão a partir do histórico
        val xuidByPlayer = mutableMapOf<String, String>()
        val timeByPlayer = mutableMapOf<String, String>()
        for (event in knownHistory) {
            val key = event.gamertag.lowercase()
            if (!event.xuid.isNullOrBlank()) {
                xuidByPlayer[key] = event.xuid
            }
            if (event.eventType == PlayerEventType.CONNECTED || event.eventType == PlayerEventType.SPAWNED) {
                timeByPlayer[key] = event.timestamp
            }
        }

        return gamertags.distinct().map { tag ->
            val lower = tag.lowercase()
            OnlinePlayer(
                gamertag = tag,
                xuid = xuidByPlayer[lower],
                connectedSince = timeByPlayer[lower]
            )
        }
    }

    /**
     * Remove prefixos de timestamp/INFO de uma linha contendo nomes de jogadores.
     */
    private fun cleanGamertagLine(raw: String): String {
        return BDS_LOG_PREFIX_REGEX.replace(raw.trim(), "").trim()
    }

    // ==========================================================================
    // 2. Parser de Histórico de Conexões e XUIDs (`mc jogadores`)
    // ==========================================================================

    /**
     * Interpreta a saída do comando `mc jogadores` (ou logs filtrados do `journalctl`)
     * extraindo todos os eventos de conexão, spawn e desconexão com XUID.
     *
     * @param rawOutput Saída textual bruta do comando `mc jogadores`.
     * @return Lista de [PlayerConnectionEvent] ordenada do mais recente para o mais antigo.
     */
    fun parsePlayerHistory(rawOutput: String): List<PlayerConnectionEvent> {
        val sanitized = StatusParser.stripAnsiEscapes(rawOutput).trim()
        if (sanitized.isBlank()) return emptyList()

        val events = mutableListOf<PlayerConnectionEvent>()

        for (rawLine in sanitized.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("===")) continue

            val match = PLAYER_EVENT_REGEX.find(trimmed) ?: continue
            val rawAction = match.groupValues[1].lowercase()
            val rawGamertag = match.groupValues[2].trim()
            val xuid = match.groupValues[3].ifEmpty { match.groupValues[4] }.trim().takeIf { it.isNotEmpty() }

            // Extrai timestamp legível se presente na linha (ex: [2026-11-09 20:31:00 INFO])
            val tsMatch = BDS_LOG_PREFIX_REGEX.find(trimmed.replace(Regex("""^[✔✖✦]\s*[A-ZÇÃÕ]+:\s*"""), ""))
            val datePart = tsMatch?.groupValues?.get(1)?.trim() ?: ""
            val timePart = tsMatch?.groupValues?.get(2)?.trim() ?: ""
            val timestamp = listOf(datePart, timePart).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "Recente" }

            val eventType = when (rawAction) {
                "connected" -> PlayerEventType.CONNECTED
                "disconnected" -> PlayerEventType.DISCONNECTED
                else -> PlayerEventType.SPAWNED
            }

            // Limpa sufixos residuais como "xuid: ..." caso tenham ficado no nome
            val cleanTag = rawGamertag
                .replace(Regex("""\s+xuid:.*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex(""",\s*pfid:.*""", RegexOption.IGNORE_CASE), "")
                .trim()

            if (cleanTag.isNotEmpty()) {
                events.add(
                    PlayerConnectionEvent(
                        timestamp = timestamp,
                        eventType = eventType,
                        gamertag = cleanTag,
                        xuid = xuid,
                        rawLine = trimmed
                    )
                )
            }
        }

        // Retorna os eventos mais recentes no topo para melhor usabilidade no celular
        return events.reversed()
    }

    // ==========================================================================
    // 3. Parser da Allowlist / Whitelist (`allowlist.json` e `allowlist list`)
    // ==========================================================================

    /**
     * Interpreta o conteúdo JSON do arquivo `/home/opc/minecraft/server/allowlist.json`
     * (ou saída textual do comando `allowlist list` do console BDS) e produz a lista
     * ordenada de jogadores autorizados na Whitelist.
     *
     * @param rawContent Conteúdo JSON ou saída do console.
     * @return Lista de [WhitelistEntry] ordenada alfabeticamente por Gamertag.
     */
    fun parseWhitelist(rawContent: String): List<WhitelistEntry> {
        val sanitized = StatusParser.stripAnsiEscapes(rawContent).trim()
        if (sanitized.isBlank()) return emptyList()

        val entries = mutableListOf<WhitelistEntry>()

        // Caso 1: JSON estruturado (allowlist.json)
        if (sanitized.contains("[") && sanitized.contains("{")) {
            for (objMatch in JSON_OBJECT_REGEX.findAll(sanitized)) {
                val objText = objMatch.value
                val name = JSON_NAME_REGEX.find(objText)?.groupValues?.get(1)?.trim()
                if (!name.isNullOrEmpty()) {
                    val xuid = JSON_XUID_REGEX.find(objText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                    val ignoresLimit = JSON_IGNORES_LIMIT_REGEX.find(objText)?.groupValues?.get(1)?.equals("true", ignoreCase = true) ?: false
                    entries.add(
                        WhitelistEntry(
                            name = name,
                            xuid = xuid,
                            ignoresPlayerLimit = ignoresLimit
                        )
                    )
                }
            }
        }

        return entries.distinctBy { it.name.lowercase() }.sortedBy { it.name.lowercase() }
    }

    // ==========================================================================
    // 4. Parser de Resultados e Lista de Hot-Backups (`mc backup` e `mc backups`)
    // ==========================================================================

    /**
     * Interpreta a saída da execução manual do backup (`mc backup` ou `hot_backup.sh`).
     *
     * @param rawOutput Saída textual retornada pelo comando SSH.
     * @param exitCode Código de saída POSIX do comando.
     * @param executionTimeMs Tempo medido pelo cliente SSH em milissegundos.
     * @return Objeto [BackupResult] rico com duração, arquivo gerado e mensagem.
     */
    fun parseBackupResult(
        rawOutput: String,
        exitCode: Int,
        executionTimeMs: Long
    ): BackupResult {
        val sanitized = StatusParser.stripAnsiEscapes(rawOutput).trim()
        val isSuccess = exitCode == 0 && !sanitized.contains("[ERRO]", ignoreCase = true)

        // Tenta extrair o tempo reportado pelo mc.py ou usa o tempo medido pelo cliente SSH
        val parsedSeconds = BACKUP_DURATION_REGEX.find(sanitized)?.groupValues?.get(1)?.toFloatOrNull()
        val elapsedSeconds = parsedSeconds ?: (executionTimeMs / 1000.0f).coerceAtLeast(0.1f)

        // Tenta identificar o nome do arquivo .tar.gz gerado
        val allFilenames = BACKUP_FILENAME_REGEX.findAll(sanitized).map { it.groupValues[1] }.toList()
        val filename = allFilenames.lastOrNull() ?: if (isSuccess) "bedrock_backup_latest.tar.gz" else "N/A"

        // Tenta extrair tamanho se houver linha de ls -lht (ex: "-rw-r--r-- 1 opc opc 42M Nov 9 ...")
        var sizeText: String? = null
        for (line in sanitized.lines()) {
            if (line.contains(".tar.gz")) {
                val parts = line.replace("Novo arquivo gerado:", "").trim().split(Regex("""\s+"""))
                if (parts.size >= 5 && (parts[0].startsWith("-") || parts[0].startsWith("r"))) {
                    sizeText = parts[4]
                }
            }
        }

        val friendlyMessage = if (isSuccess) {
            "Hot-Backup concluído com sucesso em %.1fs! Arquivo: %s".format(elapsedSeconds, filename)
        } else {
            sanitized.ifEmpty { "Falha ao executar o script de Hot-Backup (código $exitCode)." }
        }

        return BackupResult(
            success = isSuccess,
            elapsedSeconds = elapsedSeconds,
            filename = filename,
            sizeText = sizeText,
            message = friendlyMessage
        )
    }

    /**
     * Interpreta a saída de `mc backups` (`ls -lh /home/opc/minecraft/backups`)
     * transformando cada linha `.tar.gz` em um [BackupFileEntry].
     *
     * Exemplo de linha do `ls -lh`:
     * `-rw-rw-r--. 1 opc opc 38M Nov  9 04:00 bedrock_backup_2026-11-09_04-00-01.tar.gz`
     */
    fun parseBackupsList(rawOutput: String): List<BackupFileEntry> {
        val sanitized = StatusParser.stripAnsiEscapes(rawOutput).trim()
        if (sanitized.isBlank()) return emptyList()

        val list = mutableListOf<BackupFileEntry>()
        for (rawLine in sanitized.lines()) {
            val trimmed = rawLine.trim()
            if (!trimmed.contains(".tar.gz") && !trimmed.contains(".zip")) continue

            val parts = trimmed.split(Regex("""\s+"""))
            if (parts.size >= 9) {
                val permissions = parts[0]
                val sizeText = parts[4]
                val dateText = "${parts[5]} ${parts[6]} ${parts[7]}"
                val filename = parts.subList(8, parts.size).joinToString(" ")
                list.add(
                    BackupFileEntry(
                        filename = filename,
                        sizeText = sizeText,
                        dateText = dateText,
                        permissions = permissions,
                        rawLine = trimmed
                    )
                )
            } else {
                val filename = BACKUP_FILENAME_REGEX.find(trimmed)?.groupValues?.get(1) ?: trimmed
                list.add(
                    BackupFileEntry(
                        filename = filename,
                        sizeText = "--",
                        dateText = "Disponível no disco",
                        rawLine = trimmed
                    )
                )
            }
        }

        return list
    }

    // ==========================================================================
    // 5. Parser e Classificador Sintático de Logs do Console (`mc log`)
    // ==========================================================================

    /**
     * Converte um bloco de texto bruto de logs do `journalctl` / `mc log` em uma
     * lista tipada de [ConsoleEntry] com classificação de cor/categoria e timestamp.
     *
     * @param rawOutput Saída textual de `mc log N` ou `journalctl`.
     * @param baseId ID inicial para geração de identificadores únicos.
     * @return Lista de [ConsoleEntry] pronta para exibição no Console Interativo.
     */
    fun parseConsoleLogs(rawOutput: String, baseId: Long = 1L): List<ConsoleEntry> {
        val sanitized = StatusParser.stripAnsiEscapes(rawOutput).trim()
        if (sanitized.isBlank()) return emptyList()

        val entries = mutableListOf<ConsoleEntry>()
        var currentId = baseId

        for (rawLine in sanitized.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val prefixMatch = BDS_LOG_PREFIX_REGEX.find(trimmed)
            val timestamp = prefixMatch?.groupValues?.get(2)?.takeIf { it.isNotEmpty() } ?: "--:--:--"
            val levelTag = prefixMatch?.groupValues?.get(3)?.uppercase() ?: ""
            val cleanContent = if (prefixMatch != null) {
                trimmed.substring(prefixMatch.range.last + 1).trim().ifEmpty { trimmed }
            } else {
                trimmed
            }

            val lineType = classifyConsoleLine(cleanContent, levelTag)

            entries.add(
                ConsoleEntry(
                    id = currentId++,
                    timestamp = timestamp,
                    content = cleanContent,
                    type = lineType
                )
            )
        }

        return entries
    }

    /**
     * Determina a categoria visual ([ConsoleLineType]) de uma linha de log com base
     * no nível reportado pelo BDS e em palavras-chave do conteúdo.
     */
    fun classifyConsoleLine(content: String, levelTag: String = ""): ConsoleLineType {
        val lower = content.lowercase()
        return when {
            content.startsWith(">>") -> ConsoleLineType.COMMAND_SENT
            levelTag == "ERROR" || lower.contains("[error]") || lower.contains("[erro]") || lower.contains("exception") || lower.contains("failed") -> ConsoleLineType.ERROR
            levelTag == "WARN" || lower.contains("[warn]") || lower.contains("warning") || lower.contains("[aviso]") -> ConsoleLineType.WARN
            levelTag == "CHAT" || lower.contains("[server]") || (content.startsWith("<") && content.contains(">")) || lower.contains("say ") -> ConsoleLineType.CHAT
            lower.contains("player connected") || lower.contains("player disconnected") || lower.contains("player spawned") || lower.contains("xuid:") -> ConsoleLineType.PLAYER_EVENT
            else -> ConsoleLineType.INFO
        }
    }
}

