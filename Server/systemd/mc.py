#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
===============================================================================
mc.py - Utilitário Interativo e CLI de Controle do Servidor Minecraft Bedrock
===============================================================================
Projeto: Servidor Minecraft Bedrock "Never Land" (Oracle Cloud Infrastructure)
Ambiente: Oracle Linux Server 9.7 UEK (VM.Standard.E2.1.Micro)
Linguagem: Python 3 nativo (exclusivamente biblioteca padrão, zero dependências)

Este utilitário atua como a interface unificada de administração via SSH,
permitindo ao operador gerenciar o serviço systemd, monitorar métricas de
desempenho (RAM, Swap, CPU), auditar inicialização automática no boot da VM,
executar backups, acompanhar logs e interagir diretamente com o console do
motor Bedrock Dedicated Server (BDS) através do named pipe FIFO (/run/bedrock.console)
e do leitor de eventos do systemd journald.

Funcionalidades Principais:
  1. Modo CLI Direto: Execução ágil de comandos via terminal ou scripts
     Exemplos: mc status, mc list, mc say "Olá", mc kick Steve, mc backup
  2. Modo Interativo (Dashboard/Menu & REPL): Acionado ao rodar 'mc' sem
     argumentos em terminal TTY interativo via SSH.
  3. Sessão de Console REPL: Terminal bidirecional contínuo com o BDS.
  4. Comunicação Segura com FIFO: Abertura não-bloqueante (O_NONBLOCK | O_WRONLY)
     com captura preventiva de ENXIO e concorrência protegida por atomicidade POSIX.
  5. Sincronismo de Respostas: Captura de cursor no journalctl para leitura
     precisa de respostas e despacho assíncrono para comandos de broadcast (say).

