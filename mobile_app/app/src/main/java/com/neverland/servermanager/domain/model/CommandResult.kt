package com.neverland.servermanager.domain.model

/**
 * ==============================================================================
 * Never Land Server Manager - Resultado de Execução de Comando (CommandResult.kt)
 * ==============================================================================
 *
 * Encapsula o resultado detalhado retornado após a execução de um comando remoto
 * via canal de sessão SSH na máquina virtual Linux do Never Land.
 *
 * @property exitCode Código de saída retornado pelo processo remoto (0 indica sucesso)
 * @property output Conteúdo textual capturado de stdout (ou stderr em caso de erro)
 * @property executionTimeMs Tempo total de execução do comando em milissegundos (latência)
 * ==============================================================================
 */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val executionTimeMs: Long = 0L
) {
    /**
     * Indica se o comando foi concluído com sucesso pelo sistema operacional (exitCode == 0).
     */
    val isSuccess: Boolean
        get() = exitCode == 0
}
