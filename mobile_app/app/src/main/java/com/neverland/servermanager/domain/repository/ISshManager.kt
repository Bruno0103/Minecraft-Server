package com.neverland.servermanager.domain.repository

import com.neverland.servermanager.domain.model.CommandResult
import com.neverland.servermanager.domain.model.ConnectionState
import com.neverland.servermanager.domain.model.SshConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * ==============================================================================
 * Never Land Server Manager - Contrato da Interface SSH (ISshManager.kt)
 * ==============================================================================
 *
 * Contrato de serviço para o cliente de comunicação de rede SSHv2 com o servidor
 * Never Land Bedrock Dedicated Server (BDS) na Oracle Cloud Infrastructure.
 *
 * Princípios de Engenharia de Software e Clean Architecture:
 * 1. Independência de Frameworks: Esta interface de domínio não possui referências
 *    diretas a bibliotecas de terceiros (como SSHJ ou Bouncy Castle) nem a classes
 *    do Android SDK (`android.*`). Isso possibilita testes unitários puros na JVM.
 * 2. Reatividade First-Class: O estado da conexão é exposto de forma reativa através
 *    de [StateFlow], permitindo que componentes de UI (Jetpack Compose) ou ViewModels
 *    observem e reajam instantaneamente a mudanças de status.
 * 3. Assincronismo com Corrotinas: Todas as operações de entrada/saída de rede são
 *    funções suspensas ([suspend]), devendo ser executadas de forma cooperativa
 *    em threads de background (Dispatchers.IO).
 * 4. Concorrência Segura: Garante que múltiplas chamadas simultâneas de comandos
 *    sejam serializadas adequadamente para evitar corrupção de canal no socket.
 * ==============================================================================
 */
interface ISshManager {

    /**
     * Fluxo reativo do estado atual de conexão da sessão SSH.
     * Emite atualizações automáticas conforme a máquina de estados transiciona
     * entre [ConnectionState.Disconnected], [ConnectionState.Connecting],
     * [ConnectionState.Connected] e [ConnectionState.Error].
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Configuração SSH ativa no momento (ou null caso não configurada ou desconectada).
     */
    val activeConfig: StateFlow<SshConfig?>

    /**
     * Estabelece conexão e autenticação com o servidor Never Land BDS na nuvem.
     *
     * @param config Parâmetros de rede, credenciais e timeouts.
     * @return [Result.success] em caso de sucesso; [Result.failure] detalhando a causa em caso de erro.
     */
    suspend fun connect(config: SshConfig): Result<Unit>

    /**
     * Encerra a sessão SSH ativa e libera os recursos de socket e canais de comando.
     * Transiciona o [connectionState] para [ConnectionState.Disconnected].
     */
    suspend fun disconnect()

    /**
     * Consulta síncrona imediata da conectividade atual da sessão.
     *
     * @return true se houver conexão aberta e autenticada; false caso contrário.
     */
    fun isConnected(): Boolean

    /**
     * Executa um comando arbitrário no servidor remoto via canal de sessão SSHv2.
     *
     * @param command Linha de comando a ser executada na VM (ex: "mc status", "mc backup").
     * @param timeoutSeconds Tempo limite máximo de espera em segundos (padrão: 15s).
     * @return [Result.success] com [CommandResult] contendo código de saída, saída textual e latência.
     */
    suspend fun executeCommand(command: String, timeoutSeconds: Int = 15): Result<CommandResult>

    /**
     * Executa um teste rápido de conectividade SSH com a configuração informada.
     * Dispara o comando canônico 'echo "NEVERLAND_OK"', mede a latência em milissegundos
     * e retorna o resultado sem interferir na sessão primária ativa caso isolada.
     *
     * @param config Configuração a ser testada.
     * @return [Result.success] com [CommandResult] se a conexão e o comando forem bem-sucedidos.
     */
    suspend fun testConnection(config: SshConfig): Result<CommandResult>
}
