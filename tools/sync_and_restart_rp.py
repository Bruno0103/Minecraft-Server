import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

print("=== [1/3] Sincronizando sorcerer.animation.json e manifests ===")
subprocess.run(["scp", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", r"addon\resource_pack\animations\sorcerer.animation.json", f"{USER}@{HOST}:/home/opc/minecraft/server/resource_packs/NeverLandCustomContent_RP/animations/sorcerer.animation.json"])
subprocess.run(["scp", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", r"addon\resource_pack\manifest.json", f"{USER}@{HOST}:/home/opc/minecraft/server/resource_packs/NeverLandCustomContent_RP/manifest.json"])
subprocess.run(["scp", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", r"addon\behavior_pack\manifest.json", f"{USER}@{HOST}:/home/opc/minecraft/server/behavior_packs/NeverLandCustomContent_BP/manifest.json"])

print("=== [2/3] Atualizando world_resource_packs.json para [1, 0, 2] ===")
remote_script = """
import json, os

w_rp_path = '/home/opc/minecraft/server/worlds/Never Land/world_resource_packs.json'
if os.path.exists(w_rp_path):
    w_rp = json.load(open(w_rp_path))
    for p in w_rp:
        if p.get('pack_id') == '9c12e847-5a33-4f19-a1b8-223344556677':
            p['version'] = [1, 0, 2]
    with open(w_rp_path, 'w', encoding='utf-8') as f:
        json.dump(w_rp, f, indent=2)
    print("world_resource_packs.json atualizado para 1.0.2")
"""
res2 = subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 -"], input=remote_script, text=True, capture_output=True)
print(res2.stdout)

print("=== [3/3] Reiniciando BDS com mc.py ===")
subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 /home/opc/minecraft/mc.py restart"], capture_output=True, text=True, timeout=30)
print("Servidor reiniciado com sucesso!")

