# ==============================================================================
# Script de Implantação e Governança Remota no Servidor Never Land
# ==============================================================================
# Autor: Equipe de Engenharia / Antigravity
# Finalidade:
#   1. Garantir que NeverLandEssencial.tar.gz e AdvancedDynamicLighting_v6_BP.tar.gz
#      estejam no servidor via SCP resiliente.
#   2. Executar script de migração completo via STDIN ('python3 -') em SSH:
#      - Aviso no chat com 'mc say'
#      - Parada graciosa com 'mc stop'
#      - Extração do Never Land Essencial em resource_packs
#      - Arquivamento/remoção do NeverLandTexturePack antigo
#      - Injeção em world_resource_packs.json com Never Land Essencial na ÚLTIMA prioridade
#      - Extração do Advanced Dynamic Lighting v6 em behavior_packs
#      - Injeção em world_behavior_packs.json garantindo Advanced Dynamic Lighting v6
#      - Ajuste de permissões opc:opc
#      - Inicialização do BDS com 'mc start'
#      - Configuração e auditoria da gamerule playersSleepingPercentage=1
#      - Limpeza dos arquivos .tar.gz temporários
# ==============================================================================

import subprocess
import time
import os
import sys
from pathlib import Path

KEY = r"Server\VM\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"
TEMP_DIR = Path(r"Server\temp_deploy")

REMOTE_MIGRATION_SCRIPT = r"""
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

log("=== INICIANDO MIGRACAO TRANSACIONAL NO SERVIDOR NEVER LAND ===")

# 1. Notifica os jogadores no chat
log("Notificando jogadores no servidor...")
run_cmd(["python3", "/home/opc/minecraft/mc.py", "say", "§6[MANUTENÇÃO] Servidor atualizando pacotes e regra de dormir... Reinicio em breve."])
time.sleep(3)

# 2. Desliga o servidor de forma graciosa para salvar chunks e LevelDB
log("Executando desligamento gracioso (mc stop)...")
run_cmd(["python3", "/home/opc/minecraft/mc.py", "stop"])
time.sleep(5)

# 3. Extrai Never Land Essencial em resource_packs
log("Instalando Never Land Essencial em resource_packs...")
essencial_tar = "/home/opc/minecraft/NeverLandEssencial.tar.gz"
if os.path.exists(essencial_tar):
    with tarfile.open(essencial_tar, "r:gz") as tar:
        tar.extractall(RP_DIR)
    log("Never Land Essencial extraído com sucesso.")
else:
    log(f"ERRO: {essencial_tar} não encontrado!")
    sys.exit(1)

# 4. Remove / Arquiva NeverLandTexturePack antigo
old_rp_path = os.path.join(RP_DIR, "NeverLandTexturePack")
if os.path.exists(old_rp_path):
    backup_old = os.path.join(RP_DIR, "NeverLandTexturePack.backup_deprecated")
    if os.path.exists(backup_old):
        shutil.rmtree(backup_old)
    shutil.move(old_rp_path, backup_old)
    log("NeverLandTexturePack antigo desativado e arquivado com sucesso.")

# 5. Atualiza world_resource_packs.json (Never Land Essencial na ÚLTIMA prioridade)
log("Atualizando world_resource_packs.json...")
if os.path.exists(W_RP_JSON):
    with open(W_RP_JSON, "r", encoding="utf-8") as f:
        rp_data = json.load(f)
        
    # Remove qualquer referência ao pack antigo ou versão anterior do essencial
    filtered_rp = [p for p in rp_data if p.get("pack_id") not in (OLD_RP_UUID, NEW_RP_UUID)]
    
    # Adiciona Never Land Essencial no FINAL da lista (última prioridade no Bedrock)
    filtered_rp.append({
        "pack_id": NEW_RP_UUID,
        "version": NEW_RP_VER
    })
    
    with open(W_RP_JSON, "w", encoding="utf-8") as f:
        json.dump(filtered_rp, f, indent=2)
    log(f"world_resource_packs.json atualizado. Total de pacotes: {len(filtered_rp)}. Último: {NEW_RP_UUID}")
else:
    log(f"ERRO: {W_RP_JSON} não encontrado!")
    sys.exit(1)

# 6. Extrai e instala Advanced Dynamic Lighting v6
log("Instalando Advanced Dynamic Lighting v6 BP...")
light_tar = "/home/opc/minecraft/AdvancedDynamicLighting_v6_BP.tar.gz"
if os.path.exists(light_tar):
    dest_light = os.path.join(BP_DIR, "AdvancedDynamicLighting_v6_BP")
    if os.path.exists(dest_light):
        shutil.rmtree(dest_light)
    with tarfile.open(light_tar, "r:gz") as tar:
        tar.extractall(BP_DIR)
    log("Advanced Dynamic Lighting v6 BP extraído com sucesso.")
else:
    log(f"ERRO: {light_tar} não encontrado!")
    sys.exit(1)

# 7. Atualiza world_behavior_packs.json garantindo a presença do v6
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
    log(f"world_behavior_packs.json atualizado com {len(bp_data)} pacotes.")
else:
    log(f"ERRO: {W_BP_JSON} não encontrado!")
    sys.exit(1)

# Ajusta permissões do sistema de arquivos
run_cmd(["chown", "-R", "opc:opc", SERVER_DIR])
run_cmd(["chmod", "-R", "u+rwX,go+rX", RP_DIR])
run_cmd(["chmod", "-R", "u+rwX,go+rX", BP_DIR])

# 8. Inicia o servidor Bedrock
log("Iniciando o servidor Bedrock (mc start)...")
run_cmd(["python3", "/home/opc/minecraft/mc.py", "start"])
time.sleep(10)

# 9. Configura a gamerule playersSleepingPercentage=1
log("Configurando gamerule playersSleepingPercentage=1 via console...")
run_cmd(["python3", "/home/opc/minecraft/mc.py", "cmd", "gamerule playersSleepingPercentage 1"])
time.sleep(2)

# 10. Consulta a gamerule para validação
code, rule_out = run_cmd(["python3", "/home/opc/minecraft/mc.py", "cmd", "gamerule playersSleepingPercentage"])
log(f"Validação da gamerule: {rule_out}")

# Limpa os arquivos .tar.gz
for tmp in [essencial_tar, light_tar]:
    if os.path.exists(tmp):
        os.remove(tmp)

log("=== MIGRACAO CONCLUIDA COM SUCESSO! ===")
"""

