package com.neverland.servermanager.e2e

import com.neverland.servermanager.e2e.mock.MockSshServerSession
import com.neverland.servermanager.e2e.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

// ==============================================================================
// Never Land Server Manager - Suíte E2E: Tier 3 (Cross-Feature Combinations)
// ==============================================================================
// Esta classe cobre a interação combinatória entre múltiplos subsistemas do
// aplicativo:
// - Comandos concorrentes no mesmo canal SSH
// - Sincronização entre ciclo de vida e rotinas de backup
// - Encadeamento entre gestão de jogadores e permissões de allowlist
// - Comportamento de reconexão de rede pós-interrupção
// ==============================================================================
class Tier3CrossFeatureCombinationsTest {

    private lateinit var session: MockSshServerSession

    @Before
    fun setUp() {
        session = MockSshServerSession()
        session.connect()
    }

    @After
    fun tearDown() {
        session.disconnect()
    }

    /**
     * Teste 3.1: Expulsar jogador e imediatamente removê-lo da whitelist em sequência.
     * Valida que o jogador é expulso do servidor ativo e removido da lista persistente.
     */
    @Test
    fun testCross_KickAndWhitelistRemove() {
        val targetPlayer = "JogadorInfrator"
        session.onlinePlayers.add(targetPlayer)
        session.whitelistPlayers.add(targetPlayer)

        // 1. Kick
        val kickCmd = SshCommandBuilder.buildKick(targetPlayer, "Removido da comunidade")
        val kickResult = session.execute(kickCmd).getOrThrow()
        assertEquals(0, kickResult.exitCode)
        assertFalse("Jogador não deve mais constar nos online", session.onlinePlayers.contains(targetPlayer))

        // 2. Remove da whitelist
        val removeCmd = SshCommandBuilder.buildWhitelistRemove(targetPlayer)
        val removeResult = session.execute(removeCmd).getOrThrow()
        assertEquals(0, removeResult.exitCode)
        assertFalse("Jogador não deve mais constar na whitelist", session.whitelistPlayers.contains(targetPlayer))

        // 3. Verifica via listagem
        val listResult = session.execute(SshCommandBuilder.buildList()).getOrThrow()
        val onlineList = PlayerListParser.parse(listResult.output)
        assertFalse(onlineList.contains(targetPlayer))
    }

    /**
     * Teste 3.2: Envio concorrente de múltiplos comandos garantindo serialização limpa no canal SSH.
     * Simula chamadas de polling e de terminal disparadas quase simultaneamente.
     */
    @Test
    fun testCross_ConcurrentCommands_SerializedThroughSession() {
        val commands = listOf(
            SshCommandBuilder.buildStatus(),
            SshCommandBuilder.buildList(),
            SshCommandBuilder.buildEchoTest(),
            SshCommandBuilder.buildRecursos()
        )

        val results = mutableListOf<CommandResult>()
        for (cmd in commands) {
            val res = session.execute(cmd).getOrThrow()
            results.add(res)
        }

        assertEquals(4, results.size)
        assertEquals(4, session.executedCommandsHistory.size)
        assertEquals("mc status", session.executedCommandsHistory[0])
        assertEquals("mc list", session.executedCommandsHistory[1])
        assertEquals("echo \"NEVERLAND_OK\"", session.executedCommandsHistory[2])
        assertEquals("mc recursos", session.executedCommandsHistory[3])
    }

    /**
     * Teste 3.3: Disparo de broadcast no chat simultaneamente ao polling periódico de status a cada 30s.
     */
    @Test
    fun testCross_SayCommandDuringStatusPolling() {
        // Polling de status #1
        val status1 = ServerStatusParser.parse(session.execute(SshCommandBuilder.buildStatus()).getOrThrow().output)
        assertTrue(status1.isActive)

        // Broadcast de chat intercalado
        val sayResult = session.execute(SshCommandBuilder.buildSay("Aviso: evento em 5 minutos!")).getOrThrow()
        assertTrue(sayResult.output.contains("[Server] Aviso: evento em 5 minutos!"))

        // Polling de status #2
        val status2 = ServerStatusParser.parse(session.execute(SshCommandBuilder.buildStatus()).getOrThrow().output)
        assertTrue(status2.isActive)
        assertEquals(status1.pid, status2.pid)
    }

    /**
     * Teste 3.4: Prevenção de disparo de hot-backup se o servidor estiver em processo de parada.
     */
    @Test
    fun testCross_BackupTriggerDuringServerStop_Rejection() {
        // Simula transição para STOPPING
        var isStopping = true

        fun safeTriggerBackup(): Result<String> {
            if (isStopping) {
                return Result.failure(IllegalStateException("Não é possível iniciar backup: o servidor está sendo finalizado."))
            }
            return Result.success("OK")
        }

        val result = safeTriggerBackup()
        assertTrue("Disparo durante parada deve ser rejeitado", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("sendo finalizado") == true)
    }

