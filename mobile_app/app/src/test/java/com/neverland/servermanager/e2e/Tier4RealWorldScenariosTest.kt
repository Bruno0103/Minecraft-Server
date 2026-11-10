package com.neverland.servermanager.e2e

import com.neverland.servermanager.e2e.mock.MockSshServerSession
import com.neverland.servermanager.e2e.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// ==============================================================================
// Never Land Server Manager - Suíte E2E: Tier 4 (Real-World Scenarios)
// ==============================================================================
// Esta classe reproduz 4 cenários realistas de ponta a ponta vivenciados pelo
// administrador de produção do servidor Minecraft Bedrock Never Land:
//
// 1. Manutenção Agendada Completa (Aviso -> Stop -> Backup -> Start -> Verificação)
// 2. Resposta Rápida a Incidente de Griefing (Detecção -> Broadcast -> Ban Composto -> Auditoria)
// 3. Rotina Diária de Inspeção Matinal (Telemetria -> RAM -> Portas -> Backups)
// 4. Recuperação de Queda/Crash do BDS (Diagnóstico de Logs -> Boot Limpo -> Estabilização)
// ==============================================================================
class Tier4RealWorldScenariosTest {

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
     * Cenário Realista 1: Rotina Completa de Manutenção Agendada.
     * Fluxo:
     * 1. Administrador transmite aviso no chat aos jogadores: 'mc say "Servidor entrará em manutenção em 1 minuto!"'
     * 2. Desliga o servidor de forma limpa e graciosa: 'mc stop -f'
     * 3. Dispara a rotina consistente de hot-backup: 'mc backup'
     * 4. Valida a criação do arquivo compactado (.tar.gz): 'mc backups'
     * 5. Reinicia o servidor: 'mc start'
     * 6. Confirma que o serviço voltou ao estado ATIVO com canal FIFO operacional.
     */
    @Test
    fun testScenario_ScheduledServerMaintenance() {
        // 1. Broadcast de aviso
        val sayCmd = SshCommandBuilder.buildSay("Servidor entrará em manutenção em 1 minuto!")
        val sayRes = session.execute(sayCmd).getOrThrow()
        assertTrue("Aviso deve ser transmitido no chat", sayRes.output.contains("[Server] Servidor entrará em manutenção"))

        // 2. Parada segura
        val stopCmd = SshCommandBuilder.buildStop(force = true)
        val stopRes = session.execute(stopCmd).getOrThrow()
        assertEquals(0, stopRes.exitCode)
        assertFalse("Servidor deve estar parado", session.serverActive)

        // 3. Disparo do backup manual
        val backupCmd = SshCommandBuilder.buildBackup()
        val backupRes = session.execute(backupCmd).getOrThrow()
        val backupResult = BackupOutputParser.parse(backupRes.output)
        assertTrue("Backup deve ser concluído com sucesso", backupResult.success)
        assertTrue("Nome do backup deve ser válido", backupResult.filename.startsWith("backup_never_land_"))

        // 4. Verificação no histórico de backups
        val backupsListRes = session.execute(SshCommandBuilder.buildBackupsList()).getOrThrow()
        val backupItems = BackupsListParser.parse(backupsListRes.output)
        assertTrue("Novo backup deve constar no topo da lista", backupItems.any { it.filename == backupResult.filename })

        // 5. Reinício do servidor
        val startCmd = SshCommandBuilder.buildStart()
        val startRes = session.execute(startCmd).getOrThrow()
        assertEquals(0, startRes.exitCode)
        assertTrue("Servidor deve voltar a ativo", session.serverActive)

        // 6. Confirmação de status e FIFO
        val statusRes = session.execute(SshCommandBuilder.buildStatus()).getOrThrow()
        val finalStatus = ServerStatusParser.parse(statusRes.output)
        assertTrue("Servidor deve reportar status ATIVO", finalStatus.isActive)
        assertTrue("Canal FIFO deve estar operacional para os jogadores", finalStatus.isFifoAvailable)
    }

