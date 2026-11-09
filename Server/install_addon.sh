#!/usr/bin/env bash
# ==============================================================================
# SCRIPT DE INSTALAÇÃO AUTOMATIZADA DE ADDONS (MINECRAFT BEDROCK SERVER)
# ==============================================================================
# Autor: Equipe de Engenharia / Antigravity
# Finalidade: Inspecionar a pasta 'addons_inbox/', descompactar arquivos .mcaddon,
#             .mcpack ou .zip, identificar se são Behavior Packs (BP) ou Resource
#             Packs (RP), extrair os UUIDs e versões dos respectivos manifest.json
#             e cadastrá-los automaticamente em 'world_behavior_packs.json' e
#             'world_resource_packs.json', garantindo ativação no mundo Never Land.
# Local de Execução: Host Remoto (/home/opc/minecraft/)
# ==============================================================================

set -euo pipefail

BASE_DIR="/home/opc/minecraft"
SERVER_DIR="${BASE_DIR}/server"
INBOX_DIR="${BASE_DIR}/addons_inbox"
WORLDS_DIR="${SERVER_DIR}/worlds"

# Cores para formatação de saída no terminal
VERDE='\033[0;32m'
AMARELO='\033[1;33m'
AZUL='\033[0;34m'
VERMELHO='\033[0;31m'
SEM_COR='\033[0m'

echo -e "${AZUL}====================================================================${SEM_COR}"
echo -e "${AZUL}     GERENCIADOR AUTOMATIZADO DE ADDONS - MINECRAFT BEDROCK         ${SEM_COR}"
echo -e "${AZUL}====================================================================${SEM_COR}"

# 1. Garante que as pastas essenciais existam
mkdir -p "${INBOX_DIR}"
mkdir -p "${SERVER_DIR}/behavior_packs"
mkdir -p "${SERVER_DIR}/resource_packs"

# 2. Localiza o diretório do mundo ativo em worlds/
WORLD_NAME=$(grep '^level-name=' "${SERVER_DIR}/server.properties" | cut -d'=' -f2 | tr -d '\r')
if [[ -z "${WORLD_NAME}" ]]; then
    WORLD_NAME="Bedrock level"
fi
ACTIVE_WORLD_DIR="${WORLDS_DIR}/${WORLD_NAME}"

echo -e "${AMARELO}[*] Mundo ativo detectado:${SEM_COR} ${ACTIVE_WORLD_DIR}"

# 3. Executa o processador inteligente em Python para inspecionar e instalar
python3 - << 'EOF'
import os
import sys
import json
import zipfile
import shutil

inbox_dir = "/home/opc/minecraft/addons_inbox"
server_dir = "/home/opc/minecraft/server"
bp_dest = os.path.join(server_dir, "behavior_packs")
rp_dest = os.path.join(server_dir, "resource_packs")

