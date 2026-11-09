import subprocess
import time

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

print("=== [1/4] Sincronizando espada_rubi.json corrigido ===")
scp_cmd = [
    "scp", "-i", KEY,
    "-o", "StrictHostKeyChecking=accept-new",
    r"addon\behavior_pack\items\espada_rubi.json",
    f"{USER}@{HOST}:/home/opc/minecraft/server/behavior_packs/NeverLandCustomContent_BP/items/espada_rubi.json"
]
res1 = subprocess.run(scp_cmd, capture_output=True, text=True)
print("SCP Ret:", res1.returncode)

print("\n=== [2/4] Removendo transport=nethernet e server-udp-ports do server.properties ===")
clean_script = """
props_path = '/home/opc/minecraft/server/server.properties'
with open(props_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.strip().startswith('transport=') or line.strip().startswith('server-udp-ports='):
        print("Removida diretiva conflitante:", line.strip())
        continue
    new_lines.append(line)

with open(props_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print("server.properties limpo com sucesso!")
"""

res2 = subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 -"], input=clean_script, text=True, capture_output=True)
print(res2.stdout)

print("\n=== [3/4] Reiniciando bedrock.service com mc.py ===")
res3 = subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 /home/opc/minecraft/mc.py restart"], capture_output=True, text=True)
print(res3.stdout)

print("\n=== [4/4] Aguardando inicializacao e checando socket UDP 19132 ===")
time.sleep(6)

check_script = """
import subprocess

print("=== PORTAS UDP EM ESCUTA ===")
res_ss = subprocess.run(['sudo', 'ss', '-ulnp'], capture_output=True, text=True)
for line in res_ss.stdout.splitlines():
    if '19132' in line or 'bedrock' in line:
        print(line)

print("\n=== ULTIMAS LINHAS DO JOURNAL ===")
res_j = subprocess.run(['journalctl', '-u', 'bedrock.service', '-n', '25', '--no-pager'], capture_output=True, text=True)
for line in res_j.stdout.splitlines():
    if 'Accepting' in line or 'started' in line or 'Error' in line or 'ERROR' in line:
        print(line)
"""

res4 = subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 -"], input=check_script, text=True, capture_output=True)
print(res4.stdout)

