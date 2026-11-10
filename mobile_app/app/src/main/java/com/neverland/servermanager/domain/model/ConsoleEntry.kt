package com.neverland.servermanager.domain.model

/**
 * ==============================================================================
 * Never Land Server Manager - Modelos do Console Interativo (ConsoleEntry.kt)
 * ==============================================================================
 *
 * Estruturas imutáveis que modelam cada linha do terminal interativo do Bedrock
 * Dedicated Server (`mc.py` / `journalctl -u bedrock.service` / `/run/bedrock.console`).
 *
 * Permite aplicar coloração sintática (Syntax Highlighting), filtragem por
 * categoria (Chat, Jogadores, Erros, Comandos) e busca textual instantânea.
 * ==============================================================================
 */

/**
 * Categorias semânticas de linhas do console para estilização visual e filtros.
 */
enum class ConsoleLineType(val badgeLabel: String) {
    /** Mensagem informativa padrão da engine Bedrock (`[INFO]`) */
    INFO("INFO"),

    /** Alerta operacional ou aviso não-fatal (`[WARN]` / `WARNING`) */
    WARN("WARN"),

    /** Erro de execução, falha de pacote ou exceção (`[ERROR]` / `ERRO`) */
    ERROR("ERRO"),

    /** Mensagem de chat global ou broadcast (`[CHAT]` / `<Player>` / `[Server]`) */
    CHAT("CHAT"),

    /** Evento de conexão, desconexão, XUID ou spawn de jogador */
    PLAYER_EVENT("PLAYER"),

    /** Comando digitado e disparado pelo administrador no aplicativo (`>> cmd`) */
    COMMAND_SENT("CMD >>"),

    /** Resposta direta retornada pela execução de um comando do administrador */
    COMMAND_OUTPUT("RESPOSTA"),

    /** Mensagem interna de sistema do próprio aplicativo Never Land Control */
    SYSTEM("SISTEMA")
}

/**
 * Filtro selecionável na barra superior do Console Interativo.
 */
enum class ConsoleFilter(val label: String) {
    ALL("Todos"),
    CHAT_AND_PLAYERS("Chat & Players"),
    COMMANDS("Comandos"),
    WARNINGS_AND_ERRORS("Alertas & Erros")
}

/**
 * Representa um item individual de linha exibido no terminal do Console Interativo.
 *
 * @property id Identificador sequencial único para estabilidade de chaves na `LazyColumn`.
 * @property timestamp Horário extraído do log (ex.: `"20:31:00"`) ou horário local do envio.
 * @property content Conteúdo textual limpo de sequências ANSI.
 * @property type Classificação semântica ([ConsoleLineType]) que governa a cor do texto e badge.
 */
data class ConsoleEntry(
    val id: Long,
    val timestamp: String,
    val content: String,
    val type: ConsoleLineType
)

