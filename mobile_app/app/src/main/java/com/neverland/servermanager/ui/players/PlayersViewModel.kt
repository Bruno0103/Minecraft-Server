package com.neverland.servermanager.ui.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.GameModeOption
import com.neverland.servermanager.domain.repository.IServerRepository
import com.neverland.servermanager.domain.repository.ISshManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ==============================================================================
 * Never Land Server Manager - ViewModel de Jogadores & Backups (PlayersViewModel.kt)
 * ==============================================================================
 *
 * Gerencia todas as operações do Marco 5:
 * 1. Sincronização de jogadores online (`mc list`), lista de permissões (`allowlist.json`)
 *    e histórico de sessões com XUID (`mc jogadores`).
 * 2. Adição, remoção e recarregamento em tempo real da Whitelist (`allowlist add/remove/reload`).
 * 3. Moderação administrativa: Expulsão (`mc kick`) e Banimento (`mc kick` + remoção da Whitelist).
 * 4. Comandos rápidos in-game por jogador: mudança de Gamemode e concessão/revogação de OP.
 * 5. Execução de Hot-Backup consistente (`mc backup`) com cronômetro em tempo real
 *    e listagem dos arquivos `.tar.gz` salvos em `/home/opc/minecraft/backups`.
 * ==============================================================================
 */
