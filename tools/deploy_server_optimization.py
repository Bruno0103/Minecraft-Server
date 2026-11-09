import os
import tarfile
import subprocess
import time
from pathlib import Path

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"
ROOT_DIR = Path(__file__).parent.parent

BP_SRC = ROOT_DIR / "addon" / "behavior_pack"
RP_SRC = ROOT_DIR / "addon" / "resource_pack"
TEMP_DEPLOY = ROOT_DIR / "Server" / "scripts_automacao" / "temp_deploy"

def run_ssh(cmd, input_text=None, timeout=60):
    ssh_cmd = [
        "ssh", "-i", KEY,
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ConnectTimeout=15",
        f"{USER}@{HOST}",
        cmd
    ]
    return subprocess.run(ssh_cmd, input=input_text, text=True, capture_output=True, timeout=timeout)

def run_scp(local_file, remote_dest, timeout=90):
    scp_cmd = [
        "scp", "-i", KEY,
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ConnectTimeout=15",
        str(local_file),
        f"{USER}@{HOST}:{remote_dest}"
    ]
    return subprocess.run(scp_cmd, text=True, capture_output=True, timeout=timeout)

def main():
    print("=== [1/6] Empacotando Never Land Custom Content (BP e RP) ===")
    TEMP_DEPLOY.mkdir(parents=True, exist_ok=True)
    
    bp_tar = TEMP_DEPLOY / "NeverLandCustomContent_BP.tar.gz"
    with tarfile.open(bp_tar, "w:gz") as tar:
        tar.add(BP_SRC, arcname="NeverLandCustomContent_BP")
    print(f"  -> BP empacotado: {bp_tar} ({bp_tar.stat().st_size} bytes)")

    rp_tar = TEMP_DEPLOY / "NeverLandCustomContent_RP.tar.gz"
    with tarfile.open(rp_tar, "w:gz") as tar:
        tar.add(RP_SRC, arcname="NeverLandCustomContent_RP")
    print(f"  -> RP empacotado: {rp_tar} ({rp_tar.stat().st_size} bytes)")

    print("\n=== [2/6] Enviando pacotes via SCP para a VM OCI ===")
    res1 = run_scp(bp_tar, "/home/opc/minecraft/NeverLandCustomContent_BP.tar.gz")
    if res1.returncode != 0:
        print("Erro SCP BP:", res1.stderr)
        return
    print("  -> BP enviado com sucesso!")

    res2 = run_scp(rp_tar, "/home/opc/minecraft/NeverLandCustomContent_RP.tar.gz")
    if res2.returncode != 0:
        print("Erro SCP RP:", res2.stderr)
        return
    print("  -> RP enviado com sucesso!")

    print("\n=== [3/6] Executando Migração, Extração e Otimização Remota ===")
    remote_script = """
import os, sys, json, tarfile, shutil, subprocess, time

SERVER = "/home/opc/minecraft/server"
BP_DIR = os.path.join(SERVER, "behavior_packs")
RP_DIR = os.path.join(SERVER, "resource_packs")
WORLD_DIR = os.path.join(SERVER, "worlds", "Never Land")
W_BP_JSON = os.path.join(WORLD_DIR, "world_behavior_packs.json")
W_RP_JSON = os.path.join(WORLD_DIR, "world_resource_packs.json")

def log(msg):
    print(f"[DEPLOY OCI] {msg}", flush=True)

# 1. Extração dos pacotes do Never Land Custom Content
bp_tar = "/home/opc/minecraft/NeverLandCustomContent_BP.tar.gz"
if os.path.exists(bp_tar):
    with tarfile.open(bp_tar, "r:gz") as tar:
        tar.extractall(BP_DIR)
    log("NeverLandCustomContent_BP extraído em behavior_packs.")
    os.remove(bp_tar)

rp_tar = "/home/opc/minecraft/NeverLandCustomContent_RP.tar.gz"
if os.path.exists(rp_tar):
    with tarfile.open(rp_tar, "r:gz") as tar:
        tar.extractall(RP_DIR)
    log("NeverLandCustomContent_RP extraído em resource_packs.")
    os.remove(rp_tar)

# 2. Registro em world_behavior_packs.json
bp_manifest = os.path.join(BP_DIR, "NeverLandCustomContent_BP", "manifest.json")
if os.path.exists(bp_manifest):
    bp_data = json.load(open(bp_manifest))
    bp_uuid = bp_data["header"]["uuid"]
    bp_ver = bp_data["header"]["version"]
    
    w_bp = json.load(open(W_BP_JSON)) if os.path.exists(W_BP_JSON) else []
    found = False
    for entry in w_bp:
        if entry.get("pack_id") == bp_uuid:
            entry["version"] = bp_ver
            found = True
            break
    if not found:
        # Registra no topo para ter prioridade máxima de intercepção beforeEvents
        w_bp.insert(0, {"pack_id": bp_uuid, "version": bp_ver})
        log(f"Registrado BP '{bp_uuid}' no topo de world_behavior_packs.json!")
    else:
        log(f"Atualizado BP '{bp_uuid}' em world_behavior_packs.json.")
    with open(W_BP_JSON, "w", encoding="utf-8") as f:
        json.dump(w_bp, f, indent=2)

# 3. Registro em world_resource_packs.json
rp_manifest = os.path.join(RP_DIR, "NeverLandCustomContent_RP", "manifest.json")
if os.path.exists(rp_manifest):
    rp_data = json.load(open(rp_manifest))
    rp_uuid = rp_data["header"]["uuid"]
    rp_ver = rp_data["header"]["version"]
    
    w_rp = json.load(open(W_RP_JSON)) if os.path.exists(W_RP_JSON) else []
    found = False
    for entry in w_rp:
        if entry.get("pack_id") == rp_uuid:
            entry["version"] = rp_ver
            found = True
            break
    if not found:
        w_rp.append({"pack_id": rp_uuid, "version": rp_ver})
        log(f"Registrado RP '{rp_uuid}' em world_resource_packs.json!")
    with open(W_RP_JSON, "w", encoding="utf-8") as f:
        json.dump(w_rp, f, indent=2)

# 4. Otimização Cirúrgica de Cooldown nos Menus Flutuantes Existentes (4 -> 10 ticks)
eb_panel = os.path.join(BP_DIR, "EffortlessBuilding_BP", "scripts", "core", "EntityPanel.js")
if os.path.exists(eb_panel):
    content = open(eb_panel, "r", encoding="utf-8").read()
    if "const CLICK_COOLDOWN = 4;" in content:
        shutil.copy(eb_panel, eb_panel + ".bak")
        content = content.replace("const CLICK_COOLDOWN = 4;", "const CLICK_COOLDOWN = 10; // Otimizado: 500ms anti-stutter")
        open(eb_panel, "w", encoding="utf-8").write(content)
        log("Otimizado CLICK_COOLDOWN para 10 ticks em EffortlessBuilding_BP (EntityPanel.js)!")

mm_panel = os.path.join(BP_DIR, "AdvancedMinimap_BP", "scripts", "panels.js")
if os.path.exists(mm_panel):
    content = open(mm_panel, "r", encoding="utf-8").read()
    if "const CLICK_COOLDOWN = 4;" in content:
        shutil.copy(mm_panel, mm_panel + ".bak")
        content = content.replace("const CLICK_COOLDOWN = 4;", "const CLICK_COOLDOWN = 10; // Otimizado: 500ms anti-stutter")
        open(mm_panel, "w", encoding="utf-8").write(content)
        log("Otimizado CLICK_COOLDOWN para 10 ticks em AdvancedMinimap_BP (panels.js)!")

# 5. Correção de Permissões
subprocess.run(["chown", "-R", "opc:opc", SERVER])
log("Permissões de arquivos opc:opc atualizadas.")
"""
    res3 = run_ssh("python3 -", input_text=remote_script)
    print(res3.stdout)
    if res3.stderr:
        print("STDERR:", res3.stderr)

    print("\n=== [4/6] Reiniciando Servidor BDS com mc.py ===")
    res4 = run_ssh("python3 /home/opc/minecraft/mc.py restart")
    print(res4.stdout)

    print("\n=== [5/6] Aguardando estabilização do serviço (5s)... ===")
    time.sleep(5)

    print("\n=== [6/6] Verificando Status Operacional do Servidor ===")
    res5 = run_ssh("python3 /home/opc/minecraft/mc.py status")
    print(res5.stdout)

    print("\n=== Diagnóstico de Logs Recentes do BDS ===")
    res6 = run_ssh("journalctl -u bedrock.service -n 20 --no-pager")
    print(res6.stdout)

if __name__ == "__main__":
    main()
