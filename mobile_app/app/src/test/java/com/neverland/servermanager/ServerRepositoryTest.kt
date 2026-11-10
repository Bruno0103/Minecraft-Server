package com.neverland.servermanager

import com.neverland.servermanager.data.repository.ServerRepositoryImpl
import com.neverland.servermanager.domain.model.CommandResult
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.ServerHealth
import com.neverland.servermanager.domain.model.SshConfig
import com.neverland.servermanager.domain.repository.ISshManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ==============================================================================
 * Never Land Server Manager - Testes Unitários do ServerRepository (ServerRepositoryTest.kt)
 * ==============================================================================
 *
 * Valida o comportamento do [ServerRepositoryImpl] em relação à execução de comandos,
 * aplicação de fallbacks em cascata, respeito a timeouts e polling com Flow de Coroutines.
 * ==============================================================================
 */
class ServerRepositoryTest {

    private lateinit var fakeSshManager: FakeSshManager
    private lateinit var repository: ServerRepositoryImpl

    @Before
    fun setUp() {
        fakeSshManager = FakeSshManager()
        repository = ServerRepositoryImpl(fakeSshManager)
    }

    /**
     * Valida que fetchStatus falha imediatamente caso a sessão SSH não esteja conectada.
     */
    @Test
    fun testFetchStatusFailsWhenSshDisconnected() = runBlocking {
        fakeSshManager.setConnected(false)

        val result = repository.fetchStatus()

        assertTrue("Deve retornar falha quando desconectado", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    /**
     * Valida consulta com sucesso no canal primário canônico 'mc status'.
     */
    @Test
    fun testFetchStatusSuccessViaCanonicalMcStatus() = runBlocking {
        fakeSshManager.setConnected(true)
        fakeSshManager.commandHandler = { cmd, timeout ->
            assertEquals("mc status", cmd)
            assertEquals(15, timeout)
            CommandResult(
                exitCode = 0,
                output = """
                    === STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ===
                      • Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)
                      • PID do Processo:   68773
                      • Data de Início:    Mon 2026-11-09 22:44:39 GMT
                      • Contagem Restarts: 0
                      • Memória RAM (BDS): 528.5 MiB (Alerta: 600 MiB, Máx: 750 MiB)
                      • Canal Console:     /run/bedrock.console (FIFO operacional.)
                    --- Jogadores Conectados ---
                      There are 0/10 players online:
                """.trimIndent()
            )
        }

        val result = repository.fetchStatus()

        assertTrue("Deve ser sucesso", result.isSuccess)
        val status = result.getOrThrow()
        assertTrue(status.isActive)
        assertEquals(68773, status.pid)
        assertEquals(528.5f, status.memoryUsedMib ?: 0f, 0.01f)
        assertEquals(ServerHealth.HEALTHY, status.health)
    }

    /**
     * Valida fallback para 'systemctl show' quando 'mc status' não existe ou falha.
     */
    @Test
    fun testFetchStatusFallbackToSystemctlShow() = runBlocking {
        fakeSshManager.setConnected(true)
        fakeSshManager.commandHandler = { cmd, _ ->
            when {
                cmd == "mc status" -> CommandResult(exitCode = 127, output = "mc: command not found")
                cmd.startsWith("systemctl show bedrock.service") -> CommandResult(
                    exitCode = 0,
                    output = """
                        ActiveState=active
                        SubState=running
                        MainPID=68773
                        ExecMainStartTimestamp=Mon 2026-11-09 22:44:39 GMT
                        MemoryCurrent=554172416
                        NRestarts=0
                    """.trimIndent()
                )
                else -> CommandResult(exitCode = 1, output = "Unknown")
            }
        }

        val result = repository.fetchStatus()

        assertTrue("Fallback de systemctl show deve ter sucesso", result.isSuccess)
        val status = result.getOrThrow()
        assertTrue(status.isActive)
        assertEquals(68773, status.pid)
        assertEquals(528.5f, status.memoryUsedMib ?: 0f, 0.1f)
    }

    /**
     * Valida fallback de último nível para 'systemctl is-active' quando os anteriores falharem.
     */
    @Test
    fun testFetchStatusFallbackToSystemctlIsActive() = runBlocking {
        fakeSshManager.setConnected(true)
        fakeSshManager.commandHandler = { cmd, _ ->
            when {
                cmd == "mc status" -> CommandResult(exitCode = 1, output = "Error")
                cmd.startsWith("systemctl show") -> CommandResult(exitCode = 1, output = "Error")
                cmd == "systemctl is-active bedrock.service" -> CommandResult(exitCode = 0, output = "active\n")
                else -> CommandResult(exitCode = 1, output = "")
            }
        }

        val result = repository.fetchStatus()

        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertTrue(status.isActive)
        assertEquals("running", status.substate)
    }

    /**
     * Valida observeStatus emitindo dados e respeitando cancelamento de Flow.
     */
    @Test
    fun testObserveStatusEmitsAndRespectsCancellation() = runBlocking {
        fakeSshManager.setConnected(true)
        fakeSshManager.commandHandler = { _, _ ->
            CommandResult(
                exitCode = 0,
                output = "• Serviço Systemd:   [ATIVO / OPERACIONAL] (Substate: running)"
            )
        }

        // Intervalo de 1 segundo para teste rápido
        val flow = repository.observeStatus(pollIntervalSeconds = 1)
        val emissions = flow.take(2).toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0].isSuccess)
        assertTrue(emissions[1].isSuccess)
    }

