# ==============================================================================
# Script de Auditoria e Validação Final Pós-Migração
# ==============================================================================
# Inspeciona:
# 1. Status do serviço BDS (systemctl status / mc status)
# 2. Conteúdo de world_resource_packs.json (ordem e presença do Essencial em último)
# 3. Conteúdo de world_behavior_packs.json (Advanced Dynamic Lighting v6 ativo)
# 4. Valor da gamerule playersSleepingPercentage
# ==============================================================================

import subprocess
import time
import sys
import json

KEY = r"Server\VM\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

REMOTE_AUDIT_SCRIPT = r"""
import json
import os
import subprocess

print("=== 1. STATUS OPERACIONAL DO SERVIDOR ===")
subprocess.run(["python3", "/home/opc/minecraft/mc.py", "status"])

print("\n=== 2. AUDITORIA DE WORLD_RESOURCE_PACKS.JSON ===")
rp_file = "/home/opc/minecraft/server/worlds/Never Land/world_resource_packs.json"
with open(rp_file, "r", encoding="utf-8") as f:
    rp_data = json.load(f)

for idx, p in enumerate(rp_data):
    is_last = (idx == len(rp_data) - 1)
    status_str = "[ÚLTIMA PRIORIDADE]" if is_last else f"[Prioridade {idx+1}]"
    print(f"  {status_str} Pack ID: {p.get('pack_id')}, Versão: {p.get('version')}")

last_pack = rp_data[-1]
assert last_pack.get("pack_id") == "e547c6e6-d6d3-4b21-8a95-a2b013fc1669", "Never Land Essencial NÃO é o último pacote!"
print("\n[OK] Never Land Essencial está posicionado como ÚLTIMA prioridade!")

print("\n=== 3. AUDITORIA DE WORLD_BEHAVIOR_PACKS.JSON ===")
bp_file = "/home/opc/minecraft/server/worlds/Never Land/world_behavior_packs.json"
with open(bp_file, "r", encoding="utf-8") as f:
    bp_data = json.load(f)

lighting_found = False
for idx, p in enumerate(bp_data):
    if p.get("pack_id") == "d0f07809-c63f-4c28-bfbf-43488b4010a2":
        lighting_found = True
        print(f"  [ENCONTRADO] Advanced Dynamic Lighting v6: {p}")
    else:
        print(f"  Behavior Pack: {p.get('pack_id')}")

assert lighting_found, "Advanced Dynamic Lighting v6 NÃO encontrado em world_behavior_packs.json!"
print("\n[OK] Advanced Dynamic Lighting v6 está ativo no mundo!")

print("\n=== 4. AUDITORIA DA REGRA DE SONO (ONE PLAYER SLEEP) ===")
subprocess.run(["python3", "/home/opc/minecraft/mc.py", "cmd", "gamerule playersSleepingPercentage"])
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
    max_attempts = 5
    delay = 15.0
    
    for attempt in range(1, max_attempts + 1):
        print(f"[Auditoria Tentativa {attempt}/{max_attempts}] Conectando ao servidor...", flush=True)
        try:
            res = subprocess.run(
                ssh_cmd,
                input=REMOTE_AUDIT_SCRIPT,
                capture_output=True,
                text=True,
                timeout=60
            )
            if res.returncode == 0:
                print("\n============================================================")
                print("RELATÓRIO DE AUDITORIA FINAL")
                print("============================================================")
                print(res.stdout)
                return True
            else:
                print(f"Tentativa {attempt} falhou ({res.stderr.strip()[:80]})")
        except Exception as e:
            print(f"Exceção {attempt}: {e}")
            
        time.sleep(delay)
        
    print("[ERRO] Auditoria não pôde ser completada.")
    sys.exit(1)

if __name__ == "__main__":
    main()
