import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

remote_code = """
import os

server = '/home/opc/minecraft/server/behavior_packs'
for bp in ['EffortlessBuilding_BP', 'AdvancedMinimap_BP', 'VeinMiner_BP', 'PainelF3_BP']:
    for root, dirs, files in os.walk(os.path.join(server, bp)):
        for f in files:
            if f.endswith('.js'):
                path = os.path.join(root, f)
                for line in open(path, 'r', encoding='utf-8', errors='ignore'):
                    if 'itemUse' in line or 'itemStack' in line or 'typeId ===' in line or 'typeId.includes' in line:
                        print(f"{bp}/{f}: {line.strip()[:100]}")
"""

res = subprocess.run(
    ["ssh", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", f"{USER}@{HOST}", "python3 -"],
    input=remote_code,
    text=True,
    capture_output=True
)

print(res.stdout)
