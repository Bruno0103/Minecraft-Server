package com.neverland.servermanager

import com.neverland.servermanager.data.parser.StatusParser
import com.neverland.servermanager.domain.model.ServerHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ==============================================================================
 * Never Land Server Manager - Testes Unitários do StatusParser (StatusParserTest.kt)
 * ==============================================================================
 *
 * Suíte rigorosa de validação do componente [StatusParser], assegurando que
 * todas as variantes de telemetria do Bedrock Dedicated Server sejam interpretadas
 * com precisão determinística, imunidade a nulos e tolerância a ruído de rede.
 * ==============================================================================
 */
class StatusParserTest {

    /**
     * Valida a sanitização de sequências de escape ANSI (cores e comandos de controle CSI/OSC).
     */
    @Test
    fun testStripAnsiEscapesRemovesAllColorCodes() {
        val rawWithColors = "\u001B[1;32m[ATIVO / OPERACIONAL]\u001B[0m \u001B[33m528.5 MiB\u001B[0m"
        val clean = StatusParser.stripAnsiEscapes(rawWithColors)

        assertEquals("[ATIVO / OPERACIONAL] 528.5 MiB", clean)
    }

    /**
     * Valida o parsing canônico de servidor ativo e saudável com saída formatada de mc.py.
     */
    @Test
    fun testParseCanonicalActiveServerStatus() {
        val raw = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===

              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   68773
              • Data de Início:    Mon 2026-11-09 22:44:39 GMT
              • Contagem Restarts: 0
              • Memória RAM (BDS): 528.5 MiB (Alerta: 600 MiB, Máx: 750 MiB)
              • Canal Console:     /run/bedrock.console (FIFO operacional.)

            --- Jogadores Conectados ---
              [2026-11-09 23:41:13:199 INFO] There are 0/10 players online:
        """.trimIndent()

        val status = StatusParser.parseServerStatus(raw)

        assertTrue("Servidor deve ser reportado como ativo", status.isActive)
        assertEquals("running", status.substate)
        assertEquals(68773, status.pid)
        assertEquals("Mon 2026-11-09 22:44:39 GMT", status.uptimeText)
        assertEquals(528.5f, status.memoryUsedMib ?: 0f, 0.01f)
        assertEquals(600.0f, status.memoryAlertMib, 0.01f)
        assertEquals(750.0f, status.memoryMaxMib, 0.01f)
        assertTrue("FIFO deve constar como operacional", status.isFifoAvailable)
        assertEquals(0, status.onlinePlayersCount)
        assertEquals(10, status.maxPlayersCount)
        assertEquals(ServerHealth.HEALTHY, status.health)
        assertFalse("Não deve estar em alerta de memória", status.isMemoryAlert)
        assertFalse("Não deve estar em estado crítico de memória", status.isMemoryCritical)
        assertEquals("0/10", status.playersRatioText)
        assertFalse("Não deve haver jogadores online", status.hasPlayersOnline)
    }

    /**
     * Valida a extração de jogadores conectados quando há múltiplos players ativos.
     */
    @Test
    fun testParseActiveServerWithOnlinePlayers() {
        val raw = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===

              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   71200
              • Data de Início:    Tue 2026-11-10 01:00:00 GMT
              • Contagem Restarts: 0
              • Memória RAM (BDS): 550.0 MiB (Alerta: 600 MiB, Máx: 750 MiB)
              • Canal Console:     /run/bedrock.console (FIFO operacional.)

            --- Jogadores Conectados ---
              [2026-11-10 01:15:22:841 INFO] There are 2/10 players online: Bruno0103V, Mauriceliof
        """.trimIndent()

        val status = StatusParser.parseServerStatus(raw)

        assertTrue(status.isActive)
        assertEquals(2, status.onlinePlayersCount)
        assertEquals(10, status.maxPlayersCount)
        assertTrue(status.hasPlayersOnline)
        assertEquals("2/10", status.playersRatioText)
        assertEquals(ServerHealth.HEALTHY, status.health)
    }