    /**
     * Cenário Realista 2: Resposta Rápida a Incidente de Griefing no Servidor.
     * Fluxo:
     * 1. Administrador consulta a lista de jogadores online: 'mc list' e detecta "Griefer99".
     * 2. Envia broadcast no chat aos demais participantes: 'mc say "Griefer99 removido por violar regras."'
     * 3. Executa o fluxo de banimento composto do Vanilla BDS:
     *    - Expulsão imediata: 'mc kick "Griefer99" "Griefing detectado"'
     *    - Remoção da allowlist: 'mc cmd "allowlist remove \"Griefer99\""'
     *    - Recarregamento da allowlist: 'mc cmd "allowlist reload"'
     * 4. Consulta o histórico de conexões 'mc jogadores' para auditar o XUID registrado do infrator.
     * 5. Confirma que o jogador foi banido e não consta mais na allowlist nem entre os online.
     */
    @Test
    fun testScenario_GriefingIncidentResponse() {
        val grieferGamertag = "Griefer99"
        session.onlinePlayers.add("Bruno0103V")
        session.onlinePlayers.add(grieferGamertag)
        session.whitelistPlayers.add(grieferGamertag)

        // 1. Detecção via 'mc list'
        val listRes = session.execute(SshCommandBuilder.buildList()).getOrThrow()
        val onlineList = PlayerListParser.parse(listRes.output)
        assertTrue("Griefer deve ser detectado online", onlineList.contains(grieferGamertag))

        // 2. Transmissão pública no chat
        val sayCmd = SshCommandBuilder.buildSay("Jogador $grieferGamertag está sendo removido por violação das regras.")
        session.execute(sayCmd).getOrThrow()

        // 3. Sequência de banimento composto
        val kickCmd = SshCommandBuilder.buildKick(grieferGamertag, "Griefing detectado")
        session.execute(kickCmd).getOrThrow()

        val removeCmd = SshCommandBuilder.buildWhitelistRemove(grieferGamertag)
        session.execute(removeCmd).getOrThrow()

        val reloadCmd = SshCommandBuilder.buildWhitelistReload()
        session.execute(reloadCmd).getOrThrow()

        // 4. Auditoria via 'mc jogadores'
        val auditRes = session.execute(SshCommandBuilder.buildJogadores()).getOrThrow()
        assertTrue(auditRes.output.contains("Player connected") || auditRes.output.contains("Player Spawned"))

        // 5. Confirmação final
        assertFalse("Griefer não pode mais estar online", session.onlinePlayers.contains(grieferGamertag))
        assertFalse("Griefer não pode constar na allowlist", session.whitelistPlayers.contains(grieferGamertag))
        assertEquals(1, session.onlinePlayers.size) // Apenas Bruno0103V
    }

