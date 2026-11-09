import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

print("=== [1/2] Executando 'mc update' na VM OCI para atualizar BDS para 1.26.63.1 ===")
res = subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 /home/opc/minecraft/mc.py update"], capture_output=True, text=True, timeout=120)
print(res.stdout)
if res.stderr:
    print("STDERR:", res.stderr)

print("\n=== [2/2] Verificando versão e status após atualização ===")
res2 = subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 /home/opc/minecraft/mc.py status; journalctl -u bedrock.service -n 15 --no-pager"], capture_output=True, text=True, timeout=30)
print(res2.stdout)

