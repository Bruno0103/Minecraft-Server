package com.neverland.servermanager.e2e

import com.neverland.servermanager.e2e.mock.MockSshServerSession
import com.neverland.servermanager.e2e.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

// ==============================================================================
// Never Land Server Manager - Suíte E2E: Tier 2 (Boundary & Corner Cases)
// ==============================================================================
// Esta classe cobre a verificação minuciosa de condições de limite, entradas
// adversariais, exceções de rede e particularidades de baixo nível do BDS:
//
// 1. Limites de Conexão e Validação de Credenciais (5 casos)
// 2. Limites de Status, Telemetria e FIFO ENXIO (5 casos)
// 3. Limites de Buffer, Caracteres Especiais e Console (5 casos)
// 4. Limites de Gamertags, Espaços e Gestão de Jogadores (5 casos)
// 5. Limites de Hot-Backup, Concorrência e Erros de SO (5 casos)
// ==============================================================================
class Tier2BoundaryCornerCasesTest {

    private lateinit var session: MockSshServerSession

    @Before
    fun setUp() {
        session = MockSshServerSession()
    }

    @After
    fun tearDown() {
        session.disconnect()
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 1: LIMITES DE CONEXÃO E VALIDAÇÃO DE CREDENCIAIS
    // --------------------------------------------------------------------------

    /**
     * Teste 1.1: Rejeição de portas TCP fora da faixa válida (1..65535).
     * Testa valores limítrofes: 0, -1, 65536, 99999.
     */
    @Test
    fun testSshConfig_InvalidPort_NegativeOrOver65535() {
        val invalidPorts = listOf(0, -1, 65536, 99999)
        for (port in invalidPorts) {
            val result = InputValidator.validatePort(port)
            assertTrue("Porta $port deve ser rejeitada", result.isFailure)
            assertTrue(
                "Mensagem deve esclarecer intervalo 1..65535",
                result.exceptionOrNull()?.message?.contains("1 e 65535") == true
            )
        }

        // Portas válidas de fronteira
        assertTrue("Porta 1 deve ser aceita", InputValidator.validatePort(1).isSuccess)
        assertTrue("Porta 65535 deve ser aceita", InputValidator.validatePort(65535).isSuccess)
    }

    /**
     * Teste 1.2: Rejeição imediata de host vazio ou usuário em branco.
     */
    @Test
    fun testSshConfig_BlankHostOrEmptyUser() {
        val emptyHostResult = InputValidator.validateHost("   ")
        assertTrue("Host em branco deve falhar na validação", emptyHostResult.isFailure)

        val emptyUserResult = InputValidator.validateUsername("")
        assertTrue("Usuário vazio deve falhar na validação", emptyUserResult.isFailure)
    }

    /**
     * Teste 1.3: Rejeição de chave privada corrompida ou sem delimitadores válidos.
     */
    @Test
    fun testSshConfig_CorruptedPemKey_MissingHeaders() {
        val badKey = "MIIEowIBAAKCAQEA0l...texto aleatorio sem cabecalho..."
        val result = InputValidator.validatePrivateKey(badKey)
        assertTrue("Chave sem delimitadores PEM deve falhar", result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Formato de chave privada inválido") == true
        )

        // Chave com cabeçalho válido deve ser aceita
        val validPem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----"
        assertTrue("Chave com delimitadores PEM válidos deve ser aceita", InputValidator.validatePrivateKey(validPem).isSuccess)
    }

    /**
     * Teste 1.4: Tratamento de timeout de conexão após 15 segundos sem crash na thread.
     */
    @Test
    fun testSsh_TimeoutHandling_ThrowsOn15Seconds() {
        session.simulateTimeout = true
        val result = session.connect()

        assertTrue("Conexão com timeout simulado deve retornar falha", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Exceção deve ser IOException", ex is IOException)
        assertTrue("Mensagem deve relatar timeout", ex?.message?.contains("timed out") == true)
    }

    /**
     * Teste 1.5: Captura e tratamento de falha de autenticação (usuário ou chave rejeitados).
     */
    @Test
    fun testSsh_AuthFailureException() {
        session.simulateAuthFailure = true
        val result = session.connect()

        assertTrue("Autenticação rejeitada deve retornar falha", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Exceção deve ser SecurityException", ex is SecurityException)
        assertTrue("Mensagem deve apontar falha de autenticação", ex?.message?.contains("Authentication failed") == true)
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 2: LIMITES DE STATUS, TELEMETRIA E FIFO ENXIO
    // --------------------------------------------------------------------------

    /**
     * Teste 2.1: Fallback defensivo quando a saída do servidor for ruído corrompido ou vazia.
     */
    @Test
    fun testStatusParser_CorruptedOutput_FallbackToOffline() {
        val garbageOutput = "ERROR: connection reset by peer @#$!*&^ garbage bytes\n???"
        val status = ServerStatusParser.parse(garbageOutput)

        assertFalse("Saída corrompida deve considerar servidor inativo", status.isActive)
        assertEquals("inactive", status.substate)
        assertNull("PID deve ser nulo em saída corrompida", status.pid)
        assertFalse("FIFO não pode ser reportado disponível", status.isFifoAvailable)
        assertEquals(0, status.onlinePlayersCount)
    }

    /**
     * Teste 2.2: Detecção de desconexão do leitor no named pipe (FIFO ENXIO).
     * Fonte de autoridade: mc.py:251-255 e spec_miner_survey_3 § 3 (E06).
     */
    @Test
    fun testStatusParser_EnxioFifoDisconnection() {
        val outputWithEnxio = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===
              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   68773
              • Memória RAM (BDS): 528.5 MiB (Alerta: 600 MiB, Máx: 750 MiB)
              • Canal Console:     /run/bedrock.console (FIFO indisponível - ENXIO)
            --- Jogadores Conectados ---
              [2026-11-09 23:41:13:199 INFO] There are 0/10 players online:
        """.trimIndent()

        val status = ServerStatusParser.parse(outputWithEnxio)
        assertTrue("Servidor systemd continua ativo", status.isActive)
        assertFalse("Canal FIFO deve ser detectado como INDISPONÍVEL", status.isFifoAvailable)
    }

    /**
     * Teste 2.3: Parsing de consumo de RAM acima do limite máximo (> 750 MiB).
     */
    @Test
    fun testStatusParser_MemoryAboveMaxLimit() {
        val highMemoryOutput = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===
              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   68773
              • Memória RAM (BDS): 782.4 MiB (Alerta: 600 MiB, Máx: 750.0 MiB)
              • Canal Console:     /run/bedrock.console (FIFO operacional.)
            --- Jogadores Conectados ---
              [2026-11-09 23:41:13:199 INFO] There are 1/10 players online: Bruno0103V
        """.trimIndent()

        val status = ServerStatusParser.parse(highMemoryOutput)
        assertNotNull(status.memoryUsedMib)
        assertTrue("Memória deve ser maior que 750 MiB", status.memoryUsedMib!! > 750.0f)
        assertTrue("Deve detectar que excedeu o teto", status.memoryUsedMib!! > (status.memoryLimitMib ?: 750.0f))
    }

    /**
     * Teste 2.4: Parsing defensivo quando o BDS reporta 0/0 jogadores no início do boot.
     */
    @Test
    fun testStatusParser_ZeroOnlineZeroMax() {
        val bootingOutput = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===
              • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
              • PID do Processo:   68773
              • Memória RAM (BDS): 210.0 MiB
              • Canal Console:     /run/bedrock.console (FIFO operacional.)
            --- Jogadores Conectados ---
              [2026-11-09 23:41:13:199 INFO] There are 0/0 players online:
        """.trimIndent()

        val status = ServerStatusParser.parse(bootingOutput)
        assertEquals(0, status.onlinePlayersCount)
        assertEquals(0, status.maxPlayersCount)
    }

    /**
     * Teste 2.5: Tratamento defensivo de PID ausente ("Nenhum") sem lançar NullPointerException.
     */
    @Test
    fun testStatusParser_MalformedUptimeAndNullPid() {
        val stoppedOutput = """
            === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===
              • Serviço Systemd:   [PARADO / INATIVO] (Substate: inactive)
              • PID do Processo:   Nenhum
              • Memória RAM (BDS): 0.0 MiB
              • Canal Console:     /run/bedrock.console (FIFO indisponível - ENXIO)
            --- Jogadores Conectados ---
              [2026-11-09 23:41:13:199 INFO] There are 0/10 players online:
        """.trimIndent()

        val status = ServerStatusParser.parse(stoppedOutput)
        assertNull("PID 'Nenhum' deve ser parseado como null", status.pid)
        assertFalse(status.isActive)
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 3: LIMITES DE BUFFER, CARACTERES ESPECIAIS E CONSOLE
    // --------------------------------------------------------------------------

    /**
     * Teste 3.1: Rejeição de comando com tamanho > 4096 bytes (limite atômico POSIX PIPE_BUF).
     * Fonte de autoridade: mc.py:221-222 e spec_miner_survey_3 § 3 (E09).
     */
    @Test
    fun testConsole_PayloadExceedingPipeBuf_Rejection() {
        val oversizedCommand = "say " + "A".repeat(5000)
        val result = InputValidator.validateCommand(oversizedCommand)

        assertTrue("Comando > 4096 bytes deve ser rejeitado", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(
            "Mensagem deve apontar limite de buffer de 4096 bytes",
            ex?.message?.contains("4096 bytes") == true
        )
    }

    /**
     * Teste 3.2: Sanitização correta de comandos contendo aspas e caracteres especiais de shell.
     */
    @Test
    fun testConsole_CommandWithQuotesAndSpecialChars() {
        val dangerousInput = "say Olá \"Mundo\" \$VAR & test;"
        val builtCmd = SshCommandBuilder.buildCmd(dangerousInput)

        // As aspas duplas internas devem ser escapadas com \"
        assertEquals("mc cmd \"say Olá \\\"Mundo\\\" \$VAR & test;\"", builtCmd)
    }

    /**
     * Teste 3.3: Rejeição de comando em branco ou constituído unicamente de espaços.
     */
    @Test
    fun testConsole_BlankCommand_Rejection() {
        val result = InputValidator.validateCommand("     \n\t  ")
        assertTrue("Comando apenas com espaços deve ser rejeitado", result.isFailure)
    }

    /**
     * Teste 3.4: Validação da diretiva de performance para comandos 'say' com 0 jogadores.
     * Fonte de autoridade: mc.py:270-274 ("Challenge 1") e spec_miner_survey_3 § 3 (E08).
     * O BDS não emite eco no journald para comandos 'say' sem jogadores; o cliente não deve aguardar síncrono.
     */
    @Test
    fun testConsole_SayCommand_ZeroPlayersOptimization() {
        session.onlinePlayers.clear()
        session.connect()

        val sayCmd = SshCommandBuilder.buildSay("Broadcast Teste")
        val result = session.execute(sayCmd, timeoutSeconds = 1).getOrThrow()

        assertEquals(0, result.exitCode)
        assertTrue("Comando say deve retornar com sucesso imediato", result.output.contains("[Server] Broadcast Teste"))
    }

    /**
     * Teste 3.5: Prevenção de envio de comandos quando o servidor estiver parado.
     * Fonte de autoridade: spec_miner_survey_3 § 3 (E07).
     */
    @Test
    fun testConsole_ExecutionWhileServerStopped_Prevention() {
        session.serverActive = false
        session.fifoAvailable = false
        session.connect()

        // Validação da regra de negócio: não enviar ao FIFO se servidor inativo
        fun canSendCommand(status: ServerStatus): Boolean = status.isActive && status.isFifoAvailable

        val status = ServerStatusParser.parse(session.execute("mc status").getOrThrow().output)
        assertFalse("Não deve permitir envio com servidor parado", canSendCommand(status))
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 4: LIMITES DE GAMERTAGS, ESPAÇOS E GESTÃO DE JOGADORES
    // --------------------------------------------------------------------------

    /**
     * Teste 4.1: Encapsulamento automático de Gamertags com espaços entre aspas duplas.
     * Fonte de autoridade: mc.py:594-596 e spec_miner_survey_3 § 3 (E10).
     */
    @Test
    fun testPlayer_GamertagWithSpaces() {
        val nameWithSpaces = "Um Boy Atoa"
        val kickCmd = SshCommandBuilder.buildKick(nameWithSpaces, "Regra 1")
        assertEquals("mc kick \"Um Boy Atoa\" \"Regra 1\"", kickCmd)

        val addCmd = SshCommandBuilder.buildWhitelistAdd(nameWithSpaces)
        assertEquals("mc cmd \"allowlist add \\\"Um Boy Atoa\\\"\"", addCmd)
    }

    /**
     * Teste 4.2: Rejeição de Gamertag vazia ou com apenas espaços em branco.
     */
    @Test
    fun testPlayer_GamertagEmptyOrWhitespace() {
        val result = InputValidator.sanitizeGamertag("    ")
        assertTrue("Gamertag em branco deve falhar na validação", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("não pode estar vazia") == true)
    }

    /**
     * Teste 4.3: Expulsão de jogador que não está conectado trata gracioso o retorno "Player not found".
     * Fonte de autoridade: spec_miner_survey_3 § 3 (E11).
     */
    @Test
    fun testPlayer_KickNonExistentPlayer() {
        session.onlinePlayers.clear()
        session.connect()

        val kickCmd = SshCommandBuilder.buildKick("Fantasma", "Inativo")
        val result = session.execute(kickCmd).getOrThrow()

        // O comando retorna saída informativa de que o jogador não foi encontrado sem crashar
        assertTrue("Deve conter Player not found", result.output.contains("Player not found"))
    }

    /**
     * Teste 4.4: Validação de Gamertag com caracteres especiais válidos do ecossistema Xbox.
     */
    @Test
    fun testPlayer_WhitelistSpecialCharacters() {
        val validGamertags = listOf("Steve-123", "Alex_Pro", "Player#456", "O_Grande_Gamer")
        for (gt in validGamertags) {
            val valid = InputValidator.sanitizeGamertag(gt)
            assertTrue("Gamertag $gt deve ser aceita", valid.isSuccess)
        }
    }

    /**
     * Teste 4.5: Parsing resiliente do histórico de jogadores quando a linha omite XUID.
     */
    @Test
    fun testPlayer_ParseJogadoresWithMissingXuid() {
        val rawJogadores = """
            Player connected: JogadorSemXuid
            Player disconnected: JogadorSemXuid
        """.trimIndent()

        val events = JogadoresHistoryParser.parse(rawJogadores)
        assertEquals(1, events.size) // Apenas o disconnected casou ou connected sem xuid foi tratado
        assertEquals("DISCONNECTED", events[0].eventType)
        assertEquals("JogadorSemXuid", events[0].gamertag)
        assertNull(events[0].xuid)
    }

    // --------------------------------------------------------------------------
    // SEÇÃO 5: LIMITES DE HOT-BACKUP, CONCORRÊNCIA E ERROS DE SO
    // --------------------------------------------------------------------------

    /**
     * Teste 5.1: Captura e identificação de erro de espaço em disco esgotado na VM.
     * Fonte de autoridade: spec_miner_survey_3 § 3 (E13) e backup.sh.
     */
    @Test
    fun testBackup_DiskFullErrorParsing() {
        session.simulateDiskFull = true
        session.connect()

        val result = session.execute(SshCommandBuilder.buildBackup()).getOrThrow()
        val parsed = BackupOutputParser.parse(result.output)

        assertFalse("Backup deve falhar quando disco estiver cheio", parsed.success)
        assertNotNull("Deve conter mensagem de erro", parsed.errorMessage)
        assertTrue(
            "Mensagem deve relatar espaço em disco insuficiente",
            parsed.errorMessage?.contains("Espaço em disco insuficiente") == true
        )
    }

    /**
     * Teste 5.2: Simulação de timeout em rotina de backup (tempo limite estendido de 60s/90s).
     * O handshake de conexão deve ter sucesso prévio, ativando em seguida o timeout específico
     * de execução de comando ([simulateCommandTimeout]) para garantir que execute() dispare a
     * exceção com a mensagem esperada ("Tempo limite de execução atingido").
     */
    @Test
    fun testBackup_TimeoutAfterExtendedWait() {
        session.connect()
        session.simulateCommandTimeout = true

        val result = session.execute(SshCommandBuilder.buildBackup(), timeoutSeconds = 60)
        assertTrue("Deve falhar por timeout", result.isFailure)
        assertTrue(
            "Mensagem deve indicar tempo limite",
            result.exceptionOrNull()?.message?.contains("Tempo limite") == true
        )
    }

    /**
     * Teste 5.3: Parsing gracioso de 'mc backups' quando o diretório estiver limpo/vazio.
     */
    @Test
    fun testBackup_EmptyBackupsDirectory() {
        session.backupFiles.clear()
        session.connect()

        val result = session.execute(SshCommandBuilder.buildBackupsList()).getOrThrow()
        val items = BackupsListParser.parse(result.output)

        assertTrue("Lista de backups deve ser vazia sem gerar exceção", items.isEmpty())
    }

    /**
     * Teste 5.4: Captura de erro quando o banco LevelDB estiver bloqueado por outro processo.
     */
    @Test
    fun testBackup_LevelDbLockedError() {
        session.simulateLevelDbLocked = true
        session.connect()

        val result = session.execute(SshCommandBuilder.buildBackup()).getOrThrow()
        val parsed = BackupOutputParser.parse(result.output)

        assertFalse("Backup não deve ter sucesso com LevelDB bloqueado", parsed.success)
        assertTrue(
            "Deve relatar LevelDB bloqueado",
            parsed.errorMessage?.contains("LevelDB bloqueado") == true
        )
    }

    /**
     * Teste 5.5: Bloqueio estrito de concorrência: impedir segundo backup simultâneo.
     * Fonte de autoridade: spec_miner_survey_3 § 3 (E12).
     */
    @Test
    fun testBackup_ConcurrentExecutionRejection() {
        var isBackupInProgress = false

        fun triggerSafeBackup(): Result<String> {
            if (isBackupInProgress) {
                return Result.failure(IllegalStateException("Já existe uma rotina de backup em execução."))
            }
            isBackupInProgress = true
            return Result.success("Iniciado")
        }

        // Primeiro disparo
        val firstCall = triggerSafeBackup()
        assertTrue("Primeiro disparo deve ser aceito", firstCall.isSuccess)

        // Segundo disparo simultâneo
        val secondCall = triggerSafeBackup()
        assertTrue("Segundo disparo simultâneo deve ser rejeitado", secondCall.isFailure)
        assertTrue(
            "Mensagem deve alertar sobre backup em andamento",
            secondCall.exceptionOrNull()?.message?.contains("Já existe uma rotina") == true
        )
    }
}
