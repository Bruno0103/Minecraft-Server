import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

remote_code = """
import os, json

server = '/home/opc/minecraft/server'
bp_dir = os.path.join(server, 'behavior_packs')
packs = ['AdvancedDynamicLighting_v6_BP', 'AdvancedMinimap_BP', 'AutoReplant_BP', 'EffortlessBuilding_BP', 'HumoDeFogata_BP', 'PainelF3_BP', 'VeinMiner_BP']

for p in packs:
    m_path = os.path.join(bp_dir, p, 'manifest.json')
    print(f"=== {p} ===")
    if os.path.exists(m_path):
        try:
            with open(m_path, 'r', encoding='utf-8', errors='ignore') as f:
                data = json.load(f)
                print("  Name:", data.get('header', {}).get('name'))
                print("  UUID:", data.get('header', {}).get('uuid'))
                print("  Modules:", [m.get('type') for m in data.get('modules', [])])
        except Exception as e:
            print("  Erro lendo manifest:", e)
    s_dir = os.path.join(bp_dir, p, 'scripts')
    if os.path.exists(s_dir):
        print("  Scripts:", os.listdir(s_dir))
"""

res = subprocess.run(
    ["ssh", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", f"{USER}@{HOST}", "python3 -"],
    input=remote_code,
    text=True,
    capture_output=True
)

print(res.stdout)
if res.stderr:
    print("STDERR:", res.stderr)
