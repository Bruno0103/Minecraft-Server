package com.neverland.servermanager.data.parser

import com.neverland.servermanager.domain.model.ServerStatus

/**
 * ==============================================================================
 * Never Land Server Manager - Parser de Telemetria de Status (StatusParser.kt)
 * ==============================================================================
 *
 * Responsável por sanitizar sequências de escape ANSI e interpretar as saídas textuais
 * geradas remotamente pelo utilitário administrativo [mc] (/usr/local/bin/mc / mc.py)
 * ou comandos diretos do systemd e cgroups v2 da VM Oracle Linux 9.7 UEK.
 *
 * Princípios de Engenharia de Software e Robustez:
 * 1. Imunidade a Travamentos: Toda conversão numérica utiliza operadores seguros
 *    ([toIntOrNull], [toFloatOrNull]), garantindo que saídas truncadas, dados corrompidos
 *    ou caracteres espúrios jamais lancem [NumberFormatException].
 * 2. Prevenção de Ambiguidade Morfológica: A palavra "INATIVO" contém a substring "ATIVO".
 *    O algoritmo exige validação estrita por delimitadores ou checagem de negação explícita
 *    (!badge.contains("INATIVO")), eliminando falsos positivos de servidor online.
 * 3. Sanitização Rigorosa de ANSI: Remove sequências de cores e controles de terminal
 *    com regex compilada estática, garantindo casamento determinístico em terminais TTY e não-TTY.
 * 4. Fallback em Cascata: Se a saída não vier do script canônico mc.py (por exemplo, em
 *    chamadas diretas a 'systemctl show' ou 'systemctl is-active'), ativa rotinas de
 *    interpretação de pares chave=valor do systemd e descritores cgroups v2.
 * ==============================================================================
 */
object StatusParser {

    /**
     * Expressão regular estática pré-compilada para identificação e remoção de
     * sequências de escape ANSI (cores, estilos gráficos e sequências de controle CSI/OSC).
     */
    private val ANSI_ESCAPE_REGEX = Regex(
        """\u001B\[[;?0-9]*[a-zA-Z]|\u001B\].*?(\u0007|\u001B\\)"""
    )

    // ==========================================================================
    // Expressões Regulares Especializadas para 'mc status'
    // ==========================================================================

