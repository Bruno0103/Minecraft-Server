package com.neverland.servermanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.LifecycleAction
import com.neverland.servermanager.domain.repository.IServerRepository
import com.neverland.servermanager.domain.repository.ISshManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Never Land Server Manager - ViewModel da Dashboard (DashboardViewModel.kt)
 * ==============================================================================
 *
 * Gerencia o estado de apresentação, polling reativo e despacho de comandos
 * de ciclo de vida do servidor BDS Never Land.
 *
 * Princípios de Arquitetura:
 * 1. Unidirectional Data Flow (UDF): Todo o estado é encapsulado em [_uiState] e exposto
 *    como [StateFlow] imutável para a camada de apresentação Jetpack Compose.
 * 2. Reatividade First-Class: Observa o fluxo de estado da sessão SSH ([ISshManager.connectionState]),
 *    iniciando o monitoramento contínuo assim que a conexão é autenticada e suspendendo-o
 *    imediatamente em quedas ou desconexões intencionais.
 * 3. Prevenção de Falhas Operacionais: Intercepta ações críticas de parada e reinício,
 *    garantindo que exijam confirmação modal antes do envio de comandos à VM remota.
 * 4. Silêncio Operacional no Polling: Atualizações periódicas automáticas de telemetria
 *    não ativam indicadores invasivos de tela cheia, garantindo fluidez e conforto visual.
 * ==============================================================================
 */
class DashboardViewModel(
    private val sshManager: ISshManager,
    private val serverRepository: IServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Job responsável pelo laço periódico de auto-refresh de telemetria */
    private var pollingJob: Job? = null

    init {
        // Inicia a observação reativa contínua do estado da conexão SSH
        observeConnectionState()
    }

    /**
     * Observa mudanças no estado de conexão de rede SSH.
     */
    private fun observeConnectionState() {
        viewModelScope.launch {
            sshManager.connectionState.collect { connState ->
                _uiState.update { current ->
                    current.copy(
                        connectionState = connState,
                        errorMessage = if (connState is ConnectionState.Error) connState.message else null
                    )
                }

                if (connState is ConnectionState.Connected) {
                    // Ao conectar, dispara atualização inicial e ativa polling se habilitado
                    refreshStatus(silent = false)
                    restartPollingIfEnabled()
                } else {
                    // Ao desconectar ou em erro, interrompe o polling imediatamente
                    stopPolling()
                }
            }
        }
    }

    /**
     * Atualiza a telemetria do servidor sob demanda.
     *
     * @param silent Se verdadeiro, não ativa o spinner de carregamento central (usado em polling).
     */
    fun refreshStatus(silent: Boolean = false) {
        if (!sshManager.isConnected()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = if (it.connectionState !is ConnectionState.Connected) {
                        "Não conectado ao servidor SSH."
                    } else null
                )
            }
            return
        }

        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            val result = serverRepository.fetchStatus()

            result.fold(
                onSuccess = { status ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            serverStatus = status,
                            errorMessage = null,
                            lastRefreshTimestamp = System.currentTimeMillis()
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Falha ao obter telemetria: ${error.message ?: "Erro desconhecido"}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Alterna a preferência de auto-refresh periódico de telemetria.
     */
    fun toggleAutoRefresh() {
        val newState = !_uiState.value.isAutoRefreshEnabled
        _uiState.update { it.copy(isAutoRefreshEnabled = newState) }

        if (newState) {
            restartPollingIfEnabled()
        } else {
            stopPolling()
        }
    }

    /**
     * Inicia ou reinicia o loop cooperativo de polling de telemetria (a cada 15 segundos).
     */
    private fun restartPollingIfEnabled() {
        stopPolling()
        if (!_uiState.value.isAutoRefreshEnabled || !sshManager.isConnected()) return

        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L) // Intervalo de 15 segundos para monitoramento fluido de CPU e RAM
                if (sshManager.isConnected() && !_uiState.value.isActionInProgress) {
                    refreshStatus(silent = true)
                }
            }
        }
    }

    /**
     * Cancela a execução do job de polling de telemetria.
     */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // ==========================================================================
    // Gestão de Ciclo de Vida e Diálogos de Segurança
    // ==========================================================================

    /**
     * Requisita o início de uma ação de ciclo de vida (Start, Stop, Restart).
     * Se a ação exigir confirmação prévia, abre o modal [AlertDialog].
     */
    fun requestLifecycleAction(action: LifecycleAction) {
        if (_uiState.value.isActionInProgress) return

        if (action.requiresConfirmation) {
            _uiState.update { it.copy(pendingConfirmationAction = action) }
        } else {
            executeAction(action)
        }
    }

    /**
     * Confirma e despacha a execução da ação que estava pendente no diálogo modal.
     */
    fun confirmLifecycleAction() {
        val action = _uiState.value.pendingConfirmationAction ?: return
        _uiState.update { it.copy(pendingConfirmationAction = null) }
        executeAction(action)
    }

    /**
     * Cancela e fecha o diálogo modal de confirmação de ação de ciclo de vida.
     */
    fun dismissConfirmationDialog() {
        _uiState.update { it.copy(pendingConfirmationAction = null) }
    }

    /**
     * Executa concretamente a ação de ciclo de vida solicitada no servidor.
     */
    private fun executeAction(action: LifecycleAction) {
        if (!sshManager.isConnected()) {
            _uiState.update {
                it.copy(snackbarMessage = "Operação cancelada: Nenhuma sessão SSH ativa.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isActionInProgress = true,
                    actionProgressMessage = "Executando: ${action.displayName}..."
                )
            }

            val result = when (action) {
                LifecycleAction.START -> serverRepository.startServer()
                LifecycleAction.STOP -> serverRepository.stopServer(force = true)
                LifecycleAction.RESTART -> serverRepository.restartServer()
            }

            result.fold(
                onSuccess = { cmdResult ->
                    val successMsg = if (cmdResult.isSuccess) {
                        "${action.displayName} concluído com sucesso!"
                    } else {
                        "Aviso: ${action.displayName} retornou código ${cmdResult.exitCode}."
                    }
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressMessage = null,
                            snackbarMessage = successMsg
                        )
                    }
                    // Aguarda breve estabilização do serviço e atualiza telemetria
                    delay(2000L)
                    refreshStatus(silent = true)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressMessage = null,
                            snackbarMessage = "Falha ao ${action.displayName.lowercase()}: ${error.message ?: "Erro desconhecido"}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Limpa a mensagem temporária do Snackbar após exibição na interface.
     */
    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Liberação de recursos ao descartar a ViewModel.
     */
    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    /**
     * Factory utilitária para instanciação manual da [DashboardViewModel]
     * sem dependência obrigatória de frameworks de injeção de dependência.
     */
    class Factory(
        private val sshManager: ISshManager,
        private val serverRepository: IServerRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(sshManager, serverRepository) as T
            }
            throw IllegalArgumentException("Classe ViewModel desconhecida: ${modelClass.name}")
        }
    }
}