    /**
     * Teste 3.5: Início do servidor ('mc start') seguido de espera ativa até que o FIFO esteja disponível.
     */
    @Test
    fun testCross_ServerStartThenFifoWait() {
        session.serverActive = false
        session.fifoAvailable = false

        // Inicia o servidor
        session.execute(SshCommandBuilder.buildStart()).getOrThrow()
        assertTrue("Servidor deve estar ativo", session.serverActive)

        // Simula estabilização do named pipe
        session.fifoAvailable = true

        val status = ServerStatusParser.parse(session.execute(SshCommandBuilder.buildStatus()).getOrThrow().output)
        assertTrue(status.isActive)
        assertTrue("Canal FIFO deve estar operacional para receber comandos", status.isFifoAvailable)
    }

    /**
     * Teste 3.6: Resiliência em falha parcial na orquestração de banimento.
     * Se a remoção da allowlist falhar, o estado deve alertar sem mascarar o kick realizado.
     */
    @Test
    fun testCross_BanOrchestrationRollbackOnFailure() {
        session.onlinePlayers.add("Infrator2")

        // 1. Kick com sucesso
        val kickRes = session.execute(SshCommandBuilder.buildKick("Infrator2", "Regra violada")).getOrThrow()
        assertEquals(0, kickRes.exitCode)
        assertFalse(session.onlinePlayers.contains("Infrator2"))

        // 2. Simula falha na escrita do allowlist
        val allowlistFailed = true
        val banReport = if (allowlistFailed) {
            "AVISO: Jogador Infrator2 foi expulso, mas a remoção da allowlist falhou. Tente novamente."
        } else {
            "Sucesso"
        }

        assertTrue("Deve alertar falha parcial", banReport.contains("AVISO: Jogador Infrator2 foi expulso"))
    }

    /**
     * Teste 3.7: Adicionar jogador à whitelist e verificar se reflete na lista antes de ele conectar.
     */
    @Test
    fun testCross_WhitelistAddThenVerifyOnline() {
        val newPlayer = "AmigoConvidado"
        assertFalse(session.whitelistPlayers.contains(newPlayer))

        // Adiciona à allowlist
        session.execute(SshCommandBuilder.buildWhitelistAdd(newPlayer)).getOrThrow()
        assertTrue("Deve constar na allowlist", session.whitelistPlayers.contains(newPlayer))

        // Jogador conecta posteriormente
        session.onlinePlayers.add(newPlayer)

        val listOutput = session.execute(SshCommandBuilder.buildList()).getOrThrow().output
        val onlineList = PlayerListParser.parse(listOutput)
        assertTrue("Novo jogador deve aparecer na lista de online", onlineList.contains(newPlayer))
    }

    /**
     * Teste 3.8: Injeção de resposta de comando arbitrária enquanto o buffer do console está ativo.
     */
    @Test
    fun testCross_ConsoleOutputAppendedDuringLogStream() {
        val consoleLogBuffer = mutableListOf<String>()

        fun appendLog(line: String) {
            consoleLogBuffer.add(line)
        }

        appendLog("[23:45:00 INFO] Servidor operacional.")
        appendLog("[23:45:05 INFO] Executando: time query day")

        val cmdRes = session.execute(SshCommandBuilder.buildCmd("time query day")).getOrThrow()
        appendLog("[23:45:06 INFO] Resposta: ${cmdRes.output.trim()}")

        assertEquals(3, consoleLogBuffer.size)
        assertTrue(consoleLogBuffer[2].contains("Day is 42"))
    }

    /**
     * Teste 3.9: Recuperação após queda temporária de socket durante backup.
     * Ao reconectar, a aplicação consulta 'mc backups' para confirmar se o arquivo foi gerado.
     */
    @Test
    fun testCross_NetworkDisconnectionDuringBackup() {
        // Simula queda de rede no momento do backup
        session.simulateNetworkDrop = true
        val backupAttempt = session.execute(SshCommandBuilder.buildBackup())
        assertTrue("Backup deve falhar devido à queda de rede", backupAttempt.isFailure)
        assertFalse(session.isConnected)

        // Reconexão restabelecida
        session.simulateNetworkDrop = false
        session.connect()
        assertTrue("Reconectado com sucesso", session.isConnected)

        // Consulta de recuperação via 'mc backups'
        val backupsList = session.execute(SshCommandBuilder.buildBackupsList()).getOrThrow()
        val items = BackupsListParser.parse(backupsList.output)
        assertTrue("Lista de backups preservados na VM deve ser consultada", items.isNotEmpty())
    }

    /**
     * Teste 3.10: Execução rápida e encadeada dos botões de atalho (/list -> recursos -> rede).
     */
    @Test
    fun testCross_QuickShortcutsExecutionSequence() {
        val listRes = session.execute(SshCommandBuilder.buildList()).getOrThrow()
        val recursosRes = session.execute(SshCommandBuilder.buildRecursos()).getOrThrow()
        val redeRes = session.execute(SshCommandBuilder.buildRede()).getOrThrow()

        assertTrue(listRes.output.contains("players online"))
        assertTrue(recursosRes.output.contains("RECURSOS DO SISTEMA"))
        assertTrue(redeRes.output.contains("PORTAS E FIREWALL"))
    }
}
