import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

remote_code = """
import os, glob

server = '/home/opc/minecraft/server'
bp_dir = os.path.join(server, 'behavior_packs')

print("=== BUSCANDO MENU / FORMS NOS SCRIPTS DO SERVIDOR ===")
for root, dirs, files in os.walk(bp_dir):
    for f in files:
        if f.endswith('.js') or f.endswith('.ts'):
            fp = os.path.join(root, f)
            rel = os.path.relpath(fp, bp_dir)
            try:
                content = open(fp, 'r', encoding='utf-8', errors='ignore').read()
                matches = []
                if 'FormData' in content: matches.append('FormData')
                if 'itemUse' in content: matches.append('itemUse')
                if 'playerInteract' in content: matches.append('playerInteract')
                if 'beforeEvents' in content: matches.append('beforeEvents')
                if matches:
                    print(f"{rel}: {matches}")
            except Exception as e:
                pass
"""

res = subprocess.run(
    ["ssh", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", f"{USER}@{HOST}", "python3 -"],
    input=remote_code,
    text=True,
    capture_output=True
)

print(res.stdout)