def upload_file_scp(local_path: Path, remote_dest: str, max_retries: int = 4, delay: float = 12.0) -> bool:
    """Envia arquivo via SCP com cooldown entre tentativas."""
    scp_cmd = [
        "scp", "-i", KEY,
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ConnectTimeout=15",
        str(local_path),
        f"{USER}@{HOST}:{remote_dest}"
    ]
    print(f"[SCP] Enviando {local_path.name} para {remote_dest}...", flush=True)
    for attempt in range(1, max_retries + 1):
        res = subprocess.run(scp_cmd, capture_output=True, text=True, timeout=45)
        if res.returncode == 0:
            print(f"[SCP OK] {local_path.name} transferido com sucesso na tentativa {attempt}.", flush=True)
            return True
        print(f"[SCP RETRY {attempt}/{max_retries}] Erro: {res.stderr.strip()[:80]}. Aguardando {delay}s...", flush=True)
        time.sleep(delay)
    return False

def run_remote_migration(max_retries: int = 5, delay: float = 12.0) -> bool:
    """Executa a migração transmitindo o script via STDIN para o Python remoto."""
    ssh_cmd = [
        "ssh", "-i", KEY,
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ConnectTimeout=15",
        "-o", "ServerAliveInterval=15",
        f"{USER}@{HOST}",
        "python3 -"
    ]
    print("[SSH] Disparando script de migração via STDIN...", flush=True)
    for attempt in range(1, max_retries + 1):
        try:
            res = subprocess.run(
                ssh_cmd,
                input=REMOTE_MIGRATION_SCRIPT,
                capture_output=True,
                text=True,
                timeout=120
            )
            print("\n--- SAÍDA DO SCRIPT DE MIGRAÇÃO ---")
            print(res.stdout)
            if res.stderr:
                print("--- STDERR ---")
                print(res.stderr)
            if res.returncode == 0:
                print(f"[SUCESSO] Migração executada com êxito na tentativa {attempt}!")
                return True
        except Exception as e:
            print(f"[SSH EXCEÇÃO] {e}", flush=True)
        print(f"[SSH RETRY {attempt}/{max_retries}] Aguardando {delay}s...", flush=True)
        time.sleep(delay)
    return False

def main():
    print("=" * 60)
    print("INICIANDO PROCESSO COMPLETO DE MIGRAÇÃO NO SERVIDOR")
    print("=" * 60)
    
    # 1. Garante que os pacotes locais estejam preparados
    essencial_tar = TEMP_DIR / "NeverLandEssencial.tar.gz"
    lighting_tar = TEMP_DIR / "AdvancedDynamicLighting_v6_BP.tar.gz"
    
    if not essencial_tar.exists() or not lighting_tar.exists():
        print("[ERRO] Arquivos tar.gz locais não encontrados.")
        sys.exit(1)
        
    # 2. Envia os arquivos tar caso ainda não estejam no servidor
    print("\n[PASSO 1] Sincronizando pacotes com o servidor...")
    if not upload_file_scp(essencial_tar, "/home/opc/minecraft/NeverLandEssencial.tar.gz"):
        sys.exit(1)
    time.sleep(5)
    if not upload_file_scp(lighting_tar, "/home/opc/minecraft/AdvancedDynamicLighting_v6_BP.tar.gz"):
        sys.exit(1)
    time.sleep(5)
    
    # 3. Executa a migração
    print("\n[PASSO 2] Executando migração no servidor...")
    ok = run_remote_migration()
    if not ok:
        print("[ERRO] A migração remota falhou.")
        sys.exit(1)

if __name__ == "__main__":
    main()
