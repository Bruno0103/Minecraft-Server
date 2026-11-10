package com.neverland.servermanager.ui.console

import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.ConsoleEntry
import com.neverland.servermanager.domain.model.ConsoleFilter
import com.neverland.servermanager.domain.model.ConsoleLineType

/**
 * ==============================================================================
 * Never Land Server Manager - Estado de UI do Console (ConsoleUiState.kt)
 * ==============================================================================
 *
 * Estrutura imutável que representa todo o estado visual e interativo da tela
 * do Console Interativo (REPL / Terminal BDS via socket FIFO `/run/bedrock.console`
 * e leitor `journalctl -u bedrock.service`).
 *
 * @property connectionState Estado atual da sessão SSH.
 * @property entries Lista completa de entradas de log e respostas de comandos.
 * @property selectedFilter Filtro semântico ativo (Todos, Chat & Players, Comandos, Alertas & Erros).
 * @property searchQuery Texto de busca instantânea para filtrar linhas específicas no log.
 * @property commandInput Texto atualmente digitado no campo de comando.
 * @property commandHistory Histórico dos últimos comandos enviados pelo operador na sessão.
 * @property isLoadingLogs Indica se uma consulta de sincronização de logs está em andamento.
 * @property isSendingCommand Indica se um comando está sendo transmitido ao FIFO do servidor.
 * @property isAutoScrollEnabled Controla se o terminal rola automaticamente para a última linha.
 * @property isLiveTailEnabled Controla se o polling automático de logs em tempo real está ativo.
 * @property isSearchVisible Controla se a barra de pesquisa textual de logs está expandida.
 * @property showBroadcastDialog Controla a exibição do modal dedicado de anúncio global (`say`).
 * @property broadcastInput Texto digitado no modal de anúncio global.
 * @property snackbarMessage Mensagem temporária de feedback para o usuário.
 * ==============================================================================
 */
data class ConsoleUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val entries: List<ConsoleEntry> = emptyList(),
    val selectedFilter: ConsoleFilter = ConsoleFilter.ALL,
    val searchQuery: String = "",
    val commandInput: String = "",
    val commandHistory: List<String> = listOf(
        "list",
        "allowlist list",
        "time query day",
        "save query"
    ),
    val isLoadingLogs: Boolean = false,
    val isSendingCommand: Boolean = false,
    val isAutoScrollEnabled: Boolean = true,
    val isLiveTailEnabled: Boolean = true,
    val isSearchVisible: Boolean = false,
    val showBroadcastDialog: Boolean = false,
    val broadcastInput: String = "",
    val snackbarMessage: String? = null
) {
    /**
     * Lista computada de entradas de console após aplicação do filtro de categoria
     * ([selectedFilter]) e do termo de pesquisa textual ([searchQuery]).
     */
    val filteredEntries: List<ConsoleEntry>
        get() {
            val byCategory = when (selectedFilter) {
                ConsoleFilter.ALL -> entries
                ConsoleFilter.CHAT_AND_PLAYERS -> entries.filter {
                    it.type == ConsoleLineType.CHAT || it.type == ConsoleLineType.PLAYER_EVENT
                }
                ConsoleFilter.COMMANDS -> entries.filter {
                    it.type == ConsoleLineType.COMMAND_SENT ||
                        it.type == ConsoleLineType.COMMAND_OUTPUT ||
                        it.type == ConsoleLineType.SYSTEM
                }
                ConsoleFilter.WARNINGS_AND_ERRORS -> entries.filter {
                    it.type == ConsoleLineType.WARN || it.type == ConsoleLineType.ERROR
                }
            }

            val query = searchQuery.trim()
            return if (query.isEmpty()) {
                byCategory
            } else {
                byCategory.filter {
                    it.content.contains(query, ignoreCase = true) ||
                        it.timestamp.contains(query, ignoreCase = true)
                }
            }
        }

    /**
     * Indica se a sessão SSH está ativa e pronta para receber comandos no terminal.
     */
    val isConnected: Boolean
        get() = connectionState is ConnectionState.Connected
}

