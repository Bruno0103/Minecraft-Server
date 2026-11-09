#!/usr/bin/env bash
# ==============================================================================
# SISTEMA DE BACKUP AUTOMATIZADO - MINECRAFT BEDROCK DEDICATED SERVER (BDS)
# Servidor: Never Land
# Arquivo: Server/backup.sh (Versão local sincronizada com /home/opc/minecraft/backup.sh)
# Destino na VM: /home/opc/minecraft/backup.sh
# ==============================================================================
#
# DESCRIÇÃO:
#   Este script realiza o backup completo e consistente do mundo ativo,
#   das configurações do servidor e das permissões de administradores/jogadores.
#   Os arquivos são compactados no formato .tar.gz com carimbo de data e hora (timestamp),
#   armazenados em /home/opc/minecraft/backups/ e auditados com política de retenção de 7 dias.
#
# ESTRUTURA DOS BACKUPS:
#   - Origem: /home/opc/minecraft/server/
#     * worlds/ (Mundo Never Land, chunks, level.dat, LevelDB)
#     * server.properties (Configurações operacionais e regras)
#     * permissions.json (Permissões de operadores e membros)
#     * allowlist.json / whitelist.json (se existirem)
#   - Destino: /home/opc/minecraft/backups/backup_never_land_${TIMESTAMP}.tar.gz
#   - Logs: /home/opc/minecraft/backup.log
#
# POLÍTICA DE RETENÇÃO:
#   - Remove backups anteriores a 7 dias (+7 no find) para preservar espaço em disco.
#
# AGENDAMENTO NA CRONTAB (Execução diária às 04:00 da madrugada):
#   0 4 * * * /home/opc/minecraft/backup.sh
# ==============================================================================

set -euo pipefail

# Definição dos caminhos fundamentais
BASE_DIR="/home/opc/minecraft"
SERVER_DIR="${BASE_DIR}/server"
BACKUP_DIR="${BASE_DIR}/backups"
LOG_FILE="${BASE_DIR}/backup.log"

# Carimbo de data e hora para versionamento do arquivo
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILENAME="backup_never_land_${TIMESTAMP}.tar.gz"
BACKUP_FILEPATH="${BACKUP_DIR}/${BACKUP_FILENAME}"

# Função utilitária para registro padronizado de logs (console + arquivo)
log() {
    local nivel="$1"
    local mensagem="$2"
    local data_hora
    data_hora=$(date +"%Y-%m-%d %H:%M:%S")
    echo "[${data_hora}] [${nivel}] ${mensagem}" | tee -a "${LOG_FILE}"
}

log "INFO" "===================================================================="
log "INFO" "INICIANDO ROTINA DE BACKUP - MINECRAFT SERVER: Never Land"
log "INFO" "===================================================================="

# 1. Garante a existência do diretório de destino dos backups
if [[ ! -d "${BACKUP_DIR}" ]]; then
    log "INFO" "Criando diretório de backups em: ${BACKUP_DIR}"
    mkdir -p "${BACKUP_DIR}"
fi

# 2. Garante a existência do arquivo permissions.json se ainda não existir
if [[ ! -f "${SERVER_DIR}/permissions.json" ]]; then
    log "AVISO" "permissions.json não encontrado. Criando arquivo padrão vazio..."
    echo "[]" > "${SERVER_DIR}/permissions.json"
fi

# 3. Monta a lista de alvos existentes para compactação dentro do diretório do servidor
TARGETS=()

if [[ -d "${SERVER_DIR}/worlds" ]]; then
    TARGETS+=("worlds")
else
    log "ERRO" "Diretório worlds não encontrado em ${SERVER_DIR}/worlds! Abortando."
    exit 1
fi

if [[ -f "${SERVER_DIR}/server.properties" ]]; then
    TARGETS+=("server.properties")
else
    log "AVISO" "server.properties não encontrado em ${SERVER_DIR}. Continuando..."
fi

if [[ -f "${SERVER_DIR}/permissions.json" ]]; then
    TARGETS+=("permissions.json")
fi

if [[ -f "${SERVER_DIR}/allowlist.json" ]]; then
    TARGETS+=("allowlist.json")
elif [[ -f "${SERVER_DIR}/whitelist.json" ]]; then
    TARGETS+=("whitelist.json")
fi

log "INFO" "Arquivos e pastas selecionados para backup: ${TARGETS[*]}"

# 4. Executa a compactação .tar.gz
log "INFO" "Gerando arquivo compactado: ${BACKUP_FILENAME}..."
tar -czf "${BACKUP_FILEPATH}" -C "${SERVER_DIR}" "${TARGETS[@]}"

# 5. Validação da integridade do arquivo gerado
if [[ -f "${BACKUP_FILEPATH}" ]]; then
    TAMANHO=$(du -h "${BACKUP_FILEPATH}" | cut -f1)
    log "SUCESSO" "Backup concluído com êxito! Arquivo: ${BACKUP_FILENAME} (Tamanho: ${TAMANHO})"
else
    log "ERRO" "Falha na geração do arquivo de backup: ${BACKUP_FILEPATH}"
    exit 1
fi

# 6. Aplicação da política de retenção de 7 dias
log "INFO" "Aplicando política de retenção de 7 dias em: ${BACKUP_DIR}"
find "${BACKUP_DIR}" -type f -name "backup_never_land_*.tar.gz" -mtime +7 -exec rm -v {} \; >> "${LOG_FILE}" 2>&1 || true

log "INFO" "ROTINA DE BACKUP CONCLUÍDA COM SUCESSO."
log "INFO" "===================================================================="