    /**
     * Valida disparo do comando de inicialização (Start).
     */
    @Test
    fun testStartServerExecutesMcStartWithThirtySecondsTimeout() = runBlocking {
        fakeSshManager.setConnected(true)
        var executedCommand: String? = null
        var executedTimeout: Int? = null

        fakeSshManager.commandHandler = { cmd, timeout ->
            executedCommand = cmd
            executedTimeout = timeout
            CommandResult(exitCode = 0, output = "Servidor inicializado com sucesso.")
        }

        val result = repository.startServer()

        assertTrue(result.isSuccess)
        assertEquals("mc start", executedCommand)
        assertEquals(30, executedTimeout)
    }

    /**
     * Valida fallback do comando Start para 'sudo systemctl start bedrock.service'.
     */
    @Test
    fun testStartServerFallbackToSystemctl() = runBlocking {
        fakeSshManager.setConnected(true)
        val executedCommands = mutableListOf<String>()

        fakeSshManager.commandHandler = { cmd, _ ->
            executedCommands.add(cmd)
            if (cmd == "mc start") {
                CommandResult(exitCode = 127, output = "mc: not found")
            } else {
                CommandResult(exitCode = 0, output = "")
            }
        }

        val result = repository.startServer()

        assertTrue(result.isSuccess)
        assertTrue(executedCommands.contains("mc start"))
        assertTrue(executedCommands.contains("sudo systemctl start bedrock.service"))
    }

    /**
     * Valida comando de parada (Stop) disparando com flag -f e timeout longo de 65 segundos.
     */
    @Test
    fun testStopServerExecutesWithForceFlagAndSixtyFiveSecondsTimeout() = runBlocking {
        fakeSshManager.setConnected(true)
        var executedCommand: String? = null
        var executedTimeout: Int? = null

        fakeSshManager.commandHandler = { cmd, timeout ->
            executedCommand = cmd
            executedTimeout = timeout
            CommandResult(exitCode = 0, output = "Servidor parado com sucesso.")
        }

        val result = repository.stopServer(force = true)

        assertTrue(result.isSuccess)
        assertEquals("mc stop -f", executedCommand)
        assertEquals(65, executedTimeout)
    }

    /**
     * Valida comando de reinício (Restart) disparando com flag --now e timeout de 75 segundos.
     */
    @Test
    fun testRestartServerExecutesWithNowFlagAndSeventyFiveSecondsTimeout() = runBlocking {
        fakeSshManager.setConnected(true)
        var executedCommand: String? = null
        var executedTimeout: Int? = null

        fakeSshManager.commandHandler = { cmd, timeout ->
            executedCommand = cmd
            executedTimeout = timeout
            CommandResult(exitCode = 0, output = "Servidor reiniciado com sucesso.")
        }

        val result = repository.restartServer()

        assertTrue(result.isSuccess)
        assertEquals("mc restart --now", executedCommand)
        assertEquals(75, executedTimeout)
    }

    /**
     * Implementação simulada em memória de [ISshManager] para testes unitários isolados.
     */
    private class FakeSshManager : ISshManager {
        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _activeConfig = MutableStateFlow<SshConfig?>(null)
        override val activeConfig: StateFlow<SshConfig?> = _activeConfig.asStateFlow()

        var commandHandler: (command: String, timeout: Int) -> CommandResult = { _, _ ->
            CommandResult(0, "")
        }

        fun setConnected(connected: Boolean) {
            _connectionState.value = if (connected) {
                ConnectionState.Connected(host = "157.151.2.91", port = 22, latencyMs = 45L)
            } else {
                ConnectionState.Disconnected
            }
        }

        override fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

        override suspend fun connect(config: SshConfig): Result<Unit> {
            setConnected(true)
            return Result.success(Unit)
        }

        override suspend fun disconnect() {
            setConnected(false)
        }

        override suspend fun executeCommand(command: String, timeoutSeconds: Int): Result<CommandResult> {
            return if (isConnected()) {
                Result.success(commandHandler(command, timeoutSeconds))
            } else {
                Result.failure(IllegalStateException("Desconectado"))
            }
        }

        override suspend fun testConnection(config: SshConfig): Result<CommandResult> {
            return Result.success(CommandResult(0, "NEVERLAND_OK"))
        }
    }
}
