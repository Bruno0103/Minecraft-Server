# ==============================================================================
# Módulo Utilitário de Operações Remotas com Cooldown para Oracle Linux
# ==============================================================================
# sshd no Oracle Linux possui limitação de conexões simultâneas/rápidas
# (MaxStartups / fail2ban rate limit). Este módulo aguarda um intervalo
# seguro de 12 segundos entre tentativas para garantir handshake limpo.
# ==============================================================================

import subprocess
import time
import sys
from typing import Tuple

KEY = r"Server\VM\SSH keys\ssh-key-2026-11-03.key"
HOST = "157.151.2.91"
USER = "opc"

def exec_ssh(remote_cmd: str, max_attempts: int = 5, delay: float = 12.0, timeout: float = 90.0) -> Tuple[int, str, str]:
    """
    Executa comando remoto via OpenSSH com delay de cooldown entre tentativas.
    """
    ssh_args = [
        "ssh", "-i", KEY,
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "-o", "ConnectTimeout=15",
        "-o", "ServerAliveInterval=15",
        f"{USER}@{HOST}",
        remote_cmd
    ]
    
    last_code = 1
    last_stdout = ""
    last_stderr = ""
    
    for attempt in range(1, max_attempts + 1):
        try:
            print(f"[SSH {attempt}/{max_attempts}] Conectando...", flush=True)
            res = subprocess.run(ssh_args, capture_output=True, text=True, timeout=timeout)
            last_code = res.returncode
            last_stdout = res.stdout
            last_stderr = res.stderr
            if res.returncode == 0:
                print(f"[SSH OK] Conexão bem-sucedida na tentativa {attempt}.", flush=True)
                return (last_code, last_stdout, last_stderr)
            print(f"[SSH AVISO] Tentativa {attempt} falhou ({res.stderr.strip()[:80]}). Aguardando {delay}s...", flush=True)
        except Exception as e:
            last_stderr = str(e)
            last_code = 255
            print(f"[SSH EXCEÇÃO] {e}. Aguardando {delay}s...", flush=True)
            
        time.sleep(delay)
        
    return (last_code, last_stdout, last_stderr)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        cmd_to_run = " ".join(sys.argv[1:])
        code, out, err = exec_ssh(cmd_to_run)
        print("CODE:", code)
        print("STDOUT:\n", out)
        if err:
            print("STDERR:\n", err)
    else:
        print("Uso: python remote_ops.py <comando_remoto>")
