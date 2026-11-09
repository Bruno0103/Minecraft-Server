import subprocess
import time

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

print("=== [1/2] Reiniciando serviço BDS ===")
subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 /home/opc/minecraft/mc.py restart"], capture_output=True, text=True, timeout=30)

print("=== [2/2] Aguardando estabilização e checando logs ===")
time.sleep(5)
res = subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "journalctl -u bedrock.service -n 25 --no-pager"], capture_output=True, text=True)
print(res.stdout)