Autor: Equipe de Engenharia Never Land
===============================================================================
"""

import os
import sys
import stat
import time
import errno
import signal
import shutil
import subprocess
import argparse
from typing import Dict, List, Optional, Tuple

# =============================================================================
# 1. CONSTANTES E CONFIGURAÇÕES DO AMBIENTE
# =============================================================================

# Identificadores das unidades do systemd
NOME_SERVICO = "bedrock.service"
NOME_SOCKET = "bedrock.socket"

# Caminho do named pipe (FIFO) criado pela unit bedrock.socket
# O processo bedrock_server herda este FIFO como seu stdin
CAMINHO_FIFO = "/run/bedrock.console"

# Diretórios operacionais do usuário opc na VM
DIRETORIO_BASE = "/home/opc/minecraft"
DIRETORIO_SERVIDOR = os.path.join(DIRETORIO_BASE, "server")
DIRETORIO_BACKUPS = os.path.join(DIRETORIO_BASE, "backups")

# Scripts operacionais auxiliares instalados na VM
SCRIPT_PARADA = os.path.join(DIRETORIO_BASE, "bedrock_stop.sh")
SCRIPT_BACKUP = os.path.join(DIRETORIO_BASE, "backup.sh")
SCRIPT_ATUALIZACAO = os.path.join(DIRETORIO_BASE, "install_or_update.sh")
ARQUIVO_PROPRIEDADES = os.path.join(DIRETORIO_SERVIDOR, "server.properties")

# Limites de memória RAM estabelecidos no systemd para a VM de 1 GB
LIMITE_MEMORIA_ALERTA_MB = 600
LIMITE_MEMORIA_MAXIMA_MB = 750


# =============================================================================
# 2. SISTEMA DE CORES ANSI PARA TERMINAL
# =============================================================================

class Cores:
    """
    Gerenciador de paleta de cores e formatação de texto ANSI para o terminal.
    Desativa automaticamente todas as sequências de escape caso a saída padrão
    não esteja conectada a um terminal interativo (ex.: redirecionamento ou pipes).
    """
    VERDE = "\033[1;32m"
    VERMELHO = "\033[1;31m"
    AMARELO = "\033[1;33m"
    AZUL = "\033[1;34m"
    CIANO = "\033[1;36m"
    MAGENTA = "\033[1;35m"
    BRANCO = "\033[1;37m"
    CINZA = "\033[0;90m"
    NEGRITO = "\033[1m"
    SUBLINHADO = "\033[4m"
    FIM = "\033[0m"

    @classmethod
    def desativar(cls) -> None:
        """Remove todos os códigos de formatação ANSI para saídas puras."""
        cls.VERDE = ""
        cls.VERMELHO = ""
        cls.AMARELO = ""
        cls.AZUL = ""
        cls.CIANO = ""
        cls.MAGENTA = ""
        cls.BRANCO = ""
        cls.CINZA = ""
        cls.NEGRITO = ""
        cls.SUBLINHADO = ""
        cls.FIM = ""


# Se a execução não for em um terminal interativo (TTY), removemos as cores ANSI
if not sys.stdout.isatty():
    Cores.desativar()


# =============================================================================
# 3. CAMADA DE EXECUÇÃO DE COMANDOS DO SISTEMA OPERACIONAL
# =============================================================================

def executar_comando(
    cmd: List[str],
    usar_sudo: bool = False,
    capturar_saida: bool = True,
    timeout_segundos: Optional[float] = 15.0
) -> subprocess.CompletedProcess:
    """
    Executa um comando do sistema operacional de forma controlada via subprocess.

    Parâmetros:
      cmd: Lista contendo o binário e seus argumentos.
      usar_sudo: Se True, prefixa o comando com 'sudo' para execução privilegiada.
      capturar_saida: Se True, intercepta stdout e stderr em strings UTF-8.
      timeout_segundos: Tempo limite máximo de espera antes de abortar.

    Retorna:
      Instância de subprocess.CompletedProcess com retorno, stdout e stderr.
    """
    comando_completo = ["sudo"] + cmd if usar_sudo else list(cmd)
    try:
        return subprocess.run(
            comando_completo,
            capture_output=capturar_saida,
            text=True,
            timeout=timeout_segundos,
            check=False
        )
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(
            args=comando_completo,
            returncode=-1,
            stdout="",
            stderr=f"Tempo limite de {timeout_segundos}s excedido ao executar comando."
        )
    except Exception as erro:
        return subprocess.CompletedProcess(
            args=comando_completo,
            returncode=-1,
            stdout="",
            stderr=f"Falha na chamada de sistema: {erro}"
        )


# =============================================================================
# 4. NÚCLEO DE COMUNICAÇÃO COM O MOTOR BEDROCK (FIFO & JOURNALD)
# =============================================================================

class BedrockCore:
    """
    Núcleo responsável pela interação de baixo nível com o processo do Bedrock.
    Gerencia a escrita atômica no canal FIFO e a leitura cirúrgica de eventos
    através dos cursores do systemd journald.
    """

    @staticmethod
    def servico_ativo() -> bool:
        """Verifica se a unidade systemd bedrock.service está ativa."""
        proc = executar_comando(["systemctl", "is-active", "--quiet", NOME_SERVICO])
        return proc.returncode == 0

    @staticmethod
    def fifo_disponivel() -> Tuple[bool, str]:
        """
        Inspeciona o caminho do FIFO no sistema de arquivos para validar
        se ele existe e se é de fato um named pipe (stat.S_ISFIFO).
        """
        if not os.path.exists(CAMINHO_FIFO):
            return False, f"O arquivo FIFO {CAMINHO_FIFO} não existe no sistema."
        try:
            modo = os.stat(CAMINHO_FIFO).st_mode
            if not stat.S_ISFIFO(modo):
                return False, f"{CAMINHO_FIFO} existe, mas não é um named pipe (FIFO)."
        except OSError as erro:
            return False, f"Erro ao inspecionar status do FIFO: {erro}"
        return True, "FIFO operacional."

    @staticmethod
    def obter_cursor_journal() -> str:
        """
        Obtém a posição atual (cursor) do log da unidade bedrock.service no journald.
        Isso permite isolar e ler apenas as mensagens geradas após um comando específico.
        """
        proc = executar_comando(
            ["journalctl", "-u", NOME_SERVICO, "-n", "0", "--show-cursor", "-q"],
            usar_sudo=True
        )
        if proc.returncode == 0 and proc.stdout:
            for linha in proc.stdout.splitlines():
                if linha.startswith("-- cursor:"):
                    return linha.replace("-- cursor:", "").strip()
        return ""

    @classmethod
    def enviar_comando_fifo(cls, comando: str) -> Tuple[bool, str]:
        """
        Escreve um comando no named pipe FIFO conectado à entrada padrão do Bedrock.

        Garantias de Segurança:
          1. Abertura com os.O_NONBLOCK | os.O_WRONLY: Impede que o processo fique
             preso indefinidamente caso o processo leitor (BDS) não esteja conectado.
          2. Tratamento de ENXIO (No such device or address): Detecta instantaneamente
             se o servidor caiu ou se o socket não possui leitor aberto.
          3. Atomicidade POSIX: Escritas inferiores a PIPE_BUF (4096 bytes) são
             garantidas como atômicas pelo kernel do Linux, suportando concorrência.
        """
        comando_limpo = comando.strip()
        if not comando_limpo:
            return False, "Comando vazio ignorado."

        # Validação do estado do serviço
        if not cls.servico_ativo():
            return False, f"Servidor {NOME_SERVICO} está parado. Inicie-o com 'mc start'."

        # Validação da existência do FIFO
        disponivel, motivo = cls.fifo_disponivel()
        if not disponivel:
            return False, f"Canal de console indisponível: {motivo}"

        # Preparação do payload UTF-8 com terminação em quebra de linha
        payload = (comando_limpo + "\n").encode("utf-8")

        try:
            # Abertura não-bloqueante exclusiva para escrita
            fd = os.open(CAMINHO_FIFO, os.O_WRONLY | os.O_NONBLOCK)
            try:
                bytes_escritos = os.write(fd, payload)
                if bytes_escritos < len(payload):
                    return False, "Aviso: Nem todos os bytes do comando foram escritos no FIFO."
            finally:
                os.close(fd)
            return True, "Comando transmitido com sucesso ao console do servidor."
        except OSError as erro:
            if erro.errno == errno.ENXIO:
                return False, (
                    "Erro ENXIO: O arquivo FIFO existe, mas nenhum processo leitor "
                    "(bedrock_server) está conectado a ele. O servidor pode estar iniciando."
                )
            if erro.errno == errno.EACCES:
                return False, f"Permissão negada ao tentar escrever em {CAMINHO_FIFO}."
            return False, f"Falha de E/S ao escrever no FIFO: {erro}"

    @classmethod
    def executar_e_aguardar_resposta(
        cls,
        comando: str,
        aguardar_resposta: bool = True,
        timeout_segundos: float = 2.5
    ) -> Tuple[bool, str]:
        """
        Envia um comando ao console e opcionalmente faz o polling das respostas no journald.

        Particularidade Importante (Challenge 1):
          Comandos de broadcast (ex.: 'say', 'tellraw') não geram saída textual no stdout
          do Bedrock quando não há jogadores conectados no mundo. Para estes comandos,
          definir aguardar_resposta=False evita congelamentos e timeouts desnecessários.
        """
        cursor_inicial = ""
        if aguardar_resposta:
            cursor_inicial = cls.obter_cursor_journal()

        # Envia a instrução pelo named pipe
        sucesso, msg_envio = cls.enviar_comando_fifo(comando)
        if not sucesso:
            return False, msg_envio

        # Se não é para aguardar resposta síncrona (como em comandos de chat)
        if not aguardar_resposta or not cursor_inicial:
            return True, msg_envio

        # Loop de polling aguardando resposta aparecer no journald
        inicio = time.time()
        linhas_resposta: List[str] = []

        while (time.time() - inicio) < timeout_segundos:
            time.sleep(0.15)
            proc = executar_comando(
                [
                    "journalctl",
                    "-u", NOME_SERVICO,
                    f"--after-cursor={cursor_inicial}",
                    "--no-pager",
                    "-o", "cat"
                ],
                usar_sudo=True
            )
            if proc.returncode == 0 and proc.stdout.strip():
                linhas = proc.stdout.strip().splitlines()
                # Filtrar linhas pertinentes
                linhas_resposta = [
                    l for l in linhas
                    if not l.startswith("--") and not l.startswith("Started")
                ]
                if linhas_resposta:
                    break

        if linhas_resposta:
            return True, "\n".join(linhas_resposta)
        else:
            return True, "(Comando enviado. Nenhuma saída síncrona capturada no journal)"


# =============================================================================
# 5. SUBCOMANDOS DE ADMINISTRAÇÃO E DIAGNÓSTICO
# =============================================================================

def subcomando_status(args: argparse.Namespace) -> int:
    """
    Exibe o panorama detalhado de saúde do servidor:
      - Estado no systemd (ActiveState, SubState, PID, Uptime)
      - Consumo de memória RAM física (MemoryCurrent vs Limite Seguro)
      - Consumo de Swap
      - Consulta de jogadores online via comando de console 'list'
    """
    print(f"\n{Cores.NEGRITO}{Cores.CIANO}=== STATUS OPERACIONAL DO SERVIDOR MINECRAFT NEVER LAND ==={Cores.FIM}\n")

    # Coleta de propriedades da unit systemd
    proc_props = executar_comando([
        "systemctl", "show", NOME_SERVICO,
        "-p", "ActiveState",
        "-p", "SubState",
        "-p", "MainPID",
        "-p", "ExecMainStartTimestamp",
        "-p", "MemoryCurrent",
        "-p", "NRestarts"
    ])

    props: Dict[str, str] = {}
    if proc_props.returncode == 0:
        for linha in proc_props.stdout.splitlines():
            if "=" in linha:
                chave, valor = linha.split("=", 1)
                props[chave.strip()] = valor.strip()

    estado_ativo = props.get("ActiveState", "desconhecido")
    sub_estado = props.get("SubState", "desconhecido")
    pid = props.get("MainPID", "0")
    inicio_ts = props.get("ExecMainStartTimestamp", "N/A")
    memoria_bytes_str = props.get("MemoryCurrent", "0")
    reinicios = props.get("NRestarts", "0")

    # Formatação visual do estado do serviço
    if estado_ativo == "active":
        badge_status = f"{Cores.VERDE}[ATIVO / OPERACIONAL]{Cores.FIM} (Substate: {sub_estado})"
    elif estado_ativo == "inactive":
        badge_status = f"{Cores.AMARELO}[PARADO / INATIVO]{Cores.FIM}"
    else:
        badge_status = f"{Cores.VERMELHO}[FALHA / {estado_ativo.upper()}]{Cores.FIM} (Substate: {sub_estado})"

    print(f"  • Serviço Systemd:   {badge_status}")
    print(f"  • PID do Processo:   {pid if pid != '0' else 'Nenhum'}")
    print(f"  • Data de Início:    {inicio_ts}")
    print(f"  • Contagem Restarts: {reinicios}")

    # Cálculo do consumo de memória RAM do processo via Cgroups v2
    try:
        memoria_bytes = int(memoria_bytes_str)
        memoria_mb = memoria_bytes / (1024 * 1024)
        cor_mem = Cores.VERDE
        if memoria_mb > LIMITE_MEMORIA_ALERTA_MB:
            cor_mem = Cores.AMARELO
        if memoria_mb > LIMITE_MEMORIA_MAXIMA_MB:
            cor_mem = Cores.VERMELHO
        print(f"  • Memória RAM (BDS): {cor_mem}{memoria_mb:.1f} MiB{Cores.FIM} (Alerta: {LIMITE_MEMORIA_ALERTA_MB} MiB, Máx: {LIMITE_MEMORIA_MAXIMA_MB} MiB)")
    except ValueError:
        print(f"  • Memória RAM (BDS): {memoria_bytes_str}")

    # Verificação de status do named pipe FIFO
    fifo_ok, msg_fifo = BedrockCore.fifo_disponivel()
    cor_fifo = Cores.VERDE if fifo_ok else Cores.VERMELHO
    print(f"  • Canal Console:     {cor_fifo}{CAMINHO_FIFO}{Cores.FIM} ({msg_fifo})")

    # Se o serviço estiver ativo, consulta jogadores online pelo console
    print(f"\n{Cores.NEGRITO}--- Jogadores Conectados ---{Cores.FIM}")
    if estado_ativo == "active" and fifo_ok:
        sucesso, resposta = BedrockCore.executar_e_aguardar_resposta("list", aguardar_resposta=True)
        if sucesso:
            print(f"  {Cores.CIANO}{resposta.strip()}{Cores.FIM}")
        else:
            print(f"  {Cores.AMARELO}Não foi possível obter lista de jogadores: {resposta}{Cores.FIM}")
    else:
        print(f"  {Cores.CINZA}Servidor inativo. Não há jogadores online.{Cores.FIM}")

    print()
    return 0


def subcomando_start(args: argparse.Namespace) -> int:
    """Inicia o serviço bedrock.service e valida se subiu com sucesso."""
    print(f"{Cores.AMARELO}[...] Iniciando serviço {NOME_SERVICO}...{Cores.FIM}")
    proc = executar_comando(["systemctl", "start", NOME_SERVICO], usar_sudo=True)
    if proc.returncode != 0:
        print(f"{Cores.VERMELHO}[ERRO] Falha ao iniciar o serviço:{Cores.FIM}\n{proc.stderr}")
        return 1

    # Aguarda 2 segundos para checar estabilidade
    time.sleep(2.0)
    if BedrockCore.servico_ativo():
        print(f"{Cores.VERDE}[OK] Servidor Minecraft Never Land iniciado com sucesso!{Cores.FIM}")
        print(f"     Acompanhe o log ao vivo com: {Cores.CIANO}mc logs{Cores.FIM}")
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] O serviço foi acionado, mas não permaneceu ativo.{Cores.FIM}")
        print("Consulte os erros recentes com: mc log 30")
        return 1


def subcomando_stop(args: argparse.Namespace) -> int:
    """
    Executa a parada graciosa do servidor.
    O bedrock.service invoca bedrock_stop.sh, que envia 'stop' ao FIFO,
    aguarda a gravação no LevelDB e desliga o processo com total segurança.
    """
    if not getattr(args, "force", False) and sys.stdin.isatty():
        confirma = input(f"{Cores.AMARELO}Deseja realmente desligar o servidor com salvamento do mundo? (s/N): {Cores.FIM}").strip().lower()
        if confirma not in ("s", "sim", "y", "yes"):
            print("Operação cancelada pelo usuário.")
            return 0

    print(f"{Cores.AMARELO}[...] Enviando comando de salvamento seguro e parando o serviço...{Cores.FIM}")
    proc = executar_comando(["systemctl", "stop", NOME_SERVICO], usar_sudo=True, timeout_segundos=65.0)
    if proc.returncode == 0:
        print(f"{Cores.VERDE}[OK] Servidor desligado e mundo salvo com sucesso!{Cores.FIM}")
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] Falha ao parar o serviço:{Cores.FIM}\n{proc.stderr}")
        return 1


def subcomando_restart(args: argparse.Namespace) -> int:
    """
    Reinicia o servidor com aviso preventivo aos jogadores conectados.
    Emite um broadcast de 10 segundos no chat antes da reinicialização,
    a menos que a flag --now seja fornecida.
    """
    agora = getattr(args, "now", False)

    if not agora and BedrockCore.servico_ativo():
        # Aviso no chat do Minecraft
        BedrockCore.enviar_comando_fifo("say §e[AVISO] O servidor Never Land sera reiniciado em 10 segundos!")
        print(f"{Cores.AMARELO}Aviso transmitido aos jogadores. Reiniciando em 10 segundos...{Cores.FIM}")
        print(f"{Cores.CINZA}(Pressione Ctrl+C para cancelar o reinício){Cores.FIM}")
        try:
            for restante in range(10, 0, -1):
                sys.stdout.write(f"\r{Cores.CIANO}Reiniciando em {restante:2d} segundos... {Cores.FIM}")
                sys.stdout.flush()
                time.sleep(1.0)
            sys.stdout.write("\r\033[K")
        except KeyboardInterrupt:
            print(f"\n{Cores.VERMELHO}[CANCELADO] Reinicialização cancelada pelo operador.{Cores.FIM}")
            BedrockCore.enviar_comando_fifo("say §a[AVISO] Reinicio cancelado pelo administrador!")
            return 130

    print(f"\n{Cores.AMARELO}[...] Reiniciando unidade systemd {NOME_SERVICO}...{Cores.FIM}")
    proc = executar_comando(["systemctl", "restart", NOME_SERVICO], usar_sudo=True, timeout_segundos=70.0)
    if proc.returncode == 0:
        print(f"{Cores.VERDE}[OK] Servidor reiniciado com sucesso!{Cores.FIM}")
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] Falha na reinicialização:{Cores.FIM}\n{proc.stderr}")
        return 1


def subcomando_boot(args: argparse.Namespace) -> int:
    """
    Audita exaustivamente a infraestrutura de inicialização no boot da VM:
      - bedrock.service (habilitado)
      - NetworkManager-wait-online.service (habilitado)
      - firewalld (habilitado)
      - Configuração de swap no /etc/fstab
      - Uptime do sistema operacional
    """
    print(f"\n{Cores.NEGRITO}{Cores.CIANO}=== AUDITORIA DE INICIALIZAÇÃO NO BOOT DA VM ==={Cores.FIM}\n")

    def checar_unit_habilitada(unit: str) -> Tuple[str, bool]:
        p = executar_comando(["systemctl", "is-enabled", unit])
        saida = p.stdout.strip()
        return saida, (saida == "enabled")

    # Auditoria de serviços
    st_bedrock, ok_bedrock = checar_unit_habilitada(NOME_SERVICO)
    st_net, ok_net = checar_unit_habilitada("NetworkManager-wait-online.service")
    st_firewall, ok_firewall = checar_unit_habilitada("firewalld")

    # Auditoria do swap no /etc/fstab
    swap_configurado = False
    try:
        with open("/etc/fstab", "r", encoding="utf-8") as fstab:
            for l in fstab:
                if "/swapfile" in l and not l.strip().startswith("#"):
                    swap_configurado = True
                    break
    except Exception:
        pass

    # Uptime
    p_uptime = executar_comando(["uptime", "-s"])
    ultimo_boot = p_uptime.stdout.strip() if p_uptime.returncode == 0 else "N/A"

    def formatar_check(nome: str, valor: str, esperado: str, ok: bool) -> str:
        tag = f"{Cores.VERDE}[OK]{Cores.FIM}" if ok else f"{Cores.VERMELHO}[FALHA]{Cores.FIM}"
        return f"  • {nome:<32} {tag} Atual: {valor:<10} (Esperado: {esperado})"

    print(formatar_check("Autostart Bedrock (systemd)", st_bedrock, "enabled", ok_bedrock))
    print(formatar_check("Espera de Rede no Boot", st_net, "enabled", ok_net))
    print(formatar_check("Firewalld no Boot", st_firewall, "enabled", ok_firewall))
    print(formatar_check("Swap NVMe no /etc/fstab", "presente" if swap_configurado else "ausente", "presente", swap_configurado))
    print(f"  • Data do Último Boot da VM:       {Cores.CIANO}{ultimo_boot}{Cores.FIM}")
    print(f"  • Estado Atual do Serviço:         {Cores.VERDE if BedrockCore.servico_ativo() else Cores.VERMELHO}{'active' if BedrockCore.servico_ativo() else 'inactive'}{Cores.FIM}\n")

    todos_ok = ok_bedrock and ok_net and ok_firewall and swap_configurado
    if todos_ok:
        print(f"{Cores.VERDE}[SUCESSO] Todos os componentes de autostart e resiliência no boot estão operacionais.{Cores.FIM}\n")
        return 0
    else:
        print(f"{Cores.AMARELO}[ATENÇÃO] Há componentes com configuração divergente para o boot.{Cores.FIM}\n")
        return 1


def subcomando_cmd(args: argparse.Namespace) -> int:
    """Envia um comando arbitrário ao console do Bedrock e imprime o retorno."""
    comando = " ".join(args.comando).strip()
    if not comando:
        print(f"{Cores.VERMELHO}[ERRO] Nenhum comando especificado. Exemplo: mc cmd \"time query day\"{Cores.FIM}")
        return 1

    print(f"{Cores.CIANO}>> {comando}{Cores.FIM}")
    sucesso, resposta = BedrockCore.executar_e_aguardar_resposta(comando, aguardar_resposta=True)
    if sucesso:
        print(resposta.strip())
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] {resposta}{Cores.FIM}")
        return 1


def subcomando_list(args: argparse.Namespace) -> int:
    """Consulta jogadores online no momento."""
    if not BedrockCore.servico_ativo():
        print(f"{Cores.AMARELO}Servidor está inativo. Inicie com 'mc start'.{Cores.FIM}")
        return 1
    sucesso, resposta = BedrockCore.executar_e_aguardar_resposta("list", aguardar_resposta=True)
    if sucesso:
        print(f"{Cores.VERDE}{resposta.strip()}{Cores.FIM}")
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] {resposta}{Cores.FIM}")
        return 1


def subcomando_say(args: argparse.Namespace) -> int:
    """
    Transmite uma mensagem pública no chat do Minecraft.
    Usa aguardar_resposta=False para retorno imediato (evita timeout se 0 jogadores online).
    """
    mensagem = " ".join(args.mensagem).strip()
    if not mensagem:
        print(f"{Cores.VERMELHO}[ERRO] Mensagem vazia. Exemplo: mc say \"Bem-vindos ao Never Land!\"{Cores.FIM}")
        return 1

    comando_say = f"say {mensagem}"
    sucesso, retorno = BedrockCore.executar_e_aguardar_resposta(comando_say, aguardar_resposta=False)
    if sucesso:
        print(f"{Cores.VERDE}[OK] Mensagem transmitida ao chat do servidor: [Server] {mensagem}{Cores.FIM}")
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] Falha ao enviar mensagem: {retorno}{Cores.FIM}")
        return 1


def subcomando_kick(args: argparse.Namespace) -> int:
    """Expulsa um jogador conectado no servidor com motivo opcional."""
    jogador = args.jogador.strip()
    motivo = f" {args.motivo.strip()}" if args.motivo else ""

    # Adiciona aspas ao nome do jogador se contiver espaços
    if " " in jogador and not (jogador.startswith('"') and jogador.endswith('"')):
        jogador = f'"{jogador}"'

    comando_kick = f"kick {jogador}{motivo}"
    sucesso, resposta = BedrockCore.executar_e_aguardar_resposta(comando_kick, aguardar_resposta=True)
    if sucesso:
        print(f"{Cores.AMARELO}{resposta.strip()}{Cores.FIM}")
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] Falha ao expulsar jogador: {resposta}{Cores.FIM}")
        return 1


def subcomando_logs(args: argparse.Namespace) -> int:
    """
    Acompanha o fluxo de logs contínuo do servidor em tempo real (follow do journalctl).
    Intercepta KeyboardInterrupt para encerramento gracioso.
    """
    print(f"{Cores.CIANO}=== LOGS EM TEMPO REAL: {NOME_SERVICO} (Pressione Ctrl+C para sair) ==={Cores.FIM}\n")
    try:
        subprocess.run(["sudo", "journalctl", "-u", NOME_SERVICO, "-f", "-o", "cat"])
    except KeyboardInterrupt:
        print(f"\n{Cores.AMARELO}[Encerrado acompanhamento de logs]{Cores.FIM}")
    return 0


def subcomando_log(args: argparse.Namespace) -> int:
    """Exibe as últimas N linhas de log gravadas no journald."""
    linhas = getattr(args, "linhas", 50)
    proc = executar_comando(["journalctl", "-u", NOME_SERVICO, "-n", str(linhas), "--no-pager", "-o", "cat"], usar_sudo=True)
    if proc.returncode == 0:
        print(proc.stdout.strip())
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] Falha ao ler logs: {proc.stderr}{Cores.FIM}")
        return 1


def subcomando_jogadores(args: argparse.Namespace) -> int:
    """
    Filtra e exibe o histórico de eventos de conexão, desconexão e spawn de jogadores,
    identificando Gamertag e XUID a partir do histórico do journald.
    """
    print(f"\n{Cores.NEGRITO}{Cores.CIANO}=== HISTÓRICO DE CONEXÕES DE JOGADORES (XUID) ==={Cores.FIM}\n")
    proc = executar_comando(["journalctl", "-u", NOME_SERVICO, "--no-pager", "-o", "cat"], usar_sudo=True)
    if proc.returncode != 0:
        print(f"{Cores.VERMELHO}[ERRO] Falha ao ler histórico: {proc.stderr}{Cores.FIM}")
        return 1

    eventos = []
    palavras_chave = ["Player connected", "Player disconnected", "Player Spawned"]
    for linha in proc.stdout.splitlines():
        if any(pc in linha for pc in palavras_chave):
            eventos.append(linha.strip())

    if not eventos:
        print(f"{Cores.CINZA}Nenhum evento de conexão de jogadores registrado no histórico atual.{Cores.FIM}\n")
        return 0

    # Exibe os últimos 50 eventos
    for ev in eventos[-50:]:
        if "Player connected" in ev:
            print(f"  {Cores.VERDE}✔ CONEXÃO:{Cores.FIM}    {ev}")
        elif "Player disconnected" in ev:
            print(f"  {Cores.AMARELO}✖ DESCONEXÃO:{Cores.FIM} {ev}")
        else:
            print(f"  {Cores.CIANO}✦ SPAWN:{Cores.FIM}      {ev}")

    print()
    return 0


def subcomando_backup(args: argparse.Namespace) -> int:
    """Dispara a rotina oficial de backup manual do mundo."""
    if not os.path.exists(SCRIPT_BACKUP):
        print(f"{Cores.VERMELHO}[ERRO] Script de backup não encontrado em {SCRIPT_BACKUP}{Cores.FIM}")
        return 1

    print(f"{Cores.AMARELO}[...] Executando rotina de backup com salvamento do LevelDB...{Cores.FIM}")
    inicio = time.time()
    proc = executar_comando([SCRIPT_BACKUP], timeout_segundos=120.0)
    duracao = time.time() - inicio

    if proc.returncode == 0:
        print(f"{Cores.VERDE}[OK] Backup concluído em {duracao:.1f}s!{Cores.FIM}")
        # Exibe o backup recém criado
        if os.path.exists(DIRETORIO_BACKUPS):
            p_ls = executar_comando(["ls", "-lht", DIRETORIO_BACKUPS])
            if p_ls.returncode == 0:
                linhas = p_ls.stdout.splitlines()
                if len(linhas) > 1:
                    print(f"  Novo arquivo gerado: {Cores.CIANO}{linhas[1].strip()}{Cores.FIM}")
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] Falha no script de backup:{Cores.FIM}\n{proc.stderr}\n{proc.stdout}")
        return 1


def subcomando_backups(args: argparse.Namespace) -> int:
    """Lista todos os arquivos de backup existentes no diretório de backups."""
    if not os.path.exists(DIRETORIO_BACKUPS):
        print(f"{Cores.AMARELO}Diretório de backups não encontrado: {DIRETORIO_BACKUPS}{Cores.FIM}")
        return 0

    print(f"\n{Cores.NEGRITO}{Cores.CIANO}=== BACKUPS DISPONÍVEIS EM {DIRETORIO_BACKUPS} ==={Cores.FIM}\n")
    proc = executar_comando(["ls", "-lh", DIRETORIO_BACKUPS])
    if proc.returncode == 0:
        linhas = proc.stdout.strip().splitlines()
        for linha in linhas:
            if ".tar.gz" in linha:
                print(f"  {linha}")
        print()
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] Falha ao listar backups: {proc.stderr}{Cores.FIM}")
        return 1


def subcomando_recursos(args: argparse.Namespace) -> int:
    """Exibe relatório consolidado de RAM, Swap, Disco e limites de Cgroups v2."""
    print(f"\n{Cores.NEGRITO}{Cores.CIANO}=== DIAGNÓSTICO DE RECURSOS DA VM (OCI E2.1.Micro) ==={Cores.FIM}\n")

    # Memória RAM e Swap do Host
    print(f"{Cores.NEGRITO}1. Memória Física e Swap (Host Linux):{Cores.FIM}")
    p_free = executar_comando(["free", "-h"])
    if p_free.returncode == 0:
        for linha in p_free.stdout.splitlines():
            print(f"   {linha}")

    # Partição Raiz
    print(f"\n{Cores.NEGRITO}2. Armazenamento em Disco (Partição Raiz):{Cores.FIM}")
    p_df = executar_comando(["df", "-h", "/"])
    if p_df.returncode == 0:
        for linha in p_df.stdout.splitlines():
            print(f"   {linha}")

    # Uptime e Carga de CPU
    print(f"\n{Cores.NEGRITO}3. Carga de CPU e Tempo de Atividade:{Cores.FIM}")
    p_up = executar_comando(["uptime"])
    if p_up.returncode == 0:
        print(f"   {p_up.stdout.strip()}")

    # Cgroups do systemd
    print(f"\n{Cores.NEGRITO}4. Limites de Cgroups v2 do Serviço ({NOME_SERVICO}):{Cores.FIM}")
    p_cg = executar_comando([
        "systemctl", "show", NOME_SERVICO,
        "-p", "MemoryCurrent",
        "-p", "MemoryHigh",
        "-p", "MemoryMax",
        "-p", "NRestarts"
    ])
    if p_cg.returncode == 0:
        for linha in p_cg.stdout.splitlines():
            print(f"   {linha}")

    print()
    return 0


def subcomando_rede(args: argparse.Namespace) -> int:
    """Exibe o status das portas no Firewall Linux e sockets em escuta no kernel."""
    print(f"\n{Cores.NEGRITO}{Cores.CIANO}=== DIAGNÓSTICO DE REDE E FIREWALL ==={Cores.FIM}\n")

    # Portas liberadas no firewalld
    p_fw = executar_comando(["firewall-cmd", "--list-ports"], usar_sudo=True)
    portas_fw = p_fw.stdout.strip() if p_fw.returncode == 0 else "Erro ao consultar"
    print(f"  • Portas liberadas no firewalld: {Cores.VERDE}{portas_fw}{Cores.FIM}")

    # Sockets UDP e TCP em escuta (ss)
    print(f"\n  • Sockets de escuta ativos no Kernel:")
    p_ss_udp = executar_comando(["ss", "-ulpn"], usar_sudo=True)
    if p_ss_udp.returncode == 0:
        for linha in p_ss_udp.stdout.splitlines():
            if "19132" in linha or "19133" in linha or "7551" in linha or "bedrock" in linha or "State" in linha:
                print(f"     {linha}")

    p_ss_tcp = executar_comando(["ss", "-tlpn"], usar_sudo=True)
    if p_ss_tcp.returncode == 0:
        for linha in p_ss_tcp.stdout.splitlines():
            if "19132" in linha or "19133" in linha or "bedrock" in linha:
                print(f"     {linha}")

    print()
    return 0


def subcomando_config(args: argparse.Namespace) -> int:
    """Exibe as diretivas ativas do server.properties (ocultando comentários)."""
    if not os.path.exists(ARQUIVO_PROPRIEDADES):
        print(f"{Cores.VERMELHO}[ERRO] Arquivo não encontrado: {ARQUIVO_PROPRIEDADES}{Cores.FIM}")
        return 1

    print(f"\n{Cores.NEGRITO}{Cores.CIANO}=== CONFIGURAÇÕES ATIVAS (server.properties) ==={Cores.FIM}\n")
    try:
        with open(ARQUIVO_PROPRIEDADES, "r", encoding="utf-8") as f:
            for linha in f:
                linha_limpa = linha.strip()
                if linha_limpa and not linha_limpa.startswith("#"):
                    if "=" in linha_limpa:
                        k, v = linha_limpa.split("=", 1)
                        print(f"  {Cores.CIANO}{k:<28}{Cores.FIM} = {Cores.VERDE}{v}{Cores.FIM}")
                    else:
                        print(f"  {linha_limpa}")
        print()
        return 0
    except Exception as erro:
        print(f"{Cores.VERMELHO}[ERRO] Falha ao ler propriedades: {erro}{Cores.FIM}")
        return 1


def subcomando_update(args: argparse.Namespace) -> int:
    """Executa a rotina de atualização oficial do Bedrock Dedicated Server."""
    if not os.path.exists(SCRIPT_ATUALIZACAO):
        print(f"{Cores.VERMELHO}[ERRO] Script de atualização não encontrado: {SCRIPT_ATUALIZACAO}{Cores.FIM}")
        return 1

    if sys.stdin.isatty():
        confirma = input(f"{Cores.AMARELO}Deseja baixar e atualizar o BDS para a última versão oficial? (s/N): {Cores.FIM}").strip().lower()
        if confirma not in ("s", "sim", "y", "yes"):
            print("Operação de atualização cancelada.")
            return 0

    print(f"{Cores.AMARELO}[...] Executando script de atualização e preservação do mundo...{Cores.FIM}")
    proc = executar_comando([SCRIPT_ATUALIZACAO], timeout_segundos=180.0)
    if proc.returncode == 0:
        print(f"{Cores.VERDE}[OK] Atualização concluída com sucesso!{Cores.FIM}")
        print(f"{Cores.AMARELO}[...] Reiniciando serviço para carregar o novo binário...{Cores.FIM}")
        executar_comando(["systemctl", "restart", NOME_SERVICO], usar_sudo=True)
        return 0
    else:
        print(f"{Cores.VERMELHO}[ERRO] Falha durante a atualização:{Cores.FIM}\n{proc.stderr}\n{proc.stdout}")
        return 1


# =============================================================================
# 6. SESSÃO DE CONSOLE REPL BIDIRECIONAL COM O BDS
# =============================================================================

def subcomando_console(args: Optional[argparse.Namespace] = None) -> int:
    """
    Inicia uma sessão interativa de console REPL direta com o processo Bedrock.
    Cada linha digitada é enviada ao named pipe e as respostas emitidas pelo
    servidor são capturadas e exibidas imediatamente na tela.
    """
    if not BedrockCore.servico_ativo():
        print(f"{Cores.VERMELHO}[ERRO] O servidor {NOME_SERVICO} está inativo.{Cores.FIM}")
        print("Inicie o servidor com 'mc start' antes de abrir o console.")
        return 1

    print(f"\n{Cores.NEGRITO}{Cores.CIANO}================================================================{Cores.FIM}")
    print(f"{Cores.NEGRITO}{Cores.CIANO}   CONSOLE INTERATIVO MINECRAFT BEDROCK (REPL) - NEVER LAND   {Cores.FIM}")
    print(f"{Cores.NEGRITO}{Cores.CIANO}================================================================{Cores.FIM}")
    print(f"{Cores.CINZA}Digite comandos normais do Bedrock (ex.: list, time query day, gamerule, say Olá).")
    print(f"Para sair do console e voltar ao menu, digite {Cores.BRANCO}'exit'{Cores.CINZA}, {Cores.BRANCO}'quit'{Cores.CINZA} ou pressione {Cores.BRANCO}Ctrl+C{Cores.CINZA}.{Cores.FIM}\n")

    while True:
        try:
            prompt = f"{Cores.VERDE}bedrock> {Cores.FIM}"
            linha_comando = input(prompt).strip()

            if not linha_comando:
                continue

            if linha_comando.lower() in ("exit", "quit", "sair", "voltar"):
                print(f"{Cores.CINZA}Sessão de console finalizada.{Cores.FIM}")
                break

            # Tratamento especial para 'say'
            eh_say = linha_comando.startswith("say ") or linha_comando.startswith("tellraw ")

            # Envia ao console e aguarda resposta
            sucesso, retorno = BedrockCore.executar_e_aguardar_resposta(
                linha_comando,
                aguardar_resposta=(not eh_say),
                timeout_segundos=3.0
            )

            if eh_say and sucesso:
                print(f"{Cores.VERDE}[Server] Mensagem transmitida ao chat.{Cores.FIM}")
            elif retorno.strip():
                print(f"{Cores.CIANO}{retorno.strip()}{Cores.FIM}")

        except (KeyboardInterrupt, EOFError):
            print(f"\n{Cores.CINZA}Sessão de console interrompida.{Cores.FIM}")
            break

    return 0


# =============================================================================
# 7. DASHBOARD E MENU INTERATIVO (QUANDO INVOCADO SEM ARGUMENTOS EM TTY)
# =============================================================================

def renderizar_banner_menu() -> None:
    """Renderiza o cabeçalho estilizado do painel de controle interativo."""
    # Obtenção de métricas rápidas
    ativo = BedrockCore.servico_ativo()
    status_str = f"{Cores.VERDE}ATIVO / ONLINE{Cores.FIM}" if ativo else f"{Cores.VERMELHO}PARADO / OFFLINE{Cores.FIM}"

    # Memória
    p_cg = executar_comando(["systemctl", "show", NOME_SERVICO, "-p", "MemoryCurrent"])
    mem_str = "0 MiB"
    if p_cg.returncode == 0 and "MemoryCurrent=" in p_cg.stdout:
        try:
            val_bytes = int(p_cg.stdout.split("=")[1].strip())
            mem_str = f"{val_bytes / (1024 * 1024):.1f} MiB"
        except Exception:
            pass

    print(f"\n{Cores.CIANO}╔════════════════════════════════════════════════════════════════════╗{Cores.FIM}")
    print(f"{Cores.CIANO}║{Cores.FIM}   {Cores.NEGRITO}{Cores.BRANCO}MINECRAFT BEDROCK \"NEVER LAND\" — PAINEL DE CONTROLE SSH{Cores.FIM}         {Cores.CIANO}║{Cores.FIM}")
    print(f"{Cores.CIANO}╠════════════════════════════════════════════════════════════════════╣{Cores.FIM}")
    print(f"{Cores.CIANO}║{Cores.FIM}  Estado: {status_str:<28} RAM BDS: {Cores.VERDE}{mem_str:<18}{Cores.FIM} {Cores.CIANO}║{Cores.FIM}")
    print(f"{Cores.CIANO}╚════════════════════════════════════════════════════════════════════╝{Cores.FIM}")


def menu_interativo() -> int:
    """
    Loop do menu interativo principal com opções numéricas amigáveis.
    Permite operar todo o servidor sem digitar nenhum comando complexo.
    """
    while True:
        try:
            renderizar_banner_menu()
            print(f"{Cores.NEGRITO}--- OPERAÇÕES DO SERVIÇO ---{Cores.FIM}")
            print(f"  {Cores.VERDE}1{Cores.FIM} - Ver Status Completo do Servidor")
            print(f"  {Cores.VERDE}2{Cores.FIM} - Iniciar Servidor (start)")
            print(f"  {Cores.VERDE}3{Cores.FIM} - Parar Servidor com Salvamento Seguro (stop)")
            print(f"  {Cores.VERDE}4{Cores.FIM} - Reiniciar Servidor com Aviso de 10s (restart)")
            print(f"  {Cores.VERDE}5{Cores.FIM} - Auditar Configurações de Inicialização no Boot")

            print(f"\n{Cores.NEGRITO}--- CONSOLE E JOGADORES ---{Cores.FIM}")
            print(f"  {Cores.CIANO}6{Cores.FIM} - Consultar Jogadores Online (list)")
            print(f"  {Cores.CIANO}7{Cores.FIM} - Transmitir Mensagem no Chat (say)")
            print(f"  {Cores.CIANO}8{Cores.FIM} - Expulsar Jogador (kick)")
            print(f"  {Cores.CIANO}9{Cores.FIM} - Abrir Console Interativo Bedrock (REPL)")

            print(f"\n{Cores.NEGRITO}--- LOGS, BACKUP E DIAGNÓSTICO ---{Cores.FIM}")
            print(f"  {Cores.AMARELO}10{Cores.FIM} - Acompanhar Logs em Tempo Real (Ctrl+C para sair)")
            print(f"  {Cores.AMARELO}11{Cores.FIM} - Ver Últimas 50 Linhas de Log")
            print(f"  {Cores.AMARELO}12{Cores.FIM} - Histórico de Jogadores (Conexões/XUID)")
            print(f"  {Cores.AMARELO}13{Cores.FIM} - Executar Backup Imediato do Mundo")
            print(f"  {Cores.AMARELO}14{Cores.FIM} - Listar Backups Existentes")
            print(f"  {Cores.AMARELO}15{Cores.FIM} - Diagnóstico de Recursos (RAM/Swap/Disco)")
            print(f"  {Cores.AMARELO}16{Cores.FIM} - Diagnóstico de Rede e Firewall")
            print(f"  {Cores.AMARELO}17{Cores.FIM} - Ver Opções do server.properties")

            print(f"\n  {Cores.VERMELHO}0{Cores.FIM} - Sair do Painel")
            print(f"{Cores.CIANO}──────────────────────────────────────────────────────────────────────{Cores.FIM}")

            escolha = input(f"{Cores.NEGRITO}Escolha uma opção (0-17): {Cores.FIM}").strip()

            if escolha in ("0", "q", "sair", "exit"):
                print(f"\n{Cores.VERDE}Até logo!{Cores.FIM}\n")
                break
            elif escolha == "1":
                subcomando_status(argparse.Namespace())
            elif escolha == "2":
                subcomando_start(argparse.Namespace())
            elif escolha == "3":
                subcomando_stop(argparse.Namespace(force=False))
            elif escolha == "4":
                subcomando_restart(argparse.Namespace(now=False))
            elif escolha == "5":
                subcomando_boot(argparse.Namespace())
            elif escolha == "6":
                subcomando_list(argparse.Namespace())
            elif escolha == "7":
                msg = input(f"{Cores.CIANO}Digite a mensagem a ser enviada no chat: {Cores.FIM}").strip()
                if msg:
                    subcomando_say(argparse.Namespace(mensagem=[msg]))
            elif escolha == "8":
                jog = input(f"{Cores.AMARELO}Digite o nome do jogador: {Cores.FIM}").strip()
                if jog:
                    mot = input(f"{Cores.AMARELO}Digite o motivo da expulsão (opcional): {Cores.FIM}").strip()
                    subcomando_kick(argparse.Namespace(jogador=jog, motivo=mot))
            elif escolha == "9":
                subcomando_console()
            elif escolha == "10":
                subcomando_logs(argparse.Namespace())
            elif escolha == "11":
                subcomando_log(argparse.Namespace(linhas=50))
            elif escolha == "12":
                subcomando_jogadores(argparse.Namespace())
            elif escolha == "13":
                subcomando_backup(argparse.Namespace())
            elif escolha == "14":
                subcomando_backups(argparse.Namespace())
            elif escolha == "15":
                subcomando_recursos(argparse.Namespace())
            elif escolha == "16":
                subcomando_rede(argparse.Namespace())
            elif escolha == "17":
                subcomando_config(argparse.Namespace())
            else:
                print(f"{Cores.VERMELHO}Opção inválida: {escolha}{Cores.FIM}")

            input(f"\n{Cores.CINZA}Pressione <Enter> para retornar ao menu principal...{Cores.FIM}")

        except (KeyboardInterrupt, EOFError):
            print(f"\n\n{Cores.VERDE}Operação finalizada pelo usuário. Até logo!{Cores.FIM}\n")
            break

    return 0


# =============================================================================
# 8. PARSER DE ARGUMENTOS CLI E PONTO DE ENTRADA PRINCIPAL
# =============================================================================

def criar_parser() -> argparse.ArgumentParser:
    """Constrói a estrutura completa de subcomandos e ajuda da CLI."""
    parser = argparse.ArgumentParser(
        prog="mc",
        description="Utilitário de Controle e Operação do Servidor Minecraft Bedrock Never Land via SSH.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemplos de Uso:
  mc status                 # Exibe saúde do serviço, RAM, swap e jogadores online
  mc start                  # Inicia o serviço bedrock.service
  mc stop                   # Desliga com salvamento seguro do mundo
  mc restart                # Reinicia com aviso prévio de 10 segundos no chat
  mc boot                   # Audita configurações de autostart e dependências de rede
  mc cmd "time query day"   # Envia comando direto ao console e exibe o retorno
  mc list                   # Lista jogadores online no momento
  mc say "Olá mundo"        # Transmite mensagem global no chat
  mc kick Steve "Lag"       # Expulsa jogador do servidor com motivo
  mc console                # Abre console interativo bidirecional (REPL)
  mc logs                   # Acompanha logs do servidor ao vivo (Ctrl+C encerra)
  mc log 30                 # Exibe as últimas 30 linhas de log
  mc jogadores              # Histórico de conexões e XUIDs de jogadores
  mc backup                 # Dispara backup manual imediato
  mc backups                # Lista backups existentes em disco
  mc recursos               # Diagnóstico de RAM física, Swap NVMe e Cgroups
  mc rede                   # Status de portas no firewall e sockets de escuta
  mc config                 # Exibe configurações ativas do server.properties
        """
    )

    subparsers = parser.add_subparsers(dest="subcomando", help="Ação a ser executada")

    # Serviço
    p_status = subparsers.add_parser("status", help="Exibe estado detalhado do serviço, memória e jogadores")
    p_status.set_defaults(func=subcomando_status)

    p_start = subparsers.add_parser("start", help="Inicia o serviço bedrock.service")
    p_start.set_defaults(func=subcomando_start)

    p_stop = subparsers.add_parser("stop", help="Desliga o servidor graciosamente com salvamento do mundo")
    p_stop.add_argument("-f", "--force", action="store_true", help="Não solicita confirmação interativa")
    p_stop.set_defaults(func=subcomando_stop)

    p_restart = subparsers.add_parser("restart", help="Reinicia o servidor com aviso preventivo no chat")
    p_restart.add_argument("--now", action="store_true", help="Reinicia imediatamente sem contagem regressiva de 10s")
    p_restart.set_defaults(func=subcomando_restart)

    p_boot = subparsers.add_parser("boot", help="Audita configuração de autostart no boot da VM")
    p_boot.set_defaults(func=subcomando_boot)

    # Console
    p_cmd = subparsers.add_parser("cmd", help="Envia comando arbitrário ao console Bedrock")
    p_cmd.add_argument("comando", nargs="+", help="Comando do Bedrock a ser executado")
    p_cmd.set_defaults(func=subcomando_cmd)

    p_list = subparsers.add_parser("list", help="Consulta jogadores online no momento")
    p_list.set_defaults(func=subcomando_list)

    p_say = subparsers.add_parser("say", help="Transmite mensagem pública no chat do servidor")
    p_say.add_argument("mensagem", nargs="+", help="Texto da mensagem")
    p_say.set_defaults(func=subcomando_say)

    p_kick = subparsers.add_parser("kick", help="Expulsa um jogador conectado")
    p_kick.add_argument("jogador", help="Gamertag do jogador")
    p_kick.add_argument("motivo", nargs="?", default="", help="Motivo da expulsão (opcional)")
    p_kick.set_defaults(func=subcomando_kick)

    p_console = subparsers.add_parser("console", aliases=["repl"], help="Abre sessão de console interativo com o BDS")
    p_console.set_defaults(func=subcomando_console)

    # Logs
    p_logs = subparsers.add_parser("logs", help="Acompanha logs do servidor ao vivo em tempo real")
    p_logs.set_defaults(func=subcomando_logs)

    p_log = subparsers.add_parser("log", help="Exibe as últimas N linhas de log")
    p_log.add_argument("linhas", nargs="?", type=int, default=50, help="Quantidade de linhas (padrão: 50)")
    p_log.set_defaults(func=subcomando_log)

    p_jogadores = subparsers.add_parser("jogadores", help="Exibe histórico de conexões/desconexões e XUIDs")
    p_jogadores.set_defaults(func=subcomando_jogadores)

    # Backup & Manutenção
    p_backup = subparsers.add_parser("backup", help="Executa rotina manual de backup do mundo")
    p_backup.set_defaults(func=subcomando_backup)

    p_backups = subparsers.add_parser("backups", help="Lista backups salvos no disco")
    p_backups.set_defaults(func=subcomando_backups)

    p_update = subparsers.add_parser("update", help="Atualiza o BDS para a última versão oficial Mojang")
    p_update.set_defaults(func=subcomando_update)

    # Diagnóstico
    p_recursos = subparsers.add_parser("recursos", help="Exibe diagnóstico de RAM física, Swap NVMe e Cgroups")
    p_recursos.set_defaults(func=subcomando_recursos)

    p_rede = subparsers.add_parser("rede", help="Exibe portas no firewalld e sockets de escuta no kernel")
    p_rede.set_defaults(func=subcomando_rede)

    p_config = subparsers.add_parser("config", help="Exibe configurações ativas do server.properties")
    p_config.set_defaults(func=subcomando_config)

    p_ajuda = subparsers.add_parser("ajuda", help="Exibe o manual de ajuda dos comandos")
    p_ajuda.set_defaults(func=lambda a: parser.print_help() or 0)

    return parser


def main() -> int:
    """Função principal: roteia entre modo interativo (menu TTY) e modo CLI direto."""
    # Se invocado sem argumentos na linha de comando
    if len(sys.argv) == 1:
        # Se for um terminal interativo TTY (usuário conectado via SSH digitando 'mc')
        if sys.stdin.isatty():
            return menu_interativo()
        else:
            # Em chamadas não-interativas (cron, pipes, automações sem argumentos), exibe ajuda e sai
            parser = criar_parser()
            parser.print_help()
            return 0

    # Modo CLI com subcomandos
    parser = criar_parser()
    args = parser.parse_args()

    if hasattr(args, "func"):
        return args.func(args)
    else:
        parser.print_help()
        return 0


if __name__ == "__main__":
    sys.exit(main())
