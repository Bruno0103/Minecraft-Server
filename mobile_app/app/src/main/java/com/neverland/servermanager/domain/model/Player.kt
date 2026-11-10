package com.neverland.servermanager.domain.model

/**
 * ==============================================================================
 * Never Land Server Manager - Modelos de Domínio de Jogadores (Player.kt)
 * ==============================================================================
 *
 * Estruturas imutáveis de dados que representam os jogadores conectados em tempo
 * real, registros da lista de permissões (Allowlist/Whitelist) e eventos históricos
 * de conexão, spawn e desconexão com seus respectivos identificadores Xbox Live (XUID).
 *
 * Princípios de Design:
 * 1. Imutabilidade Estrita: Todas as propriedades são declaradas como `val`,
 *    garantindo segurança de leitura concorrente entre threads e recomposições
 *    do Jetpack Compose.
 * 2. Enriquecimento Semântico: Modelos expõem propriedades auxiliares para
 *    facilitar a renderização de avatares, iniciais e filtros de busca na UI.
 * ==============================================================================
 */

/**
 * Representa um jogador atualmente conectado e ativo no mundo do servidor Bedrock.
 *
 * @property gamertag Nome de usuário (Gamertag da Xbox Live) do jogador conectado.
 * @property xuid Identificador único de 16 dígitos da Xbox Live (opcional, enriquecido via histórico).
 * @property connectedSince Horário aproximado da última conexão registrada no log (opcional).
 */
data class OnlinePlayer(
    val gamertag: String,
    val xuid: String? = null,
    val connectedSince: String? = null
) {
    /**
     * Retorna as duas primeiras letras da Gamertag em maiúsculas para exibição
     * no avatar estilizado caso não haja imagem externa.
     */
    val avatarInitials: String
        get() = gamertag.trim().take(2).uppercase().ifEmpty { "NL" }
}

/**
 * Representa uma entrada cadastrada na lista de permissões oficial (`allowlist.json`)
 * do servidor Minecraft Bedrock.
 *
 * @property name Gamertag autorizada a ingressar no servidor.
 * @property xuid XUID associado à conta Microsoft/Xbox (preenchido automaticamente pelo BDS no primeiro login).
 * @property ignoresPlayerLimit Indica se o jogador pode entrar mesmo quando o servidor atingir a lotação máxima (10/10).
 */
data class WhitelistEntry(
    val name: String,
    val xuid: String? = null,
    val ignoresPlayerLimit: Boolean = false
) {
    /**
     * Iniciais para exibição visual em listas da Allowlist.
     */
    val avatarInitials: String
        get() = name.trim().take(2).uppercase().ifEmpty { "WL" }
}

/**
 * Categoriza o tipo de evento de sessão registrado pelo motor Bedrock no journald.
 */
enum class PlayerEventType(val label: String) {
    /** Jogador autenticou e entrou no servidor (`Player connected: <Gamertag>, xuid: <XUID>`) */
    CONNECTED("Conectou"),

    /** Jogador foi instanciado fisicamente no mundo (`Player Spawned: <Gamertag> xuid: <XUID>`) */
    SPAWNED("Entrou no Mundo"),

    /** Jogador encerrou a sessão ou caiu (`Player disconnected: <Gamertag>, xuid: <XUID>`) */
    DISCONNECTED("Desconectou")
}

/**
 * Representa um evento histórico de conexão, spawn ou desconexão de jogador
 * extraído dos logs do systemd journald através do comando `mc jogadores`.
 *
 * @property timestamp Data/hora extraída da linha de log (ex.: "2026-11-09 20:31:00").
 * @property eventType Tipo semântico do evento ([PlayerEventType]).
 * @property gamertag Nome do jogador identificado no evento.
 * @property xuid Identificador Xbox Live (XUID) capturado na mensagem de log.
 * @property rawLine Linha original completa para inspeção técnica caso desejado.
 */
data class PlayerConnectionEvent(
    val timestamp: String,
    val eventType: PlayerEventType,
    val gamertag: String,
    val xuid: String? = null,
    val rawLine: String
)

/**
 * Modos de jogo suportados pelo comando `gamemode` do Bedrock Dedicated Server.
 *
 * @property commandValue Argumento literal enviado ao FIFO do BDS.
 * @property displayName Nome amigável exibido na interface gráfica em Português.
 */
enum class GameModeOption(val commandValue: String, val displayName: String) {
    SURVIVAL("survival", "Sobrevivência"),
    CREATIVE("creative", "Criativo"),
    ADVENTURE("adventure", "Aventura"),
    SPECTATOR("spectator", "Espectador")
}

