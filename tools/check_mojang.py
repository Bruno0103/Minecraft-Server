import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
cmd = "curl -s -L -H 'User-Agent: Mozilla/5.0' https://net-secondary.web.minecraft-services.net/api/v1.0/download/links"
res = subprocess.run(["ssh", "-i", KEY, "opc@157.151.2.91", cmd], capture_output=True, text=True)
print(res.stdout)

