# ==============================================================================
# Script de Mapeamento Completo de Pacotes no Servidor Remoto
# ==============================================================================
# Inspeciona todos os diretórios em resource_packs e behavior_packs do servidor,
# lendo seus manifest.json para identificar todos os UUIDs, nomes e versões.
# ==============================================================================

import subprocess
import json
import time

KEY = r"Server\VM\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

REMOTE_SCRIPT = r"""python3 -c "
import json, os, glob

def check_dir(base_dir):
    print('=== BASE DIR:', base_dir, '===')
    for d in sorted(os.listdir(base_dir)):
        p = os.path.join(base_dir, d)
        if not os.path.isdir(p) or d.startswith('chemistry') or d.startswith('vanilla') or d.startswith('editor') or d.startswith('server_') or d.startswith('experimental_'):
            continue
        mf = os.path.join(p, 'manifest.json')
        if os.path.exists(mf):
            try:
                with open(mf, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                hdr = data.get('header', {})
                print(f'{d} -> Name: {hdr.get(\"name\")}, UUID: {hdr.get(\"uuid\")}, Ver: {hdr.get(\"version\")}')
            except Exception as e:
                print(f'{d} -> Error: {e}')
        else:
            print(f'{d} -> NO manifest.json')

check_dir('/home/opc/minecraft/server/resource_packs')
check_dir('/home/opc/minecraft/server/behavior_packs')
" """

def run():
    cmd = [
        "ssh", "-i", KEY,
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ConnectTimeout=15",
        "-o", "ServerAliveInterval=15",
        f"{USER}@{HOST}",
        REMOTE_SCRIPT
    ]
    for attempt in range(1, 6):
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if res.returncode == 0:
            print(res.stdout)
            return
        time.sleep(3)
    print("FAILED:", res.stderr)

if __name__ == "__main__":
    run()
