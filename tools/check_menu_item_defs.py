import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

remote_code = """
import os

p_mm = '/home/opc/minecraft/server/behavior_packs/AdvancedMinimap_BP/scripts/main.js'
for line in open(p_mm):
    if 'MENU_ITEM' in line:
        print("MM:", line.strip())

p_eb = '/home/opc/minecraft/server/behavior_packs/EffortlessBuilding_BP/scripts/Items.js'
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
