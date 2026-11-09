#!/usr/bin/env bash
# ==============================================================================
# SCRIPT DE INSTALAÇÃO E ATUALIZAÇÃO DO BEDROCK DEDICATED SERVER (BDS)
# Servidor: Never Land | Sistema Operacional: Oracle Linux 9 (x86_64)
# ==============================================================================
# Este script automatiza o processo de consulta, download oficial e extração
# da versão mais recente do Bedrock Dedicated Server da Mojang Studios.
#
# Principais funcionalidades:
# 1. Consulta dinâmica da versão mais recente estável através da API oficial da Mojang
#    com fallback para raspagem da página oficial de download.
# 2. Utilização mandatória de cabeçalho User-Agent de navegador para contornar
#    bloqueios HTTP 403 Forbidden impostos pelo CDN Azure Front Door da Mojang.
# 3. Verificação de integridade do pacote compactado ZIP antes da extração.
# 4. Parada graciosa do serviço systemd (bedrock.service), se ativo, evitando
#    conflitos de arquivo e travamento de descritores abertos.
# 5. Preservação estrita de arquivos críticos de configuração e persistência:
#    - server.properties (configurações do servidor e anti-cheats)
#    - permissions.json e allowlist.json / whitelist.json (permissões de jogadores)
#    - worlds/* (todos os chunks, LevelDB e dados de mundos existentes)
# 6. Atribuição de permissões de execução (chmod +x) ao binário bedrock_server
#    e garantia de propriedade do usuário opc:opc.
# ==============================================================================

# Ativação de modo estrito de execução:
# -e: Aborta imediatamente se qualquer comando retornar erro.
# -u: Aborta se variáveis não declaradas forem referenciadas.
# -o pipefail: Garante que erros em qualquer estágio de um pipeline de comandos sejam capturados.
set -euo pipefail

# Definição dos caminhos absolutos do ambiente
BASE_DIR="/home/opc/minecraft"
SERVER_DIR="${BASE_DIR}/server"
DOWNLOADS_DIR="${BASE_DIR}/downloads"
TEMP_ZIP="${DOWNLOADS_DIR}/bedrock_latest.zip"
SERVICE_NAME="bedrock.service"

# Cabeçalho User-Agent simulando navegador moderno (mandatório para Azure Front Door / Mojang CDN)
USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

echo "=============================================================================="
echo " [BDS] Iniciando automação de instalação/atualização do Bedrock Server"
echo " Servidor: Never Land | Diretório Alvo: ${SERVER_DIR}"
echo "=============================================================================="

# ------------------------------------------------------------------------------
# ETAPA 1: Garantir a existência da estrutura de diretórios base
# ------------------------------------------------------------------------------
echo "[1/6] Verificando e criando diretórios de trabalho..."
mkdir -p "${SERVER_DIR}" "${DOWNLOADS_DIR}" "${BASE_DIR}/backups" "${BASE_DIR}/addons_inbox/instalados"

# ------------------------------------------------------------------------------
# ETAPA 2: Obter a URL oficial da versão mais recente do BDS para Linux x86_64
# ------------------------------------------------------------------------------
echo "[2/6] Consultando URL da versão estável mais recente da Mojang..."
DOWNLOAD_URL=""

# Tentativa Primária: API oficial de links de download de serviços da Mojang
DOWNLOAD_URL=$(curl -s -L -H "User-Agent: ${USER_AGENT}" \
    "https://net-secondary.web.minecraft-services.net/api/v1.0/download/links" 2>/dev/null | \
    python3 -c "import sys, json; data = json.load(sys.stdin); print(''.join(item.get('downloadUrl', '') for item in data.get('result', {}).get('links', []) if item.get('downloadType') == 'serverBedrockLinux'))" 2>/dev/null || true)

# Tentativa Secundária (Fallback): Raspagem HTML da página oficial de download
if [ -z "${DOWNLOAD_URL}" ]; then
    echo "  -> API indisponível, consultando página HTML oficial da Mojang..."
    DOWNLOAD_URL=$(curl -s -L -H "User-Agent: ${USER_AGENT}" \
        "https://www.minecraft.net/en-us/download/server/bedrock" 2>/dev/null | \
        grep -o 'https://www.minecraft.net/bedrockdedicatedserver/bin-linux/bedrock-server-[^"]*\.zip' | \
        head -n 1 || true)
