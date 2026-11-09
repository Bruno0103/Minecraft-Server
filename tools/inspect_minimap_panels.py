import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

remote_code = """
p = '/home/opc/minecraft/server/behavior_packs/AdvancedMinimap_BP/scripts/panels.js'
print(open(p, 'r', encoding='utf-8', errors='ignore').read()[:2000])
"""

res = subprocess.run(
    ["ssh", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", f"{USER}@{HOST}", "python3 -"],
    input=remote_code,
    text=True,
    capture_output=True
)

print(res.stdout)
