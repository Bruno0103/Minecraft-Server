package com.neverland.servermanager.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neverland.servermanager.data.parser.StatusParser
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.ConsoleEntry
import com.neverland.servermanager.domain.model.ConsoleFilter
import com.neverland.servermanager.domain.model.ConsoleLineType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * ==============================================================================
 * Never Land Server Manager - ViewModel do Console Interativo (ConsoleViewModel.kt)
 * ==============================================================================
 *
 * Coordena a operação em tempo real do terminal do Bedrock Dedicated Server:
 * 1. Sincroniza as últimas linhas do `journalctl -u bedrock.service` via `mc log`.
 * 2. Despacha comandos arbitrários para o named pipe `/run/bedrock.console` via `mc cmd`.
 * 3. Despacha mensagens de broadcast público no chat via `mc say` sem bloqueio.
 * 4. Mantém histórico navegável de comandos enviados e preserva as saídas de
 *    comandos executados localmente durante o ciclo de polling de logs.
 * ==============================================================================
 */
class ConsoleViewModel(
    private val sshManager: ISshManager,
    private val serverRepository: IServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    /** Gerador atômico de IDs para garantir chaves únicas no LazyColumn */
    private val idGenerator = AtomicLong(1000L)

    /** Job de polling periódico para o modo Live Tail */
    private var liveTailJob: Job? = null

    /** Buffer local de comandos e respostas disparados pelo usuário nesta sessão */
    private val sessionInteractiveEntries = mutableListOf<ConsoleEntry>()

    init {
        observeConnection()
    }

    /**
     * Observa mudanças na conexão SSH para carregar os logs automaticamente ao conectar.
     */
    private fun observeConnection() {
        viewModelScope.launch {
            sshManager.connectionState.collect { connState ->
                _uiState.update { it.copy(connectionState = connState) }

                if (connState is ConnectionState.Connected) {
                    refreshLogs(silent = false)
                    restartLiveTailIfEnabled()
                } else {
                    stopLiveTail()
                }
            }
        }
    }

    /**
     * Busca as últimas 80 linhas de log do servidor e combina com o histórico interativo local.
     */
    fun refreshLogs(silent: Boolean = false) {
        if (!sshManager.isConnected()) {
            if (!silent) {
                _uiState.update {
                    it.copy(snackbarMessage = "Conecte-se ao SSH na aba Ajustes para sincronizar o console.")
                }
            }
            return
        }

        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoadingLogs = true) }
            }

            val result = serverRepository.fetchConsoleLogs(lines = 80)
            result.fold(
                onSuccess = { remoteLogs ->
                    val merged = mergeLogsWithInteractive(remoteLogs)
                    _uiState.update {
                        it.copy(
                            isLoadingLogs = false,
                            entries = merged
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingLogs = false,
                            snackbarMessage = if (!silent) {
                                "Erro ao ler logs: ${error.message ?: "Falha desconhecida"}"
                            } else it.snackbarMessage
                        )
                    }
                }
            )
        }
    }

    /**
     * Combina os logs vindos do `journalctl` com as interações manuais do operador.
     */
    private fun mergeLogsWithInteractive(remoteLogs: List<ConsoleEntry>): List<ConsoleEntry> {
        val reindexedRemote = remoteLogs.mapIndexed { index, entry ->
            entry.copy(id = (index + 1).toLong())
        }
        return reindexedRemote + sessionInteractiveEntries
    }

    /**
     * Atualiza o texto digitado no campo de entrada do terminal.
     */
    fun updateCommandInput(text: String) {
        _uiState.update { it.copy(commandInput = text) }
    }

    /**
     * Envia o comando atualmente preenchido no campo de entrada.
     */
    fun sendCurrentCommand() {
        val rawCmd = _uiState.value.commandInput.trim()
        if (rawCmd.isEmpty()) return
        _uiState.update { it.copy(commandInput = "") }
        executeConsoleCommand(rawCmd)
    }

    /**
     * Envia imediatamente um comando rápido (atalho ou item do histórico).
     */
    fun sendQuickCommand(command: String) {
        val clean = command.trim()
        if (clean.isEmpty()) return
        executeConsoleCommand(clean)
    }

    /**
     * Executa o envio assíncrono do comando ao servidor via [IServerRepository.sendConsoleCommand]
     * e adiciona tanto o eco do comando (`>> cmd`) quanto a resposta do servidor ao terminal.
     */
    private fun executeConsoleCommand(command: String) {
        val nowStr = currentTimeFormatted()
        val sentEntry = ConsoleEntry(
            id = idGenerator.incrementAndGet(),
            timestamp = nowStr,
            content = ">> $command",
            type = ConsoleLineType.COMMAND_SENT
        )

        sessionInteractiveEntries.add(sentEntry)

        // Atualiza histórico de comandos (mantendo os 15 mais recentes sem duplicatas)
        val updatedHistory = (listOf(command) + _uiState.value.commandHistory)
            .distinctBy { it.lowercase() }
            .take(15)

        _uiState.update {
            it.copy(
                entries = it.entries + sentEntry,
                commandHistory = updatedHistory,
                isSendingCommand = true
            )
        }

        if (!sshManager.isConnected()) {
            val offlineEntry = ConsoleEntry(
                id = idGenerator.incrementAndGet(),
                timestamp = currentTimeFormatted(),
                content = "[SISTEMA] Sessão SSH desconectada. Configure e conecte na aba Ajustes.",
                type = ConsoleLineType.ERROR
            )
            sessionInteractiveEntries.add(offlineEntry)
            _uiState.update {
                it.copy(
                    entries = it.entries + offlineEntry,
                    isSendingCommand = false,
                    snackbarMessage = "Sessão SSH desconectada."
                )
            }
            return
        }

        viewModelScope.launch {
            val result = serverRepository.sendConsoleCommand(command)
            result.fold(
                onSuccess = { cmdResult ->
                    val cleanOut = StatusParser.stripAnsiEscapes(cmdResult.output).trim()
                    val responseLines = if (cleanOut.isNotEmpty()) {
                        cleanOut.lines()
                            .map { it.trimEnd() }
                            .filter { it.isNotEmpty() && !it.startsWith(">> ") }
                    } else {
                        listOf("(Comando '$command' enviado com sucesso ao FIFO)")
                    }

                    val newOutputEntries = responseLines.map { line ->
                        ConsoleEntry(
                            id = idGenerator.incrementAndGet(),
                            timestamp = currentTimeFormatted(),
                            content = line,
                            type = if (cmdResult.isSuccess) ConsoleLineType.COMMAND_OUTPUT else ConsoleLineType.ERROR
                        )
                    }

                    sessionInteractiveEntries.addAll(newOutputEntries)
                    _uiState.update {
                        it.copy(
                            entries = it.entries + newOutputEntries,
                            isSendingCommand = false
                        )
                    }
                },
                onFailure = { err ->
                    val errEntry = ConsoleEntry(
                        id = idGenerator.incrementAndGet(),
                        timestamp = currentTimeFormatted(),
                        content = "[ERRO] Falha ao executar '$command': ${err.message}",
                        type = ConsoleLineType.ERROR
                    )
                    sessionInteractiveEntries.add(errEntry)
                    _uiState.update {
                        it.copy(
                            entries = it.entries + errEntry,
                            isSendingCommand = false,
                            snackbarMessage = "Erro ao enviar comando: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    // ==========================================================================
    // Broadcast Público no Chat (`mc say`)
    // ==========================================================================

    /**
     * Abre ou fecha o diálogo modal de transmissão de mensagem global no chat.
     */
    fun setShowBroadcastDialog(show: Boolean) {
        _uiState.update { it.copy(showBroadcastDialog = show) }
    }

    /**
     * Atualiza o texto da mensagem de broadcast no modal.
     */
    fun updateBroadcastInput(text: String) {
        _uiState.update { it.copy(broadcastInput = text) }
    }

    /**
     * Envia a mensagem de broadcast (`mc say`) para todos os jogadores conectados.
     */
    fun confirmSendBroadcast() {
        val message = _uiState.value.broadcastInput.trim()
        if (message.isEmpty()) return

        _uiState.update {
            it.copy(
                showBroadcastDialog = false,
                broadcastInput = "",
                isSendingCommand = true
            )
        }

        viewModelScope.launch {
            val result = serverRepository.sendBroadcastMessage(message)
            result.fold(
                onSuccess = {
                    val chatEntry = ConsoleEntry(
                        id = idGenerator.incrementAndGet(),
                        timestamp = currentTimeFormatted(),
                        content = "[Broadcast Chat] [Server] $message",
                        type = ConsoleLineType.CHAT
                    )
                    sessionInteractiveEntries.add(chatEntry)
                    _uiState.update {
                        it.copy(
                            entries = it.entries + chatEntry,
                            isSendingCommand = false,
                            snackbarMessage = "Mensagem transmitida no chat do servidor!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSendingCommand = false,
                            snackbarMessage = "Falha ao enviar broadcast: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    // ==========================================================================
    // Controles de Visualização, Filtros e Live Tail
    // ==========================================================================

    fun selectFilter(filter: ConsoleFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun toggleSearchVisibility() {
        _uiState.update {
            val nextVisible = !it.isSearchVisible
            it.copy(
                isSearchVisible = nextVisible,
                searchQuery = if (!nextVisible) "" else it.searchQuery
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(isAutoScrollEnabled = !it.isAutoScrollEnabled) }
    }

    fun toggleLiveTail() {
        val nextState = !_uiState.value.isLiveTailEnabled
        _uiState.update {
            it.copy(
                isLiveTailEnabled = nextState,
                snackbarMessage = if (nextState) "Sincronização ao vivo ativada (a cada 8s)." else "Sincronização ao vivo pausada."
            )
        }
        if (nextState) {
            restartLiveTailIfEnabled()
        } else {
            stopLiveTail()
        }
    }

    /**
     * Limpa o buffer visual do console na sessão atual.
     */
    fun clearConsoleBuffer() {
        sessionInteractiveEntries.clear()
        val clearNotice = ConsoleEntry(
            id = idGenerator.incrementAndGet(),
            timestamp = currentTimeFormatted(),
            content = "Buffer visual limpo pelo operador. Toque em Atualizar para recarregar o journald.",
            type = ConsoleLineType.SYSTEM
        )
        sessionInteractiveEntries.add(clearNotice)
        _uiState.update { it.copy(entries = listOf(clearNotice)) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun restartLiveTailIfEnabled() {
        stopLiveTail()
        if (!_uiState.value.isLiveTailEnabled || !sshManager.isConnected()) return

        liveTailJob = viewModelScope.launch {
            while (isActive) {
                delay(8_000L)
                if (sshManager.isConnected() && !_uiState.value.isSendingCommand && !_uiState.value.isLoadingLogs) {
                    refreshLogs(silent = true)
                }
            }
        }
    }

    private fun stopLiveTail() {
        liveTailJob?.cancel()
        liveTailJob = null
    }

    private fun currentTimeFormatted(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveTail()
    }

    /**
     * Factory para criação da [ConsoleViewModel] com dependências injetadas.
     */
    class Factory(
        private val sshManager: ISshManager,
        private val serverRepository: IServerRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ConsoleViewModel::class.java)) {
                return ConsoleViewModel(sshManager, serverRepository) as T
            }
            throw IllegalArgumentException("Classe ViewModel desconhecida: ${modelClass.name}")
        }
    }
}

