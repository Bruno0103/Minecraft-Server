#!/usr/bin/env bash
# ==============================================================================
# SCRIPT DE EXPANSÃO DE MEMÓRIA VIRTUAL (SWAP 5.0 GB) - MINECRAFT BEDROCK
# ==============================================================================
# Autor: Equipe de Engenharia / Antigravity
# Finalidade: Ampliar a memória virtual do servidor de 2.5 GB para 5.0 GB em SSD
#             NVMe, preparando o ambiente para suportar múltiplos addons pesados
#             (Behavior Packs com IA complexa, scripts JS e novas entidades)
#             sem risco de encerramento por esgotamento de memória (OOM Killer).
# Sistema Operacional: Oracle Linux 9.8 (Kernel UEK 6.12 x86_64)
# ==============================================================================

set -euo pipefail

# Definição de cores para formatação visual dos logs no terminal
VERDE='\033[0;32m'
AMARELO='\033[1;33m'
AZUL='\033[0;34m'
VERMELHO='\033[0;31m'
SEM_COR='\033[0m'

echo -e "${AZUL}====================================================================${SEM_COR}"
echo -e "${AZUL}    INICIANDO EXPANSÃO DE MEMÓRIA VIRTUAL (SWAP: 5.0 GB)           ${SEM_COR}"
echo -e "${AZUL}====================================================================${SEM_COR}"

# 1. Verificação de privilégios de superusuário (Root)
if [[ $EUID -ne 0 ]]; then
    echo -e "${VERMELHO}[ERRO] Este script precisa ser executado com privilégios de root (sudo).${SEM_COR}"
    exit 1
fi

# 2. Interrupção graciosa do serviço do Minecraft para liberar RAM durante a alocação
echo -e "\n${AMARELO}[1/7] Pausando temporariamente o serviço do Minecraft (bedrock.service)...${SEM_COR}"
if systemctl is-active --quiet bedrock.service; then
    systemctl stop bedrock.service
    echo -e "${VERDE}[OK] Servidor Minecraft pausado com sucesso.${SEM_COR}"
else
    echo -e "${AZUL}[INFO] Servidor Minecraft já estava parado.${SEM_COR}"
fi

# 3. Desativação do arquivo de Swap atual (/swapfile)
echo -e "\n${AMARELO}[2/7] Desativando a área de Swap atual...${SEM_COR}"
if swapon --show | grep -q '/swapfile'; then
    swapoff /swapfile
    echo -e "${VERDE}[OK] Swap atual desativado.${SEM_COR}"
else
    echo -e "${AZUL}[INFO] Nenhum swap ativo em /swapfile detectado.${SEM_COR}"
fi

# 4. Remoção do arquivo de Swap antigo
echo -e "\n${AMARELO}[3/7] Removendo arquivo de Swap anterior de 2.5 GB...${SEM_COR}"
rm -f /swapfile
echo -e "${VERDE}[OK] Arquivo antigo removido com segurança.${SEM_COR}"

# 5. Alocação de 5120 MB (5.0 GB) de blocos contíguos no SSD NVMe
#    'bs=1M' aloca em blocos de 1 Megabyte e 'count=5120' gera exatamente 5 Gigabytes
echo -e "\n${AMARELO}[4/7] Alocando 5.0 GB (5120 MB) contíguos no SSD NVMe (aguarde alguns segundos)...${SEM_COR}"
dd if=/dev/zero of=/swapfile bs=1M count=5120 status=progress
echo -e "${VERDE}[OK] Alocação concluída com sucesso.${SEM_COR}"

# 6. Aplicação de permissões estritas (apenas o superusuário pode ler/escrever)
echo -e "\n${AMARELO}[5/7] Configurando permissões estritas de segurança (0600)...${SEM_COR}"
chmod 600 /swapfile
echo -e "${VERDE}[OK] Permissões aplicadas (somente root).${SEM_COR}"

# 7. Formatação e Ativação do novo arquivo de Swap
echo -e "\n${AMARELO}[6/7] Formatando a assinatura de Swap e ativando no kernel...${SEM_COR}"
mkswap /swapfile
swapon /swapfile
echo -e "${VERDE}[OK] Novo Swap de 5.0 GB ativado no kernel.${SEM_COR}"

# 8. Garantir que a montagem persistente existe no /etc/fstab
if ! grep -q '/swapfile swap swap defaults' /etc/fstab; then
    echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
    echo -e "${VERDE}[OK] Linha de montagem adicionada em /etc/fstab.${SEM_COR}"
else
    echo -e "${AZUL}[INFO] Montagem em /etc/fstab já está configurada.${SEM_COR}"
fi

# 9. Calibração de parâmetros de kernel para evitar lag de leitura de disco no Minecraft
echo -e "\n${AMARELO}[7/7] Calibrando parâmetros do kernel (sysctl) para servidores de jogos...${SEM_COR}"
tee /etc/sysctl.d/99-minecraft.conf > /dev/null << 'EOF'
# Define swappiness em 20 para forçar o Linux a esgotar a RAM física antes de paginar no SSD
vm.swappiness=20

# Controla a pressão de cache de diretórios e inodes para manter chunks em memória
vm.vfs_cache_pressure=50
EOF
sysctl --system > /dev/null
echo -e "${VERDE}[OK] Parâmetros de kernel aplicados.${SEM_COR}"

# 10. Reinicialização do serviço do Minecraft
echo -e "\n${AMARELO}Reativando o serviço do Minecraft (bedrock.service)...${SEM_COR}"
systemctl start bedrock.service

# 11. Relatório final de status da memória
echo -e "\n${VERDE}====================================================================${SEM_COR}"
echo -e "${VERDE}  SUCESSO: MEMÓRIA VIRTUAL DE 5.0 GB CONFIGURADA E ATIVA!           ${SEM_COR}"
echo -e "${VERDE}====================================================================${SEM_COR}"
free -h
echo -e "\n${AZUL}Status do serviço bedrock.service:${SEM_COR}"
systemctl status bedrock.service --no-pager -n 3