    /**
     * Valida a prevenção do bug morfológico: "INATIVO" contém a substring "ATIVO",
     * mas deve ser estritamente avaliado como inativo/desligado.
     */
    @Test
    fun testParseStoppedServerDoesNotTriggerActiveDueToSubstring() {
        val raw = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===

              • Serviço Systemd:   [PARADO / INATIVO]
              • PID do Processo:   Nenhum
              • Data de Início:    N/A
              • Contagem Restarts: 0
              • Memória RAM (BDS): [not set]
              • Canal Console:     /run/bedrock.console (O arquivo FIFO /run/bedrock.console não existe no sistema.)

            --- Jogadores Conectados ---
              Servidor inativo. Não há jogadores online.
        """.trimIndent()

        val status = StatusParser.parseServerStatus(raw)

        assertFalse("Servidor parado jamais deve ser avaliado como ativo", status.isActive)
        assertEquals("inactive", status.substate)
        assertNull("PID deve ser nulo quando marcado como Nenhum", status.pid)
        assertNull("Uptime deve ser nulo quando N/A", status.uptimeText)
        assertNull("RAM deve ser nula quando [not set]", status.memoryUsedMib)
        assertFalse("FIFO não deve estar disponível", status.isFifoAvailable)
        assertEquals(0, status.onlinePlayersCount)
        assertEquals(ServerHealth.OFFLINE, status.health)
    }

    /**
     * Valida estado de falha/crash do systemd.
     */
    @Test
    fun testParseFailedServerState() {
        val raw = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===

              • Serviço Systemd:   [FALHA / CRASH] (Substate: failed)
              • PID do Processo:   Nenhum
              • Data de Início:    N/A
              • Contagem Restarts: 3
              • Memória RAM (BDS): 0.0 MiB (Alerta: 600 MiB, Máx: 750 MiB)
              • Canal Console:     /run/bedrock.console (FIFO indisponível)

            --- Jogadores Conectados ---
              Servidor inativo. Não há jogadores online.
        """.trimIndent()

        val status = StatusParser.parseServerStatus(raw)

        assertFalse(status.isActive)
        assertEquals("failed", status.substate)
        assertNull(status.pid)
        assertEquals(ServerHealth.OFFLINE, status.health)
    }

    /**
     * Valida detecção de patamar de memória em alerta (> 600 MiB).
     */
    @Test
    fun testParseMemoryWarningThreshold() {
        val raw = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===

              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   68773
              • Data de Início:    Mon 2026-11-09 22:44:39 GMT
              • Contagem Restarts: 0
              • Memória RAM (BDS): 650.0 MiB (Alerta: 600 MiB, Máx: 750 MiB)
              • Canal Console:     /run/bedrock.console (FIFO operacional.)

            --- Jogadores Conectados ---
              There are 0/10 players online:
        """.trimIndent()

        val status = StatusParser.parseServerStatus(raw)

        assertTrue(status.isActive)
        assertTrue("Deve acusar alerta de memória", status.isMemoryAlert)
        assertFalse("Não deve acusar crítico", status.isMemoryCritical)
        assertEquals(ServerHealth.WARNING, status.health)
    }

    /**
     * Valida detecção de patamar de memória crítico (> 750 MiB).
     */
    @Test
    fun testParseMemoryCriticalThreshold() {
        val raw = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===

              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   68773
              • Data de Início:    Mon 2026-11-09 22:44:39 GMT
              • Contagem Restarts: 0
              • Memória RAM (BDS): 782.4 MiB (Alerta: 600 MiB, Máx: 750 MiB)
              • Canal Console:     /run/bedrock.console (FIFO operacional.)

            --- Jogadores Conectados ---
              There are 0/10 players online:
        """.trimIndent()

        val status = StatusParser.parseServerStatus(raw)

        assertTrue(status.isActive)
        assertTrue("Deve acusar alerta de memória", status.isMemoryAlert)
        assertTrue("Deve acusar crítico de memória", status.isMemoryCritical)
        assertEquals(ServerHealth.CRITICAL, status.health)
    }