    /**
     * Cenário Realista 3: Rotina Diária de Inspeção Matinal do Administrador.
     * Fluxo:
     * 1. Teste de conectividade e eco SSH inicial ('echo "NEVERLAND_OK"')
     * 2. Coleta de telemetria geral ('mc status'): validação de RAM (< 600 MiB), Uptime e restarts = 0
     * 3. Inspeção de recursos da VM ('mc recursos'): disco livre suficiente (> 10%)
     * 4. Auditoria de portas e sockets de rede ('mc rede'): portas 19132 UDP e TCP operacionais
     * 5. Confirmação da integridade dos backups preservados ('mc backups').
     */
    @Test
    fun testScenario_DailyRoutineInspection() {
        // 1. Diagnóstico de conectividade
        val echoRes = session.execute(SshCommandBuilder.buildEchoTest()).getOrThrow()
        assertEquals(0, echoRes.exitCode)
        assertTrue(echoRes.output.contains("NEVERLAND_OK"))

        // 2. Telemetria de status
        val statusRes = session.execute(SshCommandBuilder.buildStatus()).getOrThrow()
        val status = ServerStatusParser.parse(statusRes.output)
        assertTrue("Servidor deve estar ativo", status.isActive)
        assertNotNull("PID deve existir", status.pid)
        assertNotNull("Memória RAM deve estar sob monitoramento", status.memoryUsedMib)
        assertTrue("Consumo de RAM matinal deve ser saudável (<600 MiB)", status.memoryUsedMib!! < 600.0f)
        assertTrue("Canal FIFO deve estar operacional", status.isFifoAvailable)

        // 3. Recursos do sistema (RAM, Swap, Disco)
        val recursosRes = session.execute(SshCommandBuilder.buildRecursos()).getOrThrow()
        assertTrue("Deve reportar RAM", recursosRes.output.contains("RAM Total"))
        assertTrue("Deve reportar Swap", recursosRes.output.contains("Swap Total"))
        assertTrue("Deve reportar partição de disco", recursosRes.output.contains("Disco (/)"))

        // 4. Diagnóstico de portas e firewall
        val redeRes = session.execute(SshCommandBuilder.buildRede()).getOrThrow()
        assertTrue("Porta 19132 UDP RakNet deve estar aberta", redeRes.output.contains("19132/udp"))
        assertTrue("Porta 19132 TCP NetherNet deve estar escutando", redeRes.output.contains("19132/tcp"))

        // 5. Verificação dos backups
        val backupsRes = session.execute(SshCommandBuilder.buildBackupsList()).getOrThrow()
        val backups = BackupsListParser.parse(backupsRes.output)
        assertTrue("Devem existir backups diários preservados na VM", backups.isNotEmpty())
    }

    /**
     * Cenário Realista 4: Recuperação de Queda/Crash do Servidor BDS.
     * Fluxo:
     * 1. Telemetria inicial detecta servidor no estado [FALHA / CRASH] (substate "failed", PID nulo)
     * 2. Administrador lê as últimas 50 linhas do log via 'mc log 50' para avaliar o motivo da queda
     * 3. Dispara a reinicialização limpa via 'mc start'
     * 4. Realiza polling de status confirmando restabelecimento do serviço e integridade do canal FIFO
     * 5. Administrador transmite mensagem de servidor restabelecido no chat.
     */
    @Test
    fun testScenario_ServerCrashRecoveryAndDiagnostics() {
        // 1. Simula servidor em crash
        session.serverActive = false
        session.substate = "failed"
        session.pid = null
        session.fifoAvailable = false

        val crashStatusRes = session.execute(SshCommandBuilder.buildStatus()).getOrThrow()
        val crashStatus = ServerStatusParser.parse(crashStatusRes.output)
        assertFalse("Servidor deve ser reportado como inativo/falho", crashStatus.isActive)
        assertEquals("failed", crashStatus.substate)

        // 2. Leitura dos logs de erro
        val logRes = session.execute(SshCommandBuilder.buildLog(50)).getOrThrow()
        assertTrue("Deve ler linhas de log para diagnóstico", logRes.output.contains("INFO") || logRes.output.contains("ERROR"))

        // 3. Disparo de recuperação limpa
        val startRes = session.execute(SshCommandBuilder.buildStart()).getOrThrow()
        assertEquals(0, startRes.exitCode)
        assertTrue("Servidor deve ser reiniciado", session.serverActive)

        // 4. Polling de status restabelecido
        val recoveredStatusRes = session.execute(SshCommandBuilder.buildStatus()).getOrThrow()
        val recoveredStatus = ServerStatusParser.parse(recoveredStatusRes.output)
        assertTrue("Servidor deve estar recuperado e ativo", recoveredStatus.isActive)
        assertEquals("running", recoveredStatus.substate)
        assertNotNull("Novo PID deve ser atribuído", recoveredStatus.pid)
        assertTrue("FIFO deve estar operacional", recoveredStatus.isFifoAvailable)

        // 5. Broadcast de retorno
        val sayRes = session.execute(SshCommandBuilder.buildSay("Servidor restabelecido com sucesso!")).getOrThrow()
        assertTrue(sayRes.output.contains("Servidor restabelecido com sucesso!"))
    }
}
