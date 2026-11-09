# ==============================================================================
# Script de Execução Direta da Migração no Servidor com Loop Resiliente
# ==============================================================================
# Executa a rotina de migração diretamente no servidor com tentativas automáticas
# e intervalo de 15 segundos entre tentativas para evitar o bloqueio de conexão.
# ==============================================================================

import subprocess
import time
import sys

KEY = r"Server\VM\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

REMOTE_SCRIPT = r"""
import json
import os
import sys
import shutil
import tarfile
import subprocess
import time

SERVER_DIR = "/home/opc/minecraft/server"
WORLDS_DIR = os.path.join(SERVER_DIR, "worlds")
WORLD_NAME = "Never Land"
WORLD_PATH = os.path.join(WORLDS_DIR, WORLD_NAME)
W_RP_JSON = os.path.join(WORLD_PATH, "world_resource_packs.json")
W_BP_JSON = os.path.join(WORLD_PATH, "world_behavior_packs.json")

RP_DIR = os.path.join(SERVER_DIR, "resource_packs")
BP_DIR = os.path.join(SERVER_DIR, "behavior_packs")

OLD_RP_UUID = "5025473d-cb9b-4dac-8849-ef6b7560e86b"  # Never Land Texture Pack antigo
NEW_RP_UUID = "e547c6e6-d6d3-4b21-8a95-a2b013fc1669"  # Never Land Essencial
NEW_RP_VER = [1, 0, 0]

LIGHTING_BP_UUID = "d0f07809-c63f-4c28-bfbf-43488b4010a2" # Advanced Dynamic Lighting v6
LIGHTING_BP_VER = [6, 1, 0]

def log(msg):
    print(f"[DEPLOY] {msg}", flush=True)

def run_cmd(cmd_list):
    res = subprocess.run(cmd_list, capture_output=True, text=True)
    if res.returncode != 0:
        log(f"Comando retornou {res.returncode}: {' '.join(cmd_list)}\nStderr: {res.stderr.strip()}")
    return res.returncode, res.stdout.strip()

log("=== INICIANDO ATUALIZAÇÃO NO SERVIDOR NEVER LAND ===")

# 1. Notifica os jogadores no chat
log("Notificando jogadores via chat do Minecraft...")
run_cmd(["python3", "/home/opc/minecraft/mc.py", "say", "§6[MANUTENÇÃO] Atualizando Never Land Essencial e Dynamic Lighting V6... Reinicio breve."])
time.sleep(2)

# 2. Desliga o servidor de forma graciosa para salvar o mundo
log("Desligando o servidor graciosamente para salvar os dados no LevelDB...")
run_cmd(["python3", "/home/opc/minecraft/mc.py", "stop"])
time.sleep(4)

# 3. Extrai Never Land Essencial em resource_packs
log("Extraindo Never Land Essencial...")
essencial_tar = "/home/opc/minecraft/NeverLandEssencial.tar.gz"
if os.path.exists(essencial_tar):
    with tarfile.open(essencial_tar, "r:gz") as tar:
        tar.extractall(RP_DIR)
    log("Never Land Essencial extraído com sucesso em resource_packs/NeverLandEssencial.")
else:
    log(f"ERRO CRÍTICO: {essencial_tar} não encontrado!")
    sys.exit(1)

# 4. Remove / Arquiva NeverLandTexturePack antigo
old_rp_path = os.path.join(RP_DIR, "NeverLandTexturePack")
if os.path.exists(old_rp_path):
    backup_old = os.path.join(RP_DIR, "NeverLandTexturePack.backup_deprecated")
    if os.path.exists(backup_old):
        shutil.rmtree(backup_old)
    shutil.move(old_rp_path, backup_old)
    log("NeverLandTexturePack antigo arquivado em NeverLandTexturePack.backup_deprecated.")

# 5. Atualiza world_resource_packs.json (Never Land Essencial na ÚLTIMA prioridade)
log("Atualizando world_resource_packs.json...")
if os.path.exists(W_RP_JSON):
    with open(W_RP_JSON, "r", encoding="utf-8") as f:
        rp_data = json.load(f)
        
    # Remove referências anteriores
    filtered_rp = [p for p in rp_data if p.get("pack_id") not in (OLD_RP_UUID, NEW_RP_UUID)]
    
    # Adiciona Never Land Essencial no final absoluto (última prioridade no motor Bedrock)
    filtered_rp.append({
        "pack_id": NEW_RP_UUID,
        "version": NEW_RP_VER
    })
    
    with open(W_RP_JSON, "w", encoding="utf-8") as f:
        json.dump(filtered_rp, f, indent=2)
    log(f"world_resource_packs.json gravado. Total: {len(filtered_rp)} pacotes. Último: {NEW_RP_UUID}")
else:
    log(f"ERRO CRÍTICO: {W_RP_JSON} não encontrado!")
    sys.exit(1)

# 6. Extrai e instala Advanced Dynamic Lighting v6
log("Extraindo Advanced Dynamic Lighting v6 BP...")
light_tar = "/home/opc/minecraft/AdvancedDynamicLighting_v6_BP.tar.gz"
if os.path.exists(light_tar):
    dest_light = os.path.join(BP_DIR, "AdvancedDynamicLighting_v6_BP")
    if os.path.exists(dest_light):
        shutil.rmtree(dest_light)
    with tarfile.open(light_tar, "r:gz") as tar:
        tar.extractall(BP_DIR)
    log("Advanced Dynamic Lighting v6 BP instalado com sucesso em behavior_packs/AdvancedDynamicLighting_v6_BP.")
else:
    log(f"ERRO CRÍTICO: {light_tar} não encontrado!")
    sys.exit(1)

# 7. Atualiza world_behavior_packs.json garantindo Advanced Dynamic Lighting v6
log("Atualizando world_behavior_packs.json...")
if os.path.exists(W_BP_JSON):
    with open(W_BP_JSON, "r", encoding="utf-8") as f:
        bp_data = json.load(f)
        
    has_lighting = False
    for entry in bp_data:
        if entry.get("pack_id") == LIGHTING_BP_UUID:
            entry["version"] = LIGHTING_BP_VER
            has_lighting = True
            break
    if not has_lighting:
        bp_data.insert(0, {
            "pack_id": LIGHTING_BP_UUID,
            "version": LIGHTING_BP_VER
        })
        
    with open(W_BP_JSON, "w", encoding="utf-8") as f:
        json.dump(bp_data, f, indent=2)
    log(f"world_behavior_packs.json gravado com {len(bp_data)} pacotes.")
else:
    log(f"ERRO CRÍTICO: {W_BP_JSON} não encontrado!")
    sys.exit(1)

# Ajuste de permissões
run_cmd(["chown", "-R", "opc:opc", SERVER_DIR])
run_cmd(["chmod", "-R", "u+rwX,go+rX", RP_DIR])
run_cmd(["chmod", "-R", "u+rwX,go+rX", BP_DIR])

# 8. Inicia o servidor Bedrock
log("Iniciando o servidor Bedrock...")
run_cmd(["python3", "/home/opc/minecraft/mc.py", "start"])
time.sleep(8)

# 9. Configura gamerule playersSleepingPercentage=1
log("Configurando gamerule playersSleepingPercentage=1...")
run_cmd(["python3", "/home/opc/minecraft/mc.py", "cmd", "gamerule playersSleepingPercentage 1"])
time.sleep(2)

# 10. Consulta a gamerule para confirmação
code, rule_out = run_cmd(["python3", "/home/opc/minecraft/mc.py", "cmd", "gamerule playersSleepingPercentage"])
log(f"Confirmação da regra de sono: {rule_out}")

# Limpeza de arquivos temporários
for tmp in [essencial_tar, light_tar]:
    if os.path.exists(tmp):
        os.remove(tmp)
        log(f"Arquivo temporário {tmp} removido.")

log("=== PROCESSO FINALIZADO COM ÊXITO! ===")
"""

def main():
    ssh_cmd = [
        "ssh", "-i", KEY,
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ConnectTimeout=15",
        "-o", "ServerAliveInterval=15",
        f"{USER}@{HOST}",
        "python3 -"
    ]
    
    max_attempts = 6
    delay = 15.0
    
    for attempt in range(1, max_attempts + 1):
        print(f"[SSH Tentativa {attempt}/{max_attempts}] Disparando migração no servidor...", flush=True)
        try:
            res = subprocess.run(
                ssh_cmd,
                input=REMOTE_SCRIPT,
                capture_output=True,
                text=True,
                timeout=180
            )
            if res.returncode == 0:
                print("\n--- SUCESSO TOTAL ---")
                print(res.stdout)
                return True
            else:
                print(f"[SSH Falha {attempt}] Código: {res.returncode}. Stderr: {res.stderr.strip()[:80]}")
        except Exception as e:
            print(f"[SSH Exceção {attempt}] {e}")
            
        print(f"Aguardando {delay}s antes da próxima tentativa...")
        time.sleep(delay)
        
    print("[ERRO FATAL] Todas as tentativas falharam.")
    sys.exit(1)

if __name__ == "__main__":
    main()
