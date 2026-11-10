package com.neverland.servermanager.ui.players

import com.neverland.servermanager.domain.model.BackupFileEntry
import com.neverland.servermanager.domain.model.BackupResult
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.OnlinePlayer
import com.neverland.servermanager.domain.model.PlayerConnectionEvent
import com.neverland.servermanager.domain.model.WhitelistEntry

/**
 * ==============================================================================
 * Never Land Server Manager - Estado de UI de Jogadores & Backups (PlayersUiState.kt)
 * ==============================================================================
 *
 * Estrutura imutável que representa o estado completo da seção de Gerenciamento
 * de Jogadores (Online, Whitelist/Allowlist, Histórico de XUIDs) e da Central
 * de Hot-Backups Consistentes (`mc backup` / `mc backups`).
 * ==============================================================================
 */

/**
 * Sub-abas navegáveis dentro da tela de Jogadores e Backups.
 */
enum class PlayersSubTab(val title: String) {
    ONLINE("Online"),
    WHITELIST("Whitelist"),
    HISTORY("Histórico"),
    BACKUPS("Backups")
}

/**
 * Representa uma ação disciplinar pendente de confirmação modal (Kick ou Ban).
 *
 * @property gamertag Nome do jogador alvo da ação.
 * @property isBan Se verdadeiro, executa Banimento (Kick + Remoção da Allowlist); se falso, apenas Kick.
 * @property reason Motivo textual informado pelo administrador.
 */
data class PendingModerationAction(
    val gamertag: String,
    val isBan: Boolean,
    val reason: String = if (isBan) "Acesso revogado pelo administrador" else "Expulso pelo administrador"
)

/**
 * Estado consolidado da tela [PlayersScreen].
 */
data class PlayersUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedTab: PlayersSubTab = PlayersSubTab.ONLINE,
    val isLoading: Boolean = false,
    val isActionInProgress: Boolean = false,
    val actionProgressText: String? = null,
    val onlinePlayers: List<OnlinePlayer> = emptyList(),
    val whitelist: List<WhitelistEntry> = emptyList(),
    val playerHistory: List<PlayerConnectionEvent> = emptyList(),
    val searchQuery: String = "",
    // Modais de Whitelist e Moderação
    val showAddWhitelistDialog: Boolean = false,
    val newWhitelistGamertag: String = "",
    val pendingModeration: PendingModerationAction? = null,
    val selectedPlayerForQuickActions: String? = null,
    // Estado de Hot-Backup (Marco 5)
    val isBackupRunning: Boolean = false,
    val backupElapsedSeconds: Int = 0,
    val lastBackupResult: BackupResult? = null,
    val savedBackups: List<BackupFileEntry> = emptyList(),
    val snackbarMessage: String? = null
) {
    /**
     * Filtra a lista da Whitelist pelo termo de pesquisa digitado.
     */
    val filteredWhitelist: List<WhitelistEntry>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return whitelist
            return whitelist.filter {
                it.name.contains(q, ignoreCase = true) ||
                    (it.xuid?.contains(q, ignoreCase = true) == true)
            }
        }

    /**
     * Filtra o histórico de conexões pelo termo de pesquisa digitado.
     */
    val filteredHistory: List<PlayerConnectionEvent>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return playerHistory
            return playerHistory.filter {
                it.gamertag.contains(q, ignoreCase = true) ||
                    (it.xuid?.contains(q, ignoreCase = true) == true)
            }
        }

    /**
     * Indica se a sessão SSH está conectada.
     */
    val isConnected: Boolean
        get() = connectionState is ConnectionState.Connected
}

