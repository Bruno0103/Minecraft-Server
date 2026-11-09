# ==============================================================================
# Script de Inspeção Consolidada com Mecanismo Resiliente de Retry
# ==============================================================================
# Tenta estabelecer a conexão SSH com intervalo de backoff para evitar
# o erro 'kex_exchange_identification: read: Connection reset'.
# ==============================================================================

import subprocess
import time
import sys

KEY = r"Server\VM\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

BASH_COMMAND = (
    "echo '=== PROPERTIES ==='; "
    "grep -E 'level-name|gamemode|allow-cheats' /home/opc/minecraft/server/server.properties; "
    "echo '=== WORLDS ==='; "
    "ls -la /home/opc/minecraft/server/worlds; "
    "echo '=== WORLD RESOURCE PACKS ==='; "
    "cat /home/opc/minecraft/server/worlds/*/world_resource_packs.json; "
    "echo '=== WORLD BEHAVIOR PACKS ==='; "
    "cat /home/opc/minecraft/server/worlds/*/world_behavior_packs.json; "
    "echo '=== RESOURCE PACKS DIR ==='; "
    "ls -l /home/opc/minecraft/server/resource_packs; "
    "echo '=== BEHAVIOR PACKS DIR ==='; "
    "ls -l /home/opc/minecraft/server/behavior_packs"
)

def run_with_retry():
    cmd = [
        "ssh", "-i", KEY,
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ConnectTimeout=15",
        "-o", "ServerAliveInterval=15",
        f"{USER}@{HOST}",
        BASH_COMMAND
    ]
    for attempt in range(1, 6):
        print(f"[Tentativa {attempt}/5] Conectando ao servidor...")
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if res.returncode == 0:
            print("[SUCESSO] Dados coletados do servidor:\n")
            print(res.stdout)
            return True
        print(f"[Falha {attempt}] Retorno: {res.returncode}. Stderr: {res.stderr.strip()}")
        time.sleep(5)
    return False

if __name__ == "__main__":
    success = run_with_retry()
    if not success:
        sys.exit(1)
