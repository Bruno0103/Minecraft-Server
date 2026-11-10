package com.neverland.servermanager.ui.dashboard

import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.LifecycleAction
import com.neverland.servermanager.domain.model.ServerStatus

/**
 * ==============================================================================
 * Never Land Server Manager - Estado de Apresentação da Dashboard (DashboardUiState.kt)
 * ==============================================================================
 *
 * Estrutura imutável de dados que encapsula todo o estado visual e operacional
 * da tela principal de Dashboard, permitindo a renderização reativa em Jetpack
 * Compose seguindo o padrão de arquitetura Unidirectional Data Flow (UDF).
 *
 * @property connectionState Estado atual da conexão SSH (Disconnected, Connecting, Connected, Error).
 * @property isLoading Sinaliza se uma operação de carregamento ou atualização manual de telemetria está em curso.
 * @property serverStatus Dados consolidados de telemetria do servidor Bedrock (null se ainda não consultado ou indisponível).
 * @property errorMessage Mensagem amigável de erro para exibição em banners de alerta na interface.
 * @property isActionInProgress Indica se uma ação de ciclo de vida (iniciar, parar ou reiniciar) está em execução.
 * @property actionProgressMessage Texto descritivo apresentado durante o progresso de uma ação (ex: "Parando servidor...").
 * @property isAutoRefreshEnabled Flag que indica se a rotina de polling periódico contínuo está ativada pelo operador.
 * @property lastRefreshTimestamp Timestamp Unix do momento da última atualização bem-sucedida de telemetria.
 * @property pendingConfirmationAction Ação de ciclo de vida que aguarda confirmação do operador no AlertDialog modal.
 * @property snackbarMessage Mensagem transitória de feedback para exibição no SnackbarHost.
 * ==============================================================================
 */
data class DashboardUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isLoading: Boolean = false,
    val serverStatus: ServerStatus? = null,
    val errorMessage: String? = null,
    val isActionInProgress: Boolean = false,
    val actionProgressMessage: String? = null,
    val isAutoRefreshEnabled: Boolean = true,
    val lastRefreshTimestamp: Long? = null,
    val pendingConfirmationAction: LifecycleAction? = null,
    val snackbarMessage: String? = null
) {
    /**
     * Informa se os dados de telemetria estão disponíveis e o serviço do servidor está ativo em execução.
     */
    val isServerRunning: Boolean
        get() = serverStatus?.isActive == true && serverStatus.substate.equals("running", ignoreCase = true)

    /**
     * Fração matemática normalizada do consumo de memória RAM física em relação ao limite (0.0f a 1.0f).
     */
    val memoryUsageFraction: Float
        get() {
            val used = serverStatus?.memoryUsedMib ?: return 0.0f
            val max = serverStatus.memoryMaxMib
            return if (max > 0.0f) (used / max).coerceIn(0.0f, 1.0f) else 0.0f
        }

    /**
     * Determina se as ações de ciclo de vida (Start/Stop/Restart) podem ser despachadas no momento.
     * Requer conexão SSH autenticada e ausência de outra operação em andamento.
     */
    val canExecuteActions: Boolean
        get() = connectionState is ConnectionState.Connected && !isActionInProgress
}
