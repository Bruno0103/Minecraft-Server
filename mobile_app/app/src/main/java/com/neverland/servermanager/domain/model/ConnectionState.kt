package com.neverland.servermanager.domain.model

/**
 * ==============================================================================
 * Never Land Server Manager - Máquina de Estados da Conexão SSH (ConnectionState.kt)
 * ==============================================================================
 *
 * Modela de forma determinística, reativa e tipada o ciclo de vida completo
 * da conexão SSHv2 entre o aplicativo Android e a VM Oracle Cloud do Never Land.
 *
 * Estados Possíveis:
 * 1. [Disconnected]: Nenhuma sessão ativa; estado inicial de repouso ou pós-desconexão.
 * 2. [Connecting]: Conexão em processamento (resolução de host, handshake TCP e auth).
 * 3. [Connected]: Sessão SSHv2 aberta, autenticada e pronta para envio de comandos.
 * 4. [Error]: Falha ocorrida durante a tentativa de conexão ou durante a sessão ativa.
 *
 * Por ser uma classe selada (sealed class), permite consumo seguro e exaustivo
 * em expressões `when` no Jetpack Compose e ViewModels, prevenindo estados intermediários
 * inconsistentes na interface gráfica do usuário.
 * ==============================================================================
 */
sealed class ConnectionState {

    /**
     * Sessão desconectada. Nenhum socket aberto ou thread de rede em execução.
     */
    data object Disconnected : ConnectionState()

    /**
     * Handshake de conexão em andamento.
     * A interface gráfica deve apresentar indicador visual de progresso (loading)
     * e desabilitar interações concorrentes que dependam do canal de socket.
     */
    data object Connecting : ConnectionState()

    /**
     * Conexão SSHv2 autenticada e operacional com o servidor Never Land.
     *
     * @property host Endereço IP ou domínio do host conectado (ex: "157.151.2.91")
     * @property port Porta TCP utilizada para a conexão (ex: 22)
     * @property latencyMs Latência medida no teste rápido de ida e volta (RTT) em milissegundos
     * @property connectedAtMillis Timestamp Unix do momento de estabelecimento da conexão
     */
    data class Connected(
        val host: String,
        val port: Int,
        val latencyMs: Long,
        val connectedAtMillis: Long = System.currentTimeMillis()
    ) : ConnectionState()

    /**
     * Estado de erro indicando falha na conexão, rejeição de credenciais ou timeout de socket.
     *
     * @property message Mensagem legível e amigável em Português para exibição na UI
     * @property cause Exceção original capturada pelo subsistema de rede (opcional)
     * @property timestampMillis Timestamp Unix da ocorrência do erro
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val timestampMillis: Long = System.currentTimeMillis()
    ) : ConnectionState()

    // --------------------------------------------------------------------------
    // Propriedades Utilitárias para Simplificar Avaliações na Interface Compose
    // --------------------------------------------------------------------------

    /** Retorna true se a conexão estiver ativa, autenticada e pronta para uso */
    val isConnected: Boolean
        get() = this is Connected

    /** Retorna true se uma operação de conexão estiver em andamento */
    val isConnecting: Boolean
        get() = this is Connecting

    /** Retorna true se o cliente estiver desconectado */
    val isDisconnected: Boolean
        get() = this is Disconnected

    /** Retorna true se houver um estado de erro ativo */
    val isError: Boolean
        get() = this is Error
}
