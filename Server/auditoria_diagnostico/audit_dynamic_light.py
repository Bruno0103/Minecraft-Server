# ==============================================================================
# Script de Auditoria Completa de Dynamic Light no Servidor Remoto
# ==============================================================================

from remote_ops import exec_ssh

REMOTE_SCRIPT = r"""python3 -c "
import os

print('--- PESQUISA DE ARQUIVOS COM dynamic OU darklex ---')
for root, dirs, files in os.walk('/home/opc/minecraft/server'):
    for f in files:
        if 'dynamic' in f.lower() or 'darklex' in f.lower():
            print('FILE:', os.path.join(root, f))
    for d in dirs:
        if 'dynamic' in d.lower() or 'darklex' in d.lower():
            print('DIR:', os.path.join(root, d))
" """

def run():
    code, out, err = exec_ssh(REMOTE_SCRIPT, max_attempts=5, delay=4.0)
    print("CODE:", code)
    print("STDOUT:\n", out)
    if err:
        print("STDERR:\n", err)

if __name__ == "__main__":
    run()
