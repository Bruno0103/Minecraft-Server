#!/usr/bin/env bash
# =============================================================================
# /home/opc/minecraft/bedrock_stop.sh
# -----------------------------------------------------------------------------
# DESLIGAMENTO GRACIOSO DO BEDROCK DEDICATED SERVER
#
# Chamado pelo systemd (ExecStop= do bedrock.service) sempre que o serviço é
# parado: "systemctl stop/restart bedrock", reboot ou shutdown da VM.
#
# O que faz:
#   1. Confere se o processo do servidor ainda está vivo.
#   2. Envia o comando "stop" pelo console FIFO -> o BDS salva o mundo e sai.
#   3. Espera até 50 segundos o processo terminar.
#   Se o tempo acabar, o systemd envia SIGINT e, por fim, SIGKILL.
#
# Argumento:
#   $1 = PID principal do servidor (o systemd passa ${MAINPID}).
# =============================================================================

# PID recebido do systemd (pode vir vazio se o servidor já tiver morrido)
PID="${1:-}"

# Caminho do FIFO criado pelo bedrock.socket
FIFO="/run/bedrock.console"

# -----------------------------------------------------------------------------
# Proteção importante: se o servidor JÁ ESTÁ MORTO (ex.: crash), NÃO escrevemos
# "stop" no FIFO. O systemd mantém o FIFO aberto, então o texto ficaria parado
# no buffer e seria lido pela PRÓXIMA instância, que desligaria logo ao subir.
# -----------------------------------------------------------------------------
if [ -z "$PID" ] || ! kill -0 "$PID" 2>/dev/null; then
    exit 0
fi

# Só escreve se o FIFO existir de fato (evita criar um arquivo comum por engano)
if [ -p "$FIFO" ]; then
    echo "stop" > "$FIFO"
fi

# Aguarda o processo encerrar (salvamento do mundo LevelDB) por até 50 s
for _ in $(seq 1 50); do
    if ! kill -0 "$PID" 2>/dev/null; then
        exit 0   # Encerrou limpo
    fi
    sleep 1
done

# Tempo esgotado: devolve o controle ao systemd (que enviará SIGINT/SIGKILL)
exit 0