fi

# Fallback Final: URL estável homologada caso ambas as consultas dinâmicas falhem
if [ -z "${DOWNLOAD_URL}" ]; then
    echo "  -> Aviso: Consultas dinâmicas falharam. Utilizando URL estável homologada."
    DOWNLOAD_URL="https://www.minecraft.net/bedrockdedicatedserver/bin-linux/bedrock-server-1.26.61.1.zip"
fi

echo "  -> URL de download confirmada: ${DOWNLOAD_URL}"

# ------------------------------------------------------------------------------
# ETAPA 3: Download do pacote oficial do BDS
# ------------------------------------------------------------------------------
echo "[3/6] Efetuando o download do pacote oficial com validação de segurança..."
curl -fSL -H "User-Agent: ${USER_AGENT}" \
    -o "${TEMP_ZIP}" "${DOWNLOAD_URL}"

# Validação da integridade do arquivo ZIP antes de qualquer extração
echo "  -> Testando integridade dos dados compactados (unzip -t)..."
if ! unzip -t "${TEMP_ZIP}" > /dev/null 2>&1; then
    echo "[ERRO FATAL] O arquivo baixado em ${TEMP_ZIP} está corrompido ou incompleto."
    rm -f "${TEMP_ZIP}"
    exit 1
fi
echo "  -> Pacote ZIP verificado e íntegro!"

# ------------------------------------------------------------------------------
# ETAPA 4: Gerenciamento do serviço systemd (Parada se estiver ativo)
# ------------------------------------------------------------------------------
WAS_RUNNING=0
if systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
    echo "[4/6] Serviço ${SERVICE_NAME} detectado em execução. Parando graciosamente..."
    WAS_RUNNING=1
    sudo systemctl stop "${SERVICE_NAME}"
    echo "  -> Serviço pausado para evitar conflito de binários e bibliotecas."
else
    echo "[4/6] O serviço ${SERVICE_NAME} não está em execução no momento."
fi

# ------------------------------------------------------------------------------
# ETAPA 5: Extração seletiva preservando configurações, permissões e mundos
# ------------------------------------------------------------------------------
echo "[5/6] Descompactando binários e pacotes vanilla em ${SERVER_DIR}..."
# A flag -x exclui explicitamente da extração os arquivos de configuração e dados de mundos,
# garantindo que atualizações de versão JAMAIS sobrescrevam o progresso dos jogadores
# ou redefinam allow-cheats, server-name, whitelist ou permissões de operadores.
unzip -q -o "${TEMP_ZIP}" -d "${SERVER_DIR}" \
    -x "server.properties" "permissions.json" "whitelist.json" "allowlist.json" "worlds/*" || true

# Atribuição mandatória de permissão de execução ao binário principal C++
echo "  -> Concedendo permissão de execução ao binário bedrock_server..."
chmod +x "${SERVER_DIR}/bedrock_server"

# Ajuste geral de propriedade para opc:opc em toda a estrutura do Minecraft
chown -R opc:opc "${BASE_DIR}"

# ------------------------------------------------------------------------------
# ETAPA 6: Limpeza de temporários e reinicialização condicional do serviço
# ------------------------------------------------------------------------------
echo "[6/6] Finalizando manutenção e limpando arquivos temporários..."
rm -f "${TEMP_ZIP}"

if [ "${WAS_RUNNING}" -eq 1 ]; then
    echo "  -> Reiniciando serviço ${SERVICE_NAME}..."
    sudo systemctl start "${SERVICE_NAME}"
    echo "  -> Servidor reiniciado com sucesso na nova versão!"
fi

echo "=============================================================================="
echo " [BDS] Operação concluída com sucesso!"
echo " Binário: ${SERVER_DIR}/bedrock_server"
echo " Versão instalada pronta para execução."
echo "=============================================================================="
exit 0
