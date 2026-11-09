# ==============================================================================
# Script de Verificação de Arquivos e Inbox no Servidor Remoto
# ==============================================================================
# Verifica a caixa de entrada de addons (/home/opc/minecraft/addons_inbox) e
# a pasta de downloads para entender o histórico recente de pacotes instalados.
# ==============================================================================

import subprocess
import time

KEY = r"Server\VM\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

REMOTE_SCRIPT = r"""bash -c "
echo '=== ADDONS INBOX ==='
ls -la /home/opc/minecraft/addons_inbox
echo '=== DOWNLOADS ==='
ls -la /home/opc/minecraft/downloads
echo '=== FILES IN AdvancedDynamicLighting_v6_BP ==='
ls -la /home/opc/minecraft/server/behavior_packs/AdvancedDynamicLighting_v6_BP
ls -la /home/opc/minecraft/server/behavior_packs/AdvancedDynamicLighting_v6_BP/scripts
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
