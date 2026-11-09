import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

remote_code = """
import os, json

server = '/home/opc/minecraft/server'
rp_dir = os.path.join(server, 'resource_packs')
world_rp = os.path.join(server, 'worlds', 'Never Land', 'world_resource_packs.json')

print("=== WORLD RESOURCE PACKS ===")
if os.path.exists(world_rp):
    print(open(world_rp).read())

print("=== RESOURCE PACKS DIRS ===")
for d in sorted(os.listdir(rp_dir)):
    if not (d.startswith('chemistry') or d.startswith('vanilla') or d.startswith('editor') or d.startswith('server_') or d.startswith('experimental_')):
        p = os.path.join(rp_dir, d, 'manifest.json')
        if os.path.exists(p):
            data = json.load(open(p, encoding='utf-8', errors='ignore'))
            print(f"{d} -> {data.get('header', {}).get('name')} (UUID: {data.get('header', {}).get('uuid')})")
        else:
            print(f"{d} (sem manifest)")
"""

res = subprocess.run(
    ["ssh", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", f"{USER}@{HOST}", "python3 -"],
    input=remote_code,
    text=True,
    capture_output=True
)

print(res.stdout)
