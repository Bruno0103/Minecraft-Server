package com.neverland.servermanager.domain.model

/**
 * ==============================================================================
 * Never Land Server Manager - Modelos de Domínio de Backup (BackupInfo.kt)
 * ==============================================================================
 *
 * Estruturas imutáveis de dados para representar o resultado da execução de
 * rotinas de Hot-Backup atômico (`save hold` / `save query` / `save resume`)
 * e os arquivos `.tar.gz` armazenados em `/home/opc/minecraft/backups`.
 * ==============================================================================
 */

/**
 * Resultado consolidado após o disparo manual de um Hot-Backup via comando `mc backup`.
 *
 * @property success Indica se o script de backup finalizou com código de retorno 0 e arquivo íntegro.
 * @property elapsedSeconds Tempo total gasto na operação em segundos (ex.: 4.2f).
 * @property filename Nome do arquivo `.tar.gz` gerado (ou descrição caso não identificado).
 * @property sizeText Tamanho formatado do arquivo gerado (ex.: "42.5M"), se disponível na saída.
 * @property message Mensagem completa de retorno para apresentação ao operador.
 * @property timestamp Momento da conclusão da operação em milissegundos.
 */
data class BackupResult(
    val success: Boolean,
    val elapsedSeconds: Float,
    val filename: String,
    val sizeText: String? = null,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Representa um arquivo de backup existente no diretório `/home/opc/minecraft/backups`
 * listado pelo comando `mc backups` (`ls -lh`).
 *
 * @property filename Nome do arquivo compactado (ex.: `bedrock_backup_2026-11-09_04-00-01.tar.gz`).
 * @property sizeText Tamanho legível reportado pelo sistema de arquivos Linux (ex.: `38M`, `41.2M`).
 * @property dateText Data e hora de modificação reportadas pelo `ls -lh` (ex.: `Nov 9 04:00`).
 * @property permissions Permissões POSIX e proprietário para auditoria visual (opcional).
 * @property rawLine Linha original do comando `ls -lh`.
 */
data class BackupFileEntry(
    val filename: String,
    val sizeText: String,
    val dateText: String,
    val permissions: String = "",
    val rawLine: String = ""
)

