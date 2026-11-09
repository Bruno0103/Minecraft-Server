import subprocess

KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

script = """
props = '/home/opc/minecraft/server/server.properties'
with open(props, 'r', encoding='utf-8') as f:
    content = f.read()

# Garante que transport=nethernet e server-udp-ports estejam presentes
if 'transport=' not in content:
    content = content.replace('server-port=19132', 'server-port=19132\\nserver-udp-ports=157.151.2.91:19132:19132\\ntransport=nethernet')

with open(props, 'w', encoding='utf-8') as f:
    f.write(content)

print("server.properties atualizado com NetherNet:")
for line in open(props):
    if 'server-port' in line or 'transport' in line or 'server-udp-ports' in line:
        print("  ", line.strip())
"""

res = subprocess.run(["ssh", "-i", KEY, f"{USER}@{HOST}", "python3 -"], input=script, text=True, capture_output=True)
print(res.stdout)
if res.stderr:
    print("STDERR:", res.stderr)

