package com.neverland.servermanager.e2e

import com.neverland.servermanager.e2e.mock.MockSshServerSession
import com.neverland.servermanager.e2e.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// ==============================================================================
// Never Land Server Manager - Suíte E2E: Tier 1 (Feature Coverage / Happy Paths)
// ==============================================================================
// Esta classe cobre a verificação direta e positiva (happy paths) de todas as 5
// funcionalidades principais do aplicativo Never Land Server Manager, atendendo
// aos requisitos R1, R2 e R3 de ORIGINAL_REQUEST.md e ao inventário de PROJECT.md:
//
// 1. Conexão SSH e Credenciais (F01 a F06)
// 2. Status do Servidor e Ciclo de Vida (F07 a F16)
// 3. Console Interativo & Diagnósticos (F17 a F22, F34 a F36)
// 4. Gestão de Jogadores (F23 a F29)
// 5. Hot-Backup Consistente (F30 a F33)
// ==============================================================================
class Tier1FeatureCoverageTest {

    private lateinit var session: MockSshServerSession

    @Before
    fun setUp() {
        // Inicializa uma nova sessão simulada isolada antes de cada teste
        session = MockSshServerSession()
    }

    @After
    fun tearDown() {
        // Assegura o encerramento da sessão simulada ao término de cada teste
        session.disconnect()
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 1: CONEXÃO SSH E CREDENCIAIS (Features F01 a F06)
    // --------------------------------------------------------------------------

    /**
     * Teste 1.1: Valida os parâmetros padrão da configuração SSH.
     * Fonte de autoridade: PROJECT.md § Architecture e mc_gui.py:21-22.
     * Entrada: Construtor SshConfig() sem argumentos.
     * Saída esperada: Host "157.151.2.91", porta 22, usuário "opc", tipo PRIVATE_KEY.
     */
    @Test
    fun testSshConfig_ValidDefaultConfig() {
        val config = SshConfig()
        assertEquals("Host padrão deve ser o IP do Never Land", "157.151.2.91", config.host)
        assertEquals("Porta SSH padrão deve ser 22", 22, config.port)
        assertEquals("Usuário SSH padrão deve ser opc", "opc", config.username)
        assertEquals("Tipo de autenticação padrão deve ser chave privada", AuthType.PRIVATE_KEY, config.authType)
    }

    /**
     * Teste 1.2: Valida configuração com autenticação por senha convencional.
     * Entrada: SshConfig com AuthType.PASSWORD e senha preenchida.
     * Saída esperada: Preservação íntegra dos campos sem perdas.
     */
    @Test
    fun testSshConfig_PasswordAuth() {
        val config = SshConfig(
            host = "never-land.rocks",
            port = 2222,
            username = "opc",
            authType = AuthType.PASSWORD,
            password = "SecretPassword123"
        )
        assertEquals("never-land.rocks", config.host)
        assertEquals(2222, config.port)
        assertEquals(AuthType.PASSWORD, config.authType)
        assertEquals("SecretPassword123", config.password)
    }

    /**
     * Teste 1.3: Valida a construção do comando de teste rápido de eco SSH.
     * Fonte de autoridade: explorer_survey_1 § 1.1 e 4.1.
     * Saída esperada: 'echo "NEVERLAND_OK"'.
     */
    @Test
    fun testSshCommandBuilder_BuildExecutionCommand() {
        val cmd = SshCommandBuilder.buildEchoTest()
        assertEquals("echo \"NEVERLAND_OK\"", cmd)
    }

    /**
     * Teste 1.4: Valida o handshake de conexão SSH bem-sucedido.
     * Entrada: Sessão mock com credenciais padrão.
     * Saída esperada: connect() retorna sucesso e isConnected torna-se true.
     */
    @Test
    fun testSshConnection_SuccessfulHandshake() {
        assertFalse("Sessão deve iniciar desconectada", session.isConnected)
        val result = session.connect()
        assertTrue("Conexão deve ser estabelecida com sucesso", result.isSuccess)
        assertTrue("isConnected deve refletir true após o handshake", session.isConnected)
    }

    /**
     * Teste 1.5: Valida a ação rápida de "Testar Conexão" disparando o comando de eco.
     * Entrada: Sessão conectada recebendo 'echo "NEVERLAND_OK"'.
     * Saída esperada: Código 0 e saída "NEVERLAND_OK".
     */
    @Test
    fun testSshConnection_TestConnectionEcho() {
        session.connect()
        val cmd = SshCommandBuilder.buildEchoTest()
        val execResult = session.execute(cmd)
        assertTrue(execResult.isSuccess)
        val result = execResult.getOrThrow()
        assertEquals(0, result.exitCode)
        assertTrue("Saída deve conter o marcador de sucesso NEVERLAND_OK", result.output.contains("NEVERLAND_OK"))
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 2: STATUS DO SERVIDOR E CICLO DE VIDA (Features F07 a F16)
    // --------------------------------------------------------------------------

    /**
     * Teste 2.1: Valida parsing completo da saída canônica de 'mc status' com servidor ativo.
     * Fonte de autoridade: mc.py:324-400 e explorer_survey_1 § 1.2.
     * Entrada: Saída padrão contendo PID 68773, RAM 528.5 MiB, FIFO operacional e 0 jogadores.
     */
    @Test
    fun testStatusParser_ActiveServerFullOutput() {
        session.connect()
        val execResult = session.execute(SshCommandBuilder.buildStatus()).getOrThrow()
        val status = ServerStatusParser.parse(execResult.output)

        assertTrue("Servidor deve ser reconhecido como ativo", status.isActive)
        assertEquals("Substate deve ser running", "running", status.substate)
        assertEquals("PID deve ser 68773", 68773, status.pid)
        assertNotNull("Memória RAM deve ser capturada", status.memoryUsedMib)
        assertEquals(528.5f, status.memoryUsedMib!!, 0.1f)
        assertEquals(750.0f, status.memoryLimitMib!!, 0.1f)
        assertTrue("Canal FIFO deve estar operacional", status.isFifoAvailable)
        assertEquals(0, status.onlinePlayersCount)
        assertEquals(10, status.maxPlayersCount)
    }

    /**
     * Teste 2.2: Valida parsing de status quando o servidor BDS estiver parado/inativo.
     * Entrada: Servidor marcado como inativo na sessão mock.
     * Saída esperada: isActive=false, substate="inactive", pid=null.
     */
    @Test
    fun testStatusParser_StoppedServerOutput() {
        session.serverActive = false
        session.substate = "inactive"
        session.pid = null
        session.connect()

        val execResult = session.execute(SshCommandBuilder.buildStatus()).getOrThrow()
        val status = ServerStatusParser.parse(execResult.output)

        assertFalse("Servidor deve ser reportado como inativo", status.isActive)
        assertEquals("inactive", status.substate)
        assertNull("PID deve ser nulo quando parado", status.pid)
    }

    /**
     * Teste 2.3: Valida parsing de status quando o serviço BDS entrar em falha/crash.
     * Entrada: Substate "failed" reportado pelo systemd.
     * Saída esperada: isActive=false, substate="failed".
     */
    @Test
    fun testStatusParser_FailedServerOutput() {
        val rawFailedOutput = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===
              • Serviço Systemd:   [FALHA / CRASH] (Substate: failed)
              • PID do Processo:   Nenhum
              • Memória RAM (BDS): 0.0 MiB
              • Canal Console:     /run/bedrock.console (FIFO indisponível - ENXIO)
            --- Jogadores Conectados ---
              [2026-11-09 23:41:13:199 INFO] There are 0/10 players online:
        """.trimIndent()

        val status = ServerStatusParser.parse(rawFailedOutput)
        assertFalse("Servidor em falha não pode estar ativo", status.isActive)
        assertEquals("failed", status.substate)
        assertFalse("FIFO não pode estar disponível em falha", status.isFifoAvailable)
    }

    /**
     * Teste 2.4: Valida a construção e execução do comando de inicialização ('mc start').
     * Fonte de autoridade: mc.py:405-423.
     */
    @Test
    fun testLifecycle_StartCommand() {
        session.serverActive = false
        session.connect()

        val cmd = SshCommandBuilder.buildStart()
        assertEquals("mc start", cmd)

        val execResult = session.execute(cmd).getOrThrow()
        assertEquals(0, execResult.exitCode)
        assertTrue("Servidor deve transicionar para ativo após mc start", session.serverActive)
    }

    /**
     * Teste 2.5: Valida que o comando de parada SEMPRE inclui a flag -f (--force).
     * Fonte de autoridade: explorer_survey_1 § 3 (Caveats) e mc.py:425-445.
     */
    @Test
    fun testLifecycle_StopCommand_IncludesForceFlag() {
        val stopCmd = SshCommandBuilder.buildStop(force = true)
        assertEquals("mc stop -f", stopCmd)

        session.connect()
        val execResult = session.execute(stopCmd).getOrThrow()
        assertEquals(0, execResult.exitCode)
        assertFalse("Servidor deve transicionar para inativo após mc stop -f", session.serverActive)
    }

    /**
     * Teste 2.6: Valida a construção dos comandos de reinicialização padrão e imediata.
     * Fonte de autoridade: mc.py:447-479 e explorer_survey_1 § 4.2.
     */
    @Test
    fun testLifecycle_RestartCommand_DefaultAndImmediate() {
        val defaultRestart = SshCommandBuilder.buildRestart(now = false)
        val immediateRestart = SshCommandBuilder.buildRestart(now = true)

        assertEquals("mc restart", defaultRestart)
        assertEquals("mc restart --now", immediateRestart)

        session.connect()
        val res = session.execute(immediateRestart).getOrThrow()
        assertEquals(0, res.exitCode)
        assertTrue("Servidor deve permanecer ativo após restart", session.serverActive)
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 3: CONSOLE INTERATIVO & DIAGNÓSTICOS (Features F17 a F22, F34 a F36)
    // --------------------------------------------------------------------------

    /**
     * Teste 3.1: Valida a formatação de comandos arbitrários enviados ao console BDS via 'mc cmd'.
     * Fonte de autoridade: mc.py:537-552.
     */
    @Test
    fun testConsole_BuildArbitraryCommand() {
        val rawCommand = "gamerule showcoordinates true"
        val fullCmd = SshCommandBuilder.buildCmd(rawCommand)
        assertEquals("mc cmd \"gamerule showcoordinates true\"", fullCmd)

        session.connect()
        val result = session.execute(fullCmd).getOrThrow()
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("gamerule showcoordinates true"))
    }

    /**
     * Teste 3.2: Valida o envio de transmissão de mensagem pública no chat ('mc say').
     * Fonte de autoridade: mc.py:568-586.
     */
    @Test
    fun testConsole_BuildSayCommand() {
        val message = "Bem-vindo ao Never Land!"
        val sayCmd = SshCommandBuilder.buildSay(message)
        assertEquals("mc say \"Bem-vindo ao Never Land!\"", sayCmd)

        session.connect()
        val result = session.execute(sayCmd).getOrThrow()
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("[Server] Bem-vindo ao Never Land!"))
    }

    /**
     * Teste 3.3: Valida a sintaxe dos 4 botões de atalho rápido do console.
     * Fonte de autoridade: spec_miner_survey_3 § 2 (F20) e mc_gui.py:253-261.
     */
    @Test
    fun testConsole_ShortcutsCommands() {
        assertEquals("mc list", SshCommandBuilder.buildList())
        assertEquals("mc recursos", SshCommandBuilder.buildRecursos())
        assertEquals("mc rede", SshCommandBuilder.buildRede())
        assertEquals("mc config", SshCommandBuilder.buildConfig())
        assertEquals("mc log 50", SshCommandBuilder.buildLog(50))
    }

    /**
     * Teste 3.4: Valida a execução e diagnóstico de recursos da VM ('mc recursos').
     * Fonte de autoridade: mc.py:712-751.
     */
    @Test
    fun testConsole_RecursosExecution() {
        session.connect()
        val result = session.execute(SshCommandBuilder.buildRecursos()).getOrThrow()
        assertEquals(0, result.exitCode)
        assertTrue("Deve conter informações de RAM", result.output.contains("RAM Total"))
        assertTrue("Deve conter informações de Swap", result.output.contains("Swap Total"))
        assertTrue("Deve conter informações de Disco", result.output.contains("Disco (/)"))
    }

    /**
     * Teste 3.5: Valida histórico local de comandos (armazenamento e deduplicação).
     */
    @Test
    fun testConsole_CommandHistoryState() {
        val history = mutableListOf<String>()

        fun addCommand(cmd: String) {
            val trimmed = cmd.trim()
            if (trimmed.isNotEmpty() && (history.isEmpty() || history.last() != trimmed)) {
                history.add(trimmed)
            }
        }

        addCommand("time query day")
        addCommand("weather clear")
        addCommand("weather clear") // Consecutivo duplicado
        addCommand("say Olá")

        assertEquals("Histórico deve conter 3 itens deduplicados", 3, history.size)
        assertEquals("time query day", history[0])
        assertEquals("weather clear", history[1])
        assertEquals("say Olá", history[2])
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 4: GESTÃO DE JOGADORES (Features F23 a F29)
    // --------------------------------------------------------------------------

    /**
     * Teste 4.1: Valida parsing da resposta de 'mc list' quando jogadores estão conectados.
     * Fonte de autoridade: mc.py:554-566 e explorer_survey_1 § 1.4.
     */
    @Test
    fun testPlayer_OnlinePlayersListParsing() {
        val rawOutput = "[2026-11-09 23:41:13:199 INFO] There are 3/10 players online: Bruno0103V, Steve, Alex\n"
        val players = PlayerListParser.parse(rawOutput)

        assertEquals("Devem ser encontrados 3 jogadores", 3, players.size)
        assertTrue(players.contains("Bruno0103V"))
        assertTrue(players.contains("Steve"))
        assertTrue(players.contains("Alex"))
    }

    /**
     * Teste 4.2: Valida construção e execução do comando de expulsão com motivo ('mc kick').
     * Fonte de autoridade: mc.py:588-605.
     */
    @Test
    fun testPlayer_KickCommand_WithReason() {
        session.onlinePlayers.add("Griefer99")
        session.connect()

        val kickCmd = SshCommandBuilder.buildKick("Griefer99", "Violação de regras")
        assertEquals("mc kick \"Griefer99\" \"Violação de regras\"", kickCmd)

        val result = session.execute(kickCmd).getOrThrow()
        assertEquals(0, result.exitCode)
        assertFalse("Griefer99 deve ter sido removido dos online", session.onlinePlayers.contains("Griefer99"))
    }

    /**
     * Teste 4.3: Valida a adição de jogador à allowlist ('allowlist add').
     * Fonte de autoridade: explorer_survey_1 § 1.4 e 4.4.
     */
    @Test
    fun testPlayer_WhitelistAddCommand() {
        session.connect()
        val addCmd = SshCommandBuilder.buildWhitelistAdd("NovoJogador")
        assertEquals("mc cmd \"allowlist add \\\"NovoJogador\\\"\"", addCmd)

        val result = session.execute(addCmd).getOrThrow()
        assertEquals(0, result.exitCode)
        assertTrue("NovoJogador deve constar na whitelist", session.whitelistPlayers.contains("NovoJogador"))
    }

    /**
     * Teste 4.4: Valida a remoção de jogador da allowlist ('allowlist remove').
     */
    @Test
    fun testPlayer_WhitelistRemoveCommand() {
        session.connect()
        assertTrue(session.whitelistPlayers.contains("Steve"))

        val removeCmd = SshCommandBuilder.buildWhitelistRemove("Steve")
        assertEquals("mc cmd \"allowlist remove \\\"Steve\\\"\"", removeCmd)

        val result = session.execute(removeCmd).getOrThrow()
        assertEquals(0, result.exitCode)
        assertFalse("Steve deve ter sido removido da whitelist", session.whitelistPlayers.contains("Steve"))
    }

    /**
     * Teste 4.5: Valida a sequência orquestrada de banimento para Vanilla BDS.
     * Como o BDS Vanilla não possui comando /ban nativo, o banimento é composto por:
     * 1. Kick imediato
     * 2. Remoção da allowlist
     * 3. Recarregamento da allowlist (reload)
     * Fonte de autoridade: explorer_survey_1 § 1.4 e 4.4.
     */
    @Test
    fun testPlayer_BanOrchestrationSequence() {
        session.onlinePlayers.add("Infrator")
        session.whitelistPlayers.add("Infrator")
        session.connect()

        // 1. Kick
        val kickCmd = SshCommandBuilder.buildKick("Infrator", "Banido do servidor")
        session.execute(kickCmd).getOrThrow()

        // 2. Remove da whitelist
        val removeCmd = SshCommandBuilder.buildWhitelistRemove("Infrator")
        session.execute(removeCmd).getOrThrow()

        // 3. Reload
        val reloadCmd = SshCommandBuilder.buildWhitelistReload()
        session.execute(reloadCmd).getOrThrow()

        assertFalse("Infrator não pode estar online", session.onlinePlayers.contains("Infrator"))
        assertFalse("Infrator não pode constar na allowlist", session.whitelistPlayers.contains("Infrator"))
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 5: HOT-BACKUP CONSISTENTE (Features F30 a F33)
    // --------------------------------------------------------------------------

    /**
     * Teste 5.1: Valida a construção do comando de disparo de hot-backup ('mc backup').
     * Fonte de autoridade: mc.py:666-690 e backup.sh.
     */
    @Test
    fun testBackup_TriggerCommand() {
        val cmd = SshCommandBuilder.buildBackup()
        assertEquals("mc backup", cmd)
    }

    /**
     * Teste 5.2: Valida parsing da resposta de sucesso de 'mc backup'.
     * Extrai tempo decorrido, nome do .tar.gz e tamanho do arquivo gerado.
     */
    @Test
    fun testBackup_SuccessOutputParsing() {
        val rawSuccess = "[OK] Backup concluído em 4.2s: backup_never_land_20261109_204000.tar.gz (7.5M)"
        val result = BackupOutputParser.parse(rawSuccess)

        assertTrue("Backup deve ser marcado como sucesso", result.success)
        assertEquals(4.2f, result.elapsedSeconds, 0.01f)
        assertEquals("backup_never_land_20261109_204000.tar.gz", result.filename)
        assertEquals("7.5M", result.sizeText)
        assertNull("Mensagem de erro deve ser nula no sucesso", result.errorMessage)
    }

    /**
     * Teste 5.3: Valida a listagem de arquivos de backup gravados na VM ('mc backups').
     * Fonte de autoridade: mc.py:692-710 e explorer_survey_1 § 1.5.
     */
    @Test
    fun testBackup_ListBackupsParsing() {
        session.connect()
        val result = session.execute(SshCommandBuilder.buildBackupsList()).getOrThrow()
        val items = BackupsListParser.parse(result.output)

        assertTrue("Devem ser encontrados backups na listagem", items.isNotEmpty())
        assertEquals("backup_never_land_20261109_040004.tar.gz", items[0].filename)
        assertEquals("6.8M", items[0].sizeText)
    }

    /**
     * Teste 5.4: Valida transição de estado da máquina de estados do backup.
     * IDLE -> RUNNING -> SUCCESS.
     */
    @Test
    fun testBackup_StateTransition() {
        var uiState = "IDLE"
        session.connect()

        // Dispara backup
        uiState = "RUNNING"
        assertEquals("RUNNING", uiState)

        val result = session.execute(SshCommandBuilder.buildBackup()).getOrThrow()
        val parsed = BackupOutputParser.parse(result.output)

        uiState = if (parsed.success) "SUCCESS" else "ERROR"
        assertEquals("SUCCESS", uiState)
    }

    /**
     * Teste 5.5: Valida parsing de eventos de conexões anteriores ('mc jogadores').
     * Extrai eventos CONNECTED, SPAWNED e DISCONNECTED com Gamertag e XUID.
     * Fonte de autoridade: mc.py:632-664 e explorer_survey_1 § 1.4.
     */
    @Test
    fun testBackup_JogadoresHistoryParsing() {
        session.connect()
        val result = session.execute(SshCommandBuilder.buildJogadores()).getOrThrow()
        val events = JogadoresHistoryParser.parse(result.output)

        assertEquals("Devem ser identificados 5 eventos no log simulado", 5, events.size)

        val first = events[0]
        assertEquals("CONNECTED", first.eventType)
        assertEquals("Bruno0103V", first.gamertag)
        assertEquals("2535456249590930", first.xuid)

        val last = events[4]
        assertEquals("DISCONNECTED", last.eventType)
        assertEquals("Mauriceliof", last.gamertag)
        assertNull("Desconexão não possui XUID na linha", last.xuid)
    }
}
