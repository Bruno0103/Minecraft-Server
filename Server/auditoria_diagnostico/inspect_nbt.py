# ==============================================================================
# Script de Inspeção de Tags NBT em level.dat usando remote_ops
# ==============================================================================

from remote_ops import exec_ssh

REMOTE_SCRIPT = r"""python3 -c "
import os

path = '/home/opc/minecraft/server/worlds/Never Land/level.dat'
with open(path, 'rb') as f:
    data = f.read()

pos = 0
found = False
while True:
    pos = data.lower().find(b'playersleeping', pos)
    if pos == -1:
        break
    found = True
    snippet = data[max(0, pos-10):min(len(data), pos+40)]
    print(f'Encontrado em {pos}: {repr(snippet)}')
    pos += 1

if not found:
    print('Tag playersleeping nao encontrada em level.dat')
" """

def run():
    code, out, err = exec_ssh(REMOTE_SCRIPT, max_attempts=5, delay=4.0)
    print("CODE:", code)
    print("STDOUT:\n", out)
    if err:
        print("STDERR:\n", err)

if __name__ == "__main__":
    run()