class PlayersViewModel(
    private val sshManager: ISshManager,
    private val serverRepository: IServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayersUiState())
    val uiState: StateFlow<PlayersUiState> = _uiState.asStateFlow()

    /** Job que contabiliza os segundos decorridos enquanto o Hot-Backup está rodando */
    private var backupTimerJob: Job? = null

    init {
        observeConnection()
    }

    /**
     * Observa o estado da conexão SSH e carrega automaticamente os dados ao conectar.
     */
    private fun observeConnection() {
        viewModelScope.launch {
            sshManager.connectionState.collect { connState ->
                _uiState.update { it.copy(connectionState = connState) }
                if (connState is ConnectionState.Connected) {
                    refreshAllData()
                }
            }
        }
    }

    /**
     * Seleciona a sub-aba ativa (Online, Whitelist, Histórico, Backups).
     */
    fun selectTab(tab: PlayersSubTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Atualiza o filtro de busca textual.
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Sincroniza dados de jogadores online, whitelist, histórico e lista de backups.
     */
    fun refreshAllData() {
        if (!sshManager.isConnected()) {
            _uiState.update {
                it.copy(snackbarMessage = "Sessão SSH desconectada. Conecte-se na aba Ajustes.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val onlineDef = async { serverRepository.fetchOnlinePlayers() }
            val whitelistDef = async { serverRepository.fetchWhitelist() }
            val historyDef = async { serverRepository.fetchPlayerHistory() }
            val backupsDef = async { serverRepository.fetchBackupsList() }

            val onlineRes = onlineDef.await()
            val whitelistRes = whitelistDef.await()
            val historyRes = historyDef.await()
            val backupsRes = backupsDef.await()

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    onlinePlayers = onlineRes.getOrDefault(current.onlinePlayers),
                    whitelist = whitelistRes.getOrDefault(current.whitelist),
                    playerHistory = historyRes.getOrDefault(current.playerHistory),
                    savedBackups = backupsRes.getOrDefault(current.savedBackups)
                )
            }
        }
    }

    // ==========================================================================
    // Gestão de Whitelist (Allowlist)
    // ==========================================================================

    fun openAddWhitelistDialog(prefillGamertag: String = "") {
        _uiState.update {
            it.copy(
                showAddWhitelistDialog = true,
                newWhitelistGamertag = prefillGamertag
            )
        }
    }

    fun dismissAddWhitelistDialog() {
        _uiState.update {
            it.copy(
                showAddWhitelistDialog = false,
                newWhitelistGamertag = ""
            )
        }
    }

    fun updateNewWhitelistGamertag(gamertag: String) {
        _uiState.update { it.copy(newWhitelistGamertag = gamertag) }
    }

    /**
     * Adiciona a Gamertag informada à Allowlist e recarrega a lista no servidor.
     */
    fun confirmAddToWhitelist(explicitGamertag: String? = null) {
        val tag = (explicitGamertag ?: _uiState.value.newWhitelistGamertag).trim()
        if (tag.isEmpty()) return

        _uiState.update {
            it.copy(
                showAddWhitelistDialog = false,
                newWhitelistGamertag = "",
                isActionInProgress = true,
                actionProgressText = "Adicionando '$tag' à Whitelist..."
            )
        }

        viewModelScope.launch {
            val result = serverRepository.addToWhitelist(tag)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressText = null,
                            snackbarMessage = "Jogador '$tag' adicionado à Whitelist com sucesso!"
                        )
                    }
                    refreshAllData()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressText = null,
                            snackbarMessage = "Erro ao adicionar à Whitelist: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Remove um jogador da Allowlist do servidor.
     */
    fun removeFromWhitelist(gamertag: String) {
        val tag = gamertag.trim()
        if (tag.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isActionInProgress = true,
                    actionProgressText = "Removendo '$tag' da Whitelist..."
                )
            }

            val result = serverRepository.removeFromWhitelist(tag)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressText = null,
                            snackbarMessage = "'$tag' removido da Whitelist."
                        )
                    }
                    refreshAllData()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressText = null,
                            snackbarMessage = "Erro ao remover da Whitelist: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Recarrega o arquivo `allowlist.json` na engine do BDS (`allowlist reload`).
     */
    fun reloadWhitelist() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isActionInProgress = true,
                    actionProgressText = "Recarregando allowlist.json no BDS..."
                )
            }

            val result = serverRepository.reloadWhitelist()
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressText = null,
                            snackbarMessage = "Allowlist recarregada com sucesso no servidor!"
                        )
                    }
                    refreshAllData()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressText = null,
                            snackbarMessage = "Falha ao recarregar Allowlist: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    // ==========================================================================
    // Moderação de Jogadores (Kick & Ban) e Ações Rápidas In-Game
    // ==========================================================================

    fun requestKickPlayer(gamertag: String) {
        _uiState.update {
            it.copy(
                pendingModeration = PendingModerationAction(
                    gamertag = gamertag,
                    isBan = false,
                    reason = "Expulso pelo administrador"
                )
            )
        }
    }

    fun requestBanPlayer(gamertag: String) {
        _uiState.update {
            it.copy(
                pendingModeration = PendingModerationAction(
                    gamertag = gamertag,
                    isBan = true,
                    reason = "Banido do servidor Never Land"
                )
            )
        }
    }

    fun updateModerationReason(reason: String) {
        _uiState.update { current ->
            current.copy(
                pendingModeration = current.pendingModeration?.copy(reason = reason)
            )
        }
    }

    fun dismissModerationDialog() {
        _uiState.update { it.copy(pendingModeration = null) }
    }

    /**
     * Confirma e executa a expulsão (Kick) ou banimento (Ban = Kick + Allowlist Remove).
     */
    fun confirmModerationAction() {
        val action = _uiState.value.pendingModeration ?: return
        _uiState.update {
            it.copy(
                pendingModeration = null,
                isActionInProgress = true,
                actionProgressText = if (action.isBan) {
                    "Banindo e revogando acesso de '${action.gamertag}'..."
                } else {
                    "Expulsando '${action.gamertag}'..."
                }
            )
        }

        viewModelScope.launch {
            val result = if (action.isBan) {
                serverRepository.banPlayer(action.gamertag, action.reason)
            } else {
                serverRepository.kickPlayer(action.gamertag, action.reason)
            }

            result.fold(
                onSuccess = {
                    val msg = if (action.isBan) {
                        "Jogador '${action.gamertag}' banido e removido da Whitelist!"
                    } else {
                        "Jogador '${action.gamertag}' expulso do servidor."
                    }
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressText = null,
                            snackbarMessage = msg
                        )
                    }
                    refreshAllData()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionInProgress = false,
                            actionProgressText = null,
                            snackbarMessage = "Falha na ação de moderação: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun openQuickActionsForPlayer(gamertag: String) {
        _uiState.update { it.copy(selectedPlayerForQuickActions = gamertag) }
    }

    fun dismissQuickActionsDialog() {
        _uiState.update { it.copy(selectedPlayerForQuickActions = null) }
    }

    fun changePlayerGameMode(gamertag: String, mode: GameModeOption) {
        _uiState.update {
            it.copy(
                selectedPlayerForQuickActions = null,
                isActionInProgress = true,
                actionProgressText = "Alterando modo de '$gamertag' para ${mode.displayName}..."
            )
        }

        viewModelScope.launch {
            val res = serverRepository.setPlayerGameMode(gamertag, mode.commandValue)
            _uiState.update {
                it.copy(
                    isActionInProgress = false,
                    actionProgressText = null,
                    snackbarMessage = if (res.isSuccess) {
                        "Modo de jogo de '$gamertag' alterado para ${mode.displayName}!"
                    } else {
                        "Falha ao alterar modo de jogo: ${res.exceptionOrNull()?.message}"
                    }
                )
            }
        }
    }

    fun changePlayerOperator(gamertag: String, grantOp: Boolean) {
        _uiState.update {
            it.copy(
                selectedPlayerForQuickActions = null,
                isActionInProgress = true,
                actionProgressText = if (grantOp) "Concedendo OP para '$gamertag'..." else "Revogando OP de '$gamertag'..."
            )
        }

        viewModelScope.launch {
            val res = serverRepository.setPlayerOperator(gamertag, grantOp)
            _uiState.update {
                it.copy(
                    isActionInProgress = false,
                    actionProgressText = null,
                    snackbarMessage = if (res.isSuccess) {
                        if (grantOp) "Permissão de Operador (OP) concedida a '$gamertag'!" else "Permissão de Operador revogada de '$gamertag'."
                    } else {
                        "Falha ao alterar permissão OP: ${res.exceptionOrNull()?.message}"
                    }
                )
            }
        }
    }

    // ==========================================================================
    // Rotina de Hot-Backup Consistente (`mc backup` / `mc backups`)
    // ==========================================================================

    /**
     * Dispara a rotina de Hot-Backup atômico do mundo Never Land com cronômetro ao vivo.
     */
    fun triggerHotBackup() {
        if (_uiState.value.isBackupRunning) return
        if (!sshManager.isConnected()) {
            _uiState.update {
                it.copy(snackbarMessage = "Conecte-se via SSH antes de iniciar o Hot-Backup.")
            }
            return
        }

        _uiState.update {
            it.copy(
                isBackupRunning = true,
                backupElapsedSeconds = 0,
                selectedTab = PlayersSubTab.BACKUPS
            )
        }

        // Inicia cronômetro de segundos decorridos
        backupTimerJob?.cancel()
        backupTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                _uiState.update { it.copy(backupElapsedSeconds = it.backupElapsedSeconds + 1) }
            }
        }

        viewModelScope.launch {
            val result = serverRepository.triggerHotBackup()
            backupTimerJob?.cancel()
            backupTimerJob = null

            result.fold(
                onSuccess = { backupRes ->
                    _uiState.update {
                        it.copy(
                            isBackupRunning = false,
                            lastBackupResult = backupRes,
                            snackbarMessage = backupRes.message
                        )
                    }
                    // Atualiza lista de arquivos .tar.gz no disco
                    val updatedList = serverRepository.fetchBackupsList().getOrDefault(_uiState.value.savedBackups)
                    _uiState.update { it.copy(savedBackups = updatedList) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isBackupRunning = false,
                            snackbarMessage = "Erro durante Hot-Backup: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        backupTimerJob?.cancel()
    }

    /**
     * Factory para instanciação da [PlayersViewModel].
     */
    class Factory(
        private val sshManager: ISshManager,
        private val serverRepository: IServerRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlayersViewModel::class.java)) {
                return PlayersViewModel(sshManager, serverRepository) as T
            }
            throw IllegalArgumentException("Classe ViewModel desconhecida: ${modelClass.name}")
        }
    }
}