    /**
     * Captura a linha do serviço systemd.
     * Exemplo: "• Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)"
     *          "• Serviço Systemd:   [PARADO / INATIVO]"
     *          "• Serviço Systemd:   [FALHA / CRASH] (Substate: failed)"
     */
    private val MC_SERVICE_REGEX = Regex(
        """•\s*Serviço Systemd:\s*\[([^\]]+)\](?:\s*\(Substate:\s*([\w-]+)\))?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura o PID do processo bedrock_server.
     * Exemplo: "• PID do Processo:   68773" ou "• PID do Processo:   Nenhum"
     */
    private val MC_PID_REGEX = Regex(
        """•\s*PID do Processo:\s*(\S+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura a data/hora de início reportada pelo systemd.
     * Exemplo: "• Data de Início:    Mon 2026-11-09 22:44:39 GMT"
     */
    private val MC_UPTIME_REGEX = Regex(
        """•\s*Data de Início:\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura a contagem de reinicializações automáticas do processo.
     * Exemplo: "• Contagem Restarts: 0"
     */
    private val MC_RESTARTS_REGEX = Regex(
        """•\s*Contagem Restarts:\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura métricas de consumo de memória RAM do processo BDS e seus limites.
     * Exemplo: "• Memória RAM (BDS): 528.5 MiB (Alerta: 600 MiB, Máx: 750 MiB)"
     *          "• Memória RAM (BDS): 782.4 MiB (Alerta: 600 MiB, Máx: 750 MiB)"
     *          "• Memória RAM (BDS): [not set]"
     */
    private val MC_MEMORY_REGEX = Regex(
        """•\s*Memória RAM \(BDS\):\s*([\d.]+)\s*MiB(?:\s*\(Alerta:\s*([\d.]+)\s*MiB,\s*Máx:\s*([\d.]+)\s*MiB\))?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura a integridade do canal named pipe do console.
     * Exemplo: "• Canal Console:     /run/bedrock.console (FIFO operacional.)"
     *          "• Canal Console:     /run/bedrock.console (FIFO indisponível - ENXIO)"
     */
    private val MC_FIFO_REGEX = Regex(
        """•\s*Canal Console:\s*.*?\(([^)]+)\)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura o número de jogadores conectados vs capacidade máxima.
     * Exemplo: "[2026-11-09 23:41:13:199 INFO] There are 0/10 players online:"
     *          "There are 2/10 players online: Bruno0103V, Mauriceliof"
     *          "There are 0/0 players online:"
     */
    private val MC_PLAYERS_REGEX = Regex(
        """There are (\d+)/(\d+) players online""",
        RegexOption.IGNORE_CASE
    )

    // ==========================================================================
    // Expressões Regulares para Telemetria de Processador (CPU & Load Average)
    // ==========================================================================

    /**
     * Captura o uso percentual global de CPU da máquina virtual medido via `/proc/stat`.
     * Exemplo: "CPU_TOTAL=14.8"
     */
    private val CPU_TOTAL_REGEX = Regex(
        """CPU_TOTAL=([\d.]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura o uso percentual de CPU exclusivo do processo `bedrock_server`.
     * Exemplo: "CPU_BDS=8.4"
     */
    private val CPU_BDS_REGEX = Regex(
        """CPU_BDS=([\d.]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura o Load Average de 1m / 5m / 15m da máquina virtual (`/proc/loadavg`).
     * Exemplo: "CPU_LOAD=0.14 / 0.10 / 0.08"
     */
    private val CPU_LOAD_REGEX = Regex(
        """CPU_LOAD=([^\r\n]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura a quantidade de núcleos/threads lógicos disponíveis (`nproc`).
     * Exemplo: "CPU_CORES=2"
     */
    private val CPU_CORES_REGEX = Regex(
        """CPU_CORES=(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Captura a identificação de modelo do processador (`/proc/cpuinfo`).
     * Exemplo: "CPU_MODEL=AMD EPYC 7J13 64-Core Processor"
     */
    private val CPU_MODEL_REGEX = Regex(
        """CPU_MODEL=([^\r\n]+)""",
        RegexOption.IGNORE_CASE
    )

    // ==========================================================================
    // Expressões Regulares para Fallback de Baixo Nível (systemctl show / cgroups)
    // ==========================================================================

    private val FALLBACK_ACTIVE_STATE_REGEX = Regex("""ActiveState=([\w-]+)""", RegexOption.IGNORE_CASE)
    private val FALLBACK_SUBSTATE_REGEX = Regex("""SubState=([\w-]+)""", RegexOption.IGNORE_CASE)
    private val FALLBACK_PID_REGEX = Regex("""MainPID=(\d+)""", RegexOption.IGNORE_CASE)
    private val FALLBACK_START_TIMESTAMP_REGEX = Regex("""ExecMainStartTimestamp=(.+)""", RegexOption.IGNORE_CASE)
    private val FALLBACK_MEMORY_CURRENT_REGEX = Regex("""MemoryCurrent=(\d+|\[not set\])""", RegexOption.IGNORE_CASE)

    /**
     * Remove quaisquer sequências de escape ANSI presentes no texto de entrada.
     *
     * @param input Texto potencialmente contendo códigos de cores e controle de terminal.
     * @return Texto puro limpo de qualquer código ANSI.
     */
    fun stripAnsiEscapes(input: String): String {
        if (input.isEmpty()) return ""
        return ANSI_ESCAPE_REGEX.replace(input, "")
    }

    /**
     * Interpreta a saída bruta recebida via SSH e produz uma instância de [ServerStatus].
     *
     * @param rawOutput Saída textual bruta do terminal remoto.
     * @return [ServerStatus] com os dados parseados defensivamente.
     */
    fun parseServerStatus(rawOutput: String): ServerStatus {
        val sanitized = stripAnsiEscapes(rawOutput).trim()
        if (sanitized.isBlank()) {
            return ServerStatus.offline()
        }

        // Se a saída não possui marcadores característicos do mc.py, direciona para o parser de fallback
        val hasMcHeader = sanitized.contains("STATUS OPERACIONAL", ignoreCase = true) ||
            sanitized.contains("• Serviço Systemd:", ignoreCase = true) ||
            sanitized.contains("• PID do Processo:", ignoreCase = true)

        if (!hasMcHeader) {
            return parseFallbackStatus(sanitized)
        }

        return parseMcStatusOutput(sanitized)
    }

    /**
     * Realiza o parsing detalhado da saída padrão gerada pela função subcomando_status do utilitário mc.py.
     */
    private fun parseMcStatusOutput(sanitized: String): ServerStatus {
        // 1. Serviço Systemd (ActiveState e Substate)
        val serviceMatch = MC_SERVICE_REGEX.find(sanitized)
        val badge = serviceMatch?.groupValues?.get(1)?.trim() ?: ""
        val explicitSubstate = serviceMatch?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }

        // Validação cuidadosa contra a substring morfológica:
        // "INATIVO" contém a palavra "ATIVO", portanto !badge.contains("INATIVO") é compulsório.
        val isInactiveBadge = badge.contains("INATIVO", ignoreCase = true) ||
            sanitized.contains("[PARADO / INATIVO]", ignoreCase = true) ||
            sanitized.contains("Servidor inativo", ignoreCase = true)

        val isActive = if (isInactiveBadge) {
            false
        } else if (badge.isNotEmpty()) {
            badge.contains("ATIVO", ignoreCase = true)
        } else {
            sanitized.contains("[ATIVO / OPERACIONAL]", ignoreCase = true)
        }

        val substate = explicitSubstate
            ?: if (isActive) "running" else if (isInactiveBadge) "inactive" else "unknown"

        // 2. PID do Processo
        val pidMatch = MC_PID_REGEX.find(sanitized)
        val pidRaw = pidMatch?.groupValues?.get(1)?.trim()
        val pid = if (pidRaw.isNullOrEmpty() || pidRaw.equals("Nenhum", ignoreCase = true) || pidRaw == "0") {
            null
        } else {
            pidRaw.toIntOrNull()
        }

        // 3. Data de Início / Uptime
        val uptimeMatch = MC_UPTIME_REGEX.find(sanitized)
        val uptimeRaw = uptimeMatch?.groupValues?.get(1)?.trim()
        val uptimeText = if (uptimeRaw.isNullOrEmpty() || uptimeRaw.equals("N/A", ignoreCase = true) || uptimeRaw.equals("[not set]", ignoreCase = true)) {
            null
        } else {
            uptimeRaw
        }

        // 4. Memória RAM (BDS)
        val memMatch = MC_MEMORY_REGEX.find(sanitized)
        val memoryUsedMib = memMatch?.groupValues?.get(1)?.toFloatOrNull()
        val memoryAlertMib = memMatch?.groupValues?.getOrNull(2)?.toFloatOrNull() ?: 600.0f
        val memoryMaxMib = memMatch?.groupValues?.getOrNull(3)?.toFloatOrNull() ?: 750.0f

        // 5. Canal Console FIFO (/run/bedrock.console)
        val fifoMatch = MC_FIFO_REGEX.find(sanitized)
        val fifoMessage = fifoMatch?.groupValues?.get(1)?.trim() ?: ""
        // Apenas é operacional se declarar explicitamente "FIFO operacional" e NÃO for erro ENXIO
        val isFifoAvailable = fifoMessage.contains("FIFO operacional", ignoreCase = true) &&
            !fifoMessage.contains("ENXIO", ignoreCase = true) &&
            !fifoMessage.contains("não existe", ignoreCase = true)

        // 6. Jogadores Conectados
        val playersMatch = MC_PLAYERS_REGEX.find(sanitized)
        val onlinePlayers = playersMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val maxPlayers = playersMatch?.groupValues?.get(2)?.toIntOrNull() ?: 10

        // 7. Telemetria do Processador (CPU Total, CPU do BDS, Load Average, Núcleos e Modelo)
        val cpuTotal = CPU_TOTAL_REGEX.find(sanitized)?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0.0f, 100.0f)
        val cpuBds = CPU_BDS_REGEX.find(sanitized)?.groupValues?.get(1)?.toFloatOrNull()?.coerceAtLeast(0.0f)
        val cpuLoad = CPU_LOAD_REGEX.find(sanitized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val cpuCores = CPU_CORES_REGEX.find(sanitized)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
        val cpuModel = CPU_MODEL_REGEX.find(sanitized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        return ServerStatus(
            isActive = isActive,
            substate = substate,
            pid = pid,
            memoryUsedMib = memoryUsedMib,
            memoryAlertMib = memoryAlertMib,
            memoryMaxMib = memoryMaxMib,
            uptimeText = uptimeText,
            isFifoAvailable = isFifoAvailable,
            onlinePlayersCount = onlinePlayers,
            maxPlayersCount = if (maxPlayers > 0) maxPlayers else 10,
            cpuUsagePercent = cpuTotal,
            bdsCpuUsagePercent = cpuBds,
            cpuLoadAverage = cpuLoad,
            cpuCores = cpuCores,
            cpuModel = cpuModel,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Parser de resiliência e fallback para comandos de baixo nível do sistema operacional:
     * - 'systemctl show bedrock.service -p ActiveState -p SubState -p MainPID -p ExecMainStartTimestamp -p MemoryCurrent'
     * - 'systemctl is-active bedrock.service'
     */
    fun parseFallbackStatus(sanitized: String): ServerStatus {
        // Checagem ultra-rápida de saída simples de 'systemctl is-active'
        val isSimpleActive = sanitized.equals("active", ignoreCase = true)
        val isSimpleInactive = sanitized.equals("inactive", ignoreCase = true) ||
            sanitized.equals("failed", ignoreCase = true) ||
            sanitized.equals("dead", ignoreCase = true)

        if (isSimpleActive) {
            return ServerStatus(
                isActive = true,
                substate = "running",
                pid = null,
                memoryUsedMib = null,
                memoryAlertMib = 600.0f,
                memoryMaxMib = 750.0f,
                uptimeText = null,
                isFifoAvailable = false,
                onlinePlayersCount = 0,
                maxPlayersCount = 10
            )
        }

        if (isSimpleInactive) {
            return ServerStatus.offline().copy(substate = sanitized.lowercase())
        }

        // Parsing estruturado de pares chave=valor do systemctl show
        val activeStateMatch = FALLBACK_ACTIVE_STATE_REGEX.find(sanitized)
        val activeState = activeStateMatch?.groupValues?.get(1)?.trim()?.lowercase()

        val substateMatch = FALLBACK_SUBSTATE_REGEX.find(sanitized)
        val substate = substateMatch?.groupValues?.get(1)?.trim()?.lowercase() ?: (if (activeState == "active") "running" else "inactive")

        val isActive = activeState == "active"

        val pidMatch = FALLBACK_PID_REGEX.find(sanitized)
        val pidRaw = pidMatch?.groupValues?.get(1)?.toIntOrNull()
        val pid = if (pidRaw != null && pidRaw > 0) pidRaw else null

        val startTimestampMatch = FALLBACK_START_TIMESTAMP_REGEX.find(sanitized)
        val uptimeRaw = startTimestampMatch?.groupValues?.get(1)?.trim()
        val uptimeText = if (uptimeRaw.isNullOrEmpty() || uptimeRaw.equals("[not set]", ignoreCase = true)) null else uptimeRaw

        val memMatch = FALLBACK_MEMORY_CURRENT_REGEX.find(sanitized)
        val memRaw = memMatch?.groupValues?.get(1)?.trim()
        val memoryUsedMib = if (memRaw != null && memRaw != "[not set]") {
            memRaw.toLongOrNull()?.let { bytes ->
                bytes.toFloat() / (1024.0f * 1024.0f)
            }
        } else {
            null
        }

        val cpuTotal = CPU_TOTAL_REGEX.find(sanitized)?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0.0f, 100.0f)
        val cpuBds = CPU_BDS_REGEX.find(sanitized)?.groupValues?.get(1)?.toFloatOrNull()?.coerceAtLeast(0.0f)
        val cpuLoad = CPU_LOAD_REGEX.find(sanitized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val cpuCores = CPU_CORES_REGEX.find(sanitized)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
        val cpuModel = CPU_MODEL_REGEX.find(sanitized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        return ServerStatus(
            isActive = isActive,
            substate = substate,
            pid = pid,
            memoryUsedMib = memoryUsedMib,
            memoryAlertMib = 600.0f,
            memoryMaxMib = 750.0f,
            uptimeText = uptimeText,
            isFifoAvailable = isActive, // No fallback, supõe disponível caso o serviço esteja ativo
            onlinePlayersCount = 0,
            maxPlayersCount = 10,
            cpuUsagePercent = cpuTotal,
            bdsCpuUsagePercent = cpuBds,
            cpuLoadAverage = cpuLoad,
            cpuCores = cpuCores,
            cpuModel = cpuModel,
            timestamp = System.currentTimeMillis()
        )
    }
}
