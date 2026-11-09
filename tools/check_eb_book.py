import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

remote_code = """
import os

p_eb = '/home/opc/minecraft/server/behavior_packs/EffortlessBuilding_BP/scripts/core/Items.js'
if not os.path.exists(p_eb):
    for root, dirs, files in os.walk('/home/opc/minecraft/server/behavior_packs/EffortlessBuilding_BP'):
        if 'Items.js' in files:
            p_eb = os.path.join(root, 'Items.js')
            break
print("Path:", p_eb)
for line in open(p_eb):
    if 'BOOK_ID' in line:
        print("EB:", line.strip())
"""

res = subprocess.run(
    ["ssh", "-i", KEY, "-o", "StrictHostKeyChecking=accept-new", f"{USER}@{HOST}", "python3 -"],
    input=remote_code,
    text=True,
    capture_output=True
)

print(res.stdout)