    /**
     * Valida detecção de erro ENXIO no socket FIFO com servidor ativo (deve classificar como WARNING).
     */
    @Test
    fun testParseFifoEnxioErrorTriggersWarning() {
        val raw = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===

              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   68773
              • Data de Início:    Mon 2026-11-09 22:44:39 GMT
              • Contagem Restarts: 0
              • Memória RAM (BDS): 520.0 MiB (Alerta: 600 MiB, Máx: 750 MiB)
              • Canal Console:     /run/bedrock.console (FIFO indisponível - ENXIO)

            --- Jogadores Conectados ---
              There are 0/10 players online:
        """.trimIndent()

        val status = StatusParser.parseServerStatus(raw)

        assertTrue(status.isActive)
        assertFalse("FIFO deve constar como inoperante", status.isFifoAvailable)
        assertEquals(ServerHealth.WARNING, status.health)
    }

    /**
     * Valida contagem 0/0 de jogadores durante a fase transitória de boot do BDS.
     */
    @Test
    fun testParseBootingServerZeroTotalPlayers() {
        val raw = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===

              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   69001
              • Data de Início:    Mon 2026-11-09 23:00:00 GMT
              • Contagem Restarts: 0
              • Memória RAM (BDS): 350.0 MiB (Alerta: 600 MiB, Máx: 750 MiB)
              • Canal Console:     /run/bedrock.console (FIFO operacional.)

            --- Jogadores Conectados ---
              [2026-11-09 23:00:05:000 INFO] There are 0/0 players online:
        """.trimIndent()

        val status = StatusParser.parseServerStatus(raw)

        assertTrue(status.isActive)
        assertEquals(0, status.onlinePlayersCount)
        assertEquals("Capacidade zero deve recuar para padrão 10", 10, status.maxPlayersCount)
    }

    /**
     * Valida o mecanismo de contingência para saídas diretas de 'systemctl show'.
     */
    @Test
    fun testParseFallbackSystemctlShowOutput() {
        val rawShow = """
            ActiveState=active
            SubState=running
            MainPID=68773
            ExecMainStartTimestamp=Mon 2026-11-09 22:44:39 GMT
            MemoryCurrent=554172416
            NRestarts=0
        """.trimIndent()

        val status = StatusParser.parseServerStatus(rawShow)

        assertTrue(status.isActive)
        assertEquals("running", status.substate)
        assertEquals(68773, status.pid)
        assertEquals("Mon 2026-11-09 22:44:39 GMT", status.uptimeText)
        // 554172416 bytes / (1024 * 1024) = 528.5 MiB
        assertEquals(528.5f, status.memoryUsedMib ?: 0f, 0.1f)
        assertEquals(ServerHealth.HEALTHY, status.health)
    }

    /**
     * Valida o mecanismo de contingência para 'systemctl is-active'.
     */
    @Test
    fun testParseFallbackSystemctlIsActiveOutput() {
        val statusActive = StatusParser.parseFallbackStatus("active")
        assertTrue(statusActive.isActive)
        assertEquals("running", statusActive.substate)

        val statusInactive = StatusParser.parseFallbackStatus("inactive")
        assertFalse(statusInactive.isActive)
        assertEquals("inactive", statusInactive.substate)

        val statusFailed = StatusParser.parseFallbackStatus("failed")
        assertFalse(statusFailed.isActive)
        assertEquals("failed", statusFailed.substate)
    }

    /**
     * Valida resiliência diante de saídas em branco, corrompidas ou ruído puro de socket.
     */
    @Test
    fun testParseResilienceAgainstGarbageAndEmptyInput() {
        val emptyStatus = StatusParser.parseServerStatus("")
        assertFalse(emptyStatus.isActive)
        assertEquals("inactive", emptyStatus.substate)

        val blankStatus = StatusParser.parseServerStatus("   \n\t  ")
        assertFalse(blankStatus.isActive)

        val garbage = "ssh: connect to host 157.151.2.91 port 22: Connection refused"
        val garbageStatus = StatusParser.parseServerStatus(garbage)
        assertFalse(garbageStatus.isActive)
    }
}
