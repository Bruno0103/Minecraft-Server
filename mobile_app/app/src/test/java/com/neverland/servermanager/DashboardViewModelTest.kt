package com.neverland.servermanager

import com.neverland.servermanager.domain.model.CommandResult
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.LifecycleAction
import com.neverland.servermanager.domain.model.ServerStatus
import com.neverland.servermanager.domain.model.SshConfig
import com.neverland.servermanager.domain.repository.IServerRepository
import com.neverland.servermanager.domain.repository.ISshManager
import com.neverland.servermanager.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ==============================================================================
 * Never Land Server Manager - Testes Unitários do DashboardViewModel (DashboardViewModelTest.kt)
 * ==============================================================================
 *
 * Suíte de testes da lógica de apresentação do Dashboard, validando a máquina
 * de estados reativa, controle de diálogos de segurança modal e despacho de comandos.
 * ==============================================================================
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSshManager: FakeSshManager
    private lateinit var fakeRepository: FakeServerRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSshManager = FakeSshManager()
        fakeRepository = FakeServerRepository()
        viewModel = DashboardViewModel(fakeSshManager, fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Valida o estado inicial padrão da ViewModel ao ser criada.
     */
    @Test
    fun testInitialUiStateIsDisconnectedAndEmpty() {
        val state = viewModel.uiState.value

        assertEquals(ConnectionState.Disconnected, state.connectionState)
        assertFalse(state.isLoading)
        assertNull(state.serverStatus)
        assertTrue(state.isAutoRefreshEnabled)
        assertFalse(state.isActionInProgress)
        assertNull(state.pendingConfirmationAction)
        assertNull(state.snackbarMessage)
    }

    /**
     * Valida que a transição de estado da conexão SSH para Connected dispara refresh automático.
     */
    @Test
    fun testConnectionStateConnectedTriggersRefreshStatus() = runTest {
        val sampleStatus = ServerStatus(
            isActive = true,
            substate = "running",
            pid = 68773,
            memoryUsedMib = 528.5f,
            memoryAlertMib = 600f,
            memoryMaxMib = 750f,
            uptimeText = "Mon 2026-11-09 22:44:39 GMT",
            isFifoAvailable = true,
            onlinePlayersCount = 1,
            maxPlayersCount = 10
        )
        fakeRepository.statusResult = Result.success(sampleStatus)

        // Conecta o SSH
        fakeSshManager.setConnected(true)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("Estado de conexão deve ser Connected", state.connectionState is ConnectionState.Connected)
        assertEquals(sampleStatus, state.serverStatus)
        assertFalse(state.isLoading)
        assertNotNull(state.lastRefreshTimestamp)
    }

    /**
     * Valida a alternância da preferência de auto-refresh.
     */
    @Test
    fun testToggleAutoRefresh() {
        assertTrue(viewModel.uiState.value.isAutoRefreshEnabled)

        viewModel.toggleAutoRefresh()
        assertFalse(viewModel.uiState.value.isAutoRefreshEnabled)

        viewModel.toggleAutoRefresh()
        assertTrue(viewModel.uiState.value.isAutoRefreshEnabled)
    }

    /**
     * Valida o fluxo de ação com confirmação prévia (STOP).
     * 1. Solicitação define pendingConfirmationAction.
     * 2. Cancelamento limpa pendingConfirmationAction sem chamar repositório.
     * 3. Confirmação dispara a execução no repositório.
     */
    @Test
    fun testStopActionRequiresAndHandlesConfirmationFlow() = runTest {
        fakeSshManager.setConnected(true)
        testScheduler.runCurrent()

        // 1. Solicita Stop
        viewModel.requestLifecycleAction(LifecycleAction.STOP)
        assertEquals(LifecycleAction.STOP, viewModel.uiState.value.pendingConfirmationAction)
        assertEquals(0, fakeRepository.stopServerCallCount)

        // 2. Cancela Stop
        viewModel.dismissConfirmationDialog()
        assertNull(viewModel.uiState.value.pendingConfirmationAction)
        assertEquals(0, fakeRepository.stopServerCallCount)

        // 3. Solicita novamente e confirma
        viewModel.requestLifecycleAction(LifecycleAction.STOP)
        assertEquals(LifecycleAction.STOP, viewModel.uiState.value.pendingConfirmationAction)

        viewModel.confirmLifecycleAction()
        testScheduler.advanceTimeBy(3000L)
        testScheduler.runCurrent()

        assertNull(viewModel.uiState.value.pendingConfirmationAction)
        assertEquals(1, fakeRepository.stopServerCallCount)
        assertTrue(
            "Deve apresentar snackbar de sucesso",
            viewModel.uiState.value.snackbarMessage?.contains("concluído com sucesso") == true
        )
    }

    /**
     * Valida o fluxo de ação sem confirmação (START) executando imediatamente.
     */
    @Test
    fun testStartActionExecutesImmediatelyWithoutDialog() = runTest {
        fakeSshManager.setConnected(true)
        testScheduler.runCurrent()

        viewModel.requestLifecycleAction(LifecycleAction.START)
        testScheduler.advanceTimeBy(3000L)
        testScheduler.runCurrent()

        assertNull(viewModel.uiState.value.pendingConfirmationAction)
        assertEquals(1, fakeRepository.startServerCallCount)
        assertTrue(
            viewModel.uiState.value.snackbarMessage?.contains("concluído com sucesso") == true
        )
    }

    /**
     * Valida tratamento de falha na execução de comando de ciclo de vida.
     */
    @Test
    fun testLifecycleActionFailureSetsErrorMessageInSnackbar() = runTest {
        fakeSshManager.setConnected(true)
        fakeRepository.actionResult = Result.failure(RuntimeException("Falha de timeout no SSH"))
        testScheduler.runCurrent()

        viewModel.requestLifecycleAction(LifecycleAction.START)
        testScheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isActionInProgress)
        val msg = viewModel.uiState.value.snackbarMessage
        assertNotNull(msg)
        assertTrue(msg?.contains("Falha ao") == true)
        assertTrue(msg?.contains("Falha de timeout") == true)
    }

    /**
     * Valida que clearSnackbar limpa a mensagem temporária.
     */
    @Test
    fun testClearSnackbar() = runTest {
        fakeSshManager.setConnected(true)
        fakeRepository.actionResult = Result.failure(RuntimeException("Falha de teste"))
        testScheduler.runCurrent()

        viewModel.requestLifecycleAction(LifecycleAction.START)
        testScheduler.runCurrent()

        assertNotNull(viewModel.uiState.value.snackbarMessage)

        viewModel.clearSnackbar()
        assertNull(viewModel.uiState.value.snackbarMessage)
    }

    // ==========================================================================
    // Fakes em memória para isolamento de testes
    // ==========================================================================

    private class FakeSshManager : ISshManager {
        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _activeConfig = MutableStateFlow<SshConfig?>(null)
        override val activeConfig: StateFlow<SshConfig?> = _activeConfig.asStateFlow()

        fun setConnected(connected: Boolean) {
            _connectionState.value = if (connected) {
                ConnectionState.Connected(host = "157.151.2.91", port = 22, latencyMs = 30L)
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
            return Result.success(CommandResult(0, ""))
        }

        override suspend fun testConnection(config: SshConfig): Result<CommandResult> {
            return Result.success(CommandResult(0, "NEVERLAND_OK"))
        }
    }

    private class FakeServerRepository : IServerRepository {
        var statusResult: Result<ServerStatus> = Result.success(ServerStatus.offline())
        var actionResult: Result<CommandResult> = Result.success(CommandResult(0, "OK"))

        var startServerCallCount = 0
        var stopServerCallCount = 0
        var restartServerCallCount = 0

        override suspend fun fetchStatus(): Result<ServerStatus> = statusResult

        override fun observeStatus(pollIntervalSeconds: Int): Flow<Result<ServerStatus>> = flowOf(statusResult)

        override suspend fun startServer(): Result<CommandResult> {
            startServerCallCount++
            return actionResult
        }

        override suspend fun stopServer(force: Boolean): Result<CommandResult> {
            stopServerCallCount++
            return actionResult
        }

        override suspend fun restartServer(): Result<CommandResult> {
            restartServerCallCount++
            return actionResult
        }
    }
}