# Obtem o nome do mundo
level_name = "Bedrock level"
props_file = os.path.join(server_dir, "server.properties")
if os.path.exists(props_file):
    with open(props_file, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if line.startswith("level-name="):
                level_name = line.strip().split("=", 1)[1]
                break

world_dir = os.path.join(server_dir, "worlds", level_name)
world_bp_file = os.path.join(world_dir, "world_behavior_packs.json")
world_rp_file = os.path.join(world_dir, "world_resource_packs.json")

def load_json(filepath):
    if os.path.exists(filepath):
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return []
    return []

def save_json(filepath, data):
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

def register_pack(json_path, pack_id, version):
    packs = load_json(json_path)
    # Verifica se já está registrado para evitar duplicatas
    for entry in packs:
        if entry.get("pack_id") == pack_id:
            entry["version"] = version
            save_json(json_path, packs)
            return False  # Já existia, foi apenas atualizado
    packs.append({"pack_id": pack_id, "version": version})
    save_json(json_path, packs)
    return True  # Novo registro adicionado

def process_single_pack(extracted_folder, pack_name):
    manifest_path = os.path.join(extracted_folder, "manifest.json")
    if not os.path.exists(manifest_path):
        # Pode estar dentro de uma subpasta
        subdirs = [d for d in os.listdir(extracted_folder) if os.path.isdir(os.path.join(extracted_folder, d))]
        for sub in subdirs:
            candidate = os.path.join(extracted_folder, sub, "manifest.json")
            if os.path.exists(candidate):
                manifest_path = candidate
                extracted_folder = os.path.join(extracted_folder, sub)
                break

    if not os.path.exists(manifest_path):
        print(f"  [AVISO] manifest.json não encontrado em {pack_name}, pulando...")
        return

    with open(manifest_path, "r", encoding="utf-8", errors="ignore") as f:
        manifest = json.load(f)

    header = manifest.get("header", {})
    pack_id = header.get("uuid")
    version = header.get("version", [1, 0, 0])
    name = header.get("name", pack_name)

    # Identifica o tipo através dos modules
    is_bp = False
    is_rp = False
    for mod in manifest.get("modules", []):
        mtype = mod.get("type", "").lower()
        if mtype in ["data", "script", "client_data"]:
            is_bp = True
        elif mtype in ["resources"]:
            is_rp = True

    if is_bp:
        target_dir = os.path.join(bp_dest, pack_name)
        if os.path.exists(target_dir):
            shutil.rmtree(target_dir)
        shutil.copytree(extracted_folder, target_dir)
        is_new = register_pack(world_bp_file, pack_id, version)
        status = "Registrado" if is_new else "Atualizado"
        print(f"  [OK] Behavior Pack (BP): '{name}' ({pack_id}) -> {status}")
    elif is_rp:
        target_dir = os.path.join(rp_dest, pack_name)
        if os.path.exists(target_dir):
            shutil.rmtree(target_dir)
        shutil.copytree(extracted_folder, target_dir)
        is_new = register_pack(world_rp_file, pack_id, version)
        status = "Registrado" if is_new else "Atualizado"
        print(f"  [OK] Resource Pack (RP): '{name}' ({pack_id}) -> {status}")
    else:
        # Padrão: trata como behavior se tiver scripts/entities
        target_dir = os.path.join(bp_dest, pack_name)
        if os.path.exists(target_dir):
            shutil.rmtree(target_dir)
        shutil.copytree(extracted_folder, target_dir)
        register_pack(world_bp_file, pack_id, version)
        print(f"  [OK] Pack genérico instalado como BP: '{name}' ({pack_id})")

# Varredura dos arquivos na pasta inbox
files = [f for f in os.listdir(inbox_dir) if f.endswith(('.mcaddon', '.mcpack', '.zip'))]
if not files:
    print("\n[INFO] Nenhum arquivo .mcaddon, .mcpack ou .zip encontrado em /home/opc/minecraft/addons_inbox/")
    print("       Envie seus arquivos de addon para essa pasta e execute este script novamente.")
    sys.exit(0)

print(f"\n[+] Encontrados {len(files)} arquivo(s) de addon para processar:")
tmp_work = "/tmp/minecraft_addon_extract"

for filename in files:
    filepath = os.path.join(inbox_dir, filename)
    print(f"\n-> Processando: {filename}")
    if os.path.exists(tmp_work):
        shutil.rmtree(tmp_work)
    os.makedirs(tmp_work, exist_ok=True)

    try:
        with zipfile.ZipFile(filepath, 'r') as zip_ref:
            zip_ref.extractall(tmp_work)

        # Se for um .mcaddon contendo múltiplos .mcpacks internos
        inner_mcpacks = [f for f in os.listdir(tmp_work) if f.endswith('.mcpack')]
        if inner_mcpacks:
            for inner in inner_mcpacks:
                inner_path = os.path.join(tmp_work, inner)
                inner_dest = os.path.join(tmp_work, f"extracted_{inner}")
                with zipfile.ZipFile(inner_path, 'r') as inner_zip:
                    inner_zip.extractall(inner_dest)
                process_single_pack(inner_dest, os.path.splitext(inner)[0])
        else:
            # É um pacote direto (.mcpack ou .zip)
            process_single_pack(tmp_work, os.path.splitext(filename)[0])

        # Move o arquivo processado para uma pasta de backup
        processed_dir = os.path.join(inbox_dir, "instalados")
        os.makedirs(processed_dir, exist_ok=True)
        shutil.move(filepath, os.path.join(processed_dir, filename))
        print(f"  [SUCESSO] Addon '{filename}' instalado e arquivado com sucesso!")

    except Exception as e:
        print(f"  [ERRO] Falha ao processar {filename}: {e}")
    finally:
        if os.path.exists(tmp_work):
            shutil.rmtree(tmp_work)

EOF

# 4. Ajusta permissões dos arquivos para o usuário opc
echo -e "\n${AMARELO}[*] Normalizando permissões dos pacotes instalados...${SEM_COR}"
chown -R opc:opc "${SERVER_DIR}/behavior_packs" "${SERVER_DIR}/resource_packs" "${WORLDS_DIR}" "${INBOX_DIR}"
chmod -R u=rwX,go=rX "${SERVER_DIR}/behavior_packs" "${SERVER_DIR}/resource_packs" "${WORLDS_DIR}"

echo -e "\n${VERDE}====================================================================${SEM_COR}"
echo -e "${VERDE}  PROCESSAMENTO DE ADDONS CONCLUÍDO!                                ${SEM_COR}"
echo -e "${VERDE}====================================================================${SEM_COR}"
echo -e "${AZUL}Para que as alterações entrem em vigor no jogo, reinicie o servidor com:${SEM_COR}"
echo -e "sudo systemctl restart bedrock.service"
