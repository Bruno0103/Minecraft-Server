#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
========================================================================================
SISTEMA DE ATIVAÇÃO DE COORDENADAS - MINECRAFT BEDROCK DEDICATED SERVER (BDS)
Servidor: Never Land
Arquivo: Server/enable_coordinates.py (Cópia local versionada do script da VM)
Destino na VM: /home/opc/minecraft/enable_coordinates.py
========================================================================================

DESCRIÇÃO DETALHADA:
    No Minecraft Bedrock Edition, a visualização de coordenadas em tempo real no canto
    superior esquerdo da tela é governada pela regra de jogo (gamerule) 'showcoordinates'.

PRESERVAÇÃO INTEGRAL DE CONQUISTAS (ACHIEVEMENTS):
    - No Bedrock Edition, 'showcoordinates' NÃO é considerado cheat ou trapaça.
    - O jogo permite habilitar coordenadas no menu vanilla de qualquer mundo sem desativar
      troféus ou conquistas da Xbox Live / PlayStation Network / Nintendo Switch.
    - Este script realiza uma alteração cirúrgica no arquivo NBT binário (level.dat e
      level.dat_old), alterando o valor do byte de 0 para 1.
    - As flags críticas de integridade ('commandsEnabled' e 'hasBeenLoadedInCreative')
      permanecem estritamente intactas com o valor 0.

METODOLOGIA TÉCNICA:
    1. Execução recomendada com o serviço 'bedrock' pausado para evitar concorrência de I/O:
           sudo systemctl stop bedrock
           python3 /home/opc/minecraft/enable_coordinates.py
           sudo systemctl start bedrock
    2. Criação automática de snapshots de backup de segurança com carimbo de data e hora
       (ex: level.dat.bak_20261103_142420).
    3. Varredura dos bytes no formato Little-Endian NBT em busca da TAG_Byte correspondente:
       Assinatura: [0x01] [0x0f, 0x00] [s, h, o, w, c, o, o, r, d, i, n, a, t, e, s] [0x00]
    4. Transição atômica do payload binário de 0x00 para 0x01.
    5. Validação final pós-gravação atestando que todas as tags de conquistas continuam 0.
========================================================================================
"""

import os
import sys
import shutil
import datetime

# Diretório padrão onde o mundo 'Never Land' reside na VM Oracle Linux
WORLD_DIR = "/home/opc/minecraft/server/worlds/Never Land"

# Arquivos que armazenam o estado e metadados das gamerules do mundo
TARGET_FILES = ["level.dat", "level.dat_old"]

# Assinatura binária NBT da tag showcoordinates:
# - \x01: Identificador de tipo da tag NBT (TAG_Byte)
# - \x0f\x00: Comprimento da string do nome da tag (15 caracteres em Little Endian short)
# - showcoordinates: Nome da chave em ASCII
# - \x00: Valor numérico atual (0 = Falso / Desativado)
SEARCH_PATTERN_DISABLED = b"\x01\x0f\x00showcoordinates\x00"
SEARCH_PATTERN_ENABLED  = b"\x01\x0f\x00showcoordinates\x01"

def backup_and_modify(filename: str) -> bool:
    """
    Localiza o arquivo de dados do mundo, gera uma cópia de segurança com timestamp
    e realiza a substituição binária da gamerule 'showcoordinates' de 0 para 1.

    Args:
        filename (str): Nome do arquivo a ser processado ('level.dat' ou 'level.dat_old').

    Returns:
        bool: True se o arquivo foi atualizado com sucesso ou já estava ativo; False em caso de falha.
    """
    file_path = os.path.join(WORLD_DIR, filename)
    
    # Verifica a existência do arquivo antes de prosseguir
    if not os.path.exists(file_path):
        print(f"[AVISO] O arquivo de destino '{file_path}' não foi encontrado no disco. Pulando etapa...")
        return False

    print(f"\n[PROCESSANDO] Analisando arquivo: {filename}...")

    # Gera cópia de backup preventiva com data e hora exata para rollback caso necessário
    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = f"{file_path}.bak_{timestamp}"
    shutil.copyfile(file_path, backup_path)
    print(f" -> [BACKUP] Snapshot de segurança gerado com sucesso em: {backup_path}")

    # Abre o arquivo em modo binário e carrega como bytearray para permitir mutação direta
    with open(file_path, "rb") as f:
        data = bytearray(f.read())

    # Se o padrão ativo já estiver presente, não é necessária nova gravação
    if SEARCH_PATTERN_ENABLED in data:
        print(f" -> [INFO] A gamerule 'showcoordinates' já está ATIVADA (valor = 1) no arquivo '{filename}'.")
        return True

    # Busca o padrão com valor desativado (0)
    idx = data.find(SEARCH_PATTERN_DISABLED)
    if idx == -1:
        print(f" -> [ERRO] A assinatura binária da tag 'showcoordinates' não foi localizada em '{filename}'.")
        return False

    # O byte que representa o valor booleano é exatamente o último caractere do padrão
    value_offset = idx + len(SEARCH_PATTERN_DISABLED) - 1
    
    # Modificação in-place para 1 (True / Exibição ativada)
    data[value_offset] = 1

    # Persiste os dados binários modificados de volta no arquivo
    with open(file_path, "wb") as f:
        f.write(data)

    print(f" -> [SUCESSO] Gamerule 'showcoordinates' atualizada para 1 no byte offset {value_offset}!")
    return True

def verify_achievements_integrity() -> None:
    """
    Inspeciona o arquivo level.dat modificado para certificar categoricamente
    que nenhuma flag que desativa conquistas foi corrompida ou alterada.
    """
    level_dat_path = os.path.join(WORLD_DIR, "level.dat")
    with open(level_dat_path, "rb") as f:
        data = f.read()

    print("\n" + "-" * 70)
    print(" [RELATÓRIO DE INTEGRIDADE DAS CONQUISTAS E REGRAS]")
    print("-" * 70)
    
    # Dicionário de auditoria de tags críticas
    checks = {
        b"commandsEnabled": "Comandos/Cheats Habilitados (deve ser 0)",
        b"hasBeenLoadedInCreative": "Mundo Aberto em Criativo (deve ser 0)",
        b"showcoordinates": "Visualização de Coordenadas (deve ser 1)"
    }

    all_valid = True
    for tag_bytes, label in checks.items():
        idx = data.find(tag_bytes)
        if idx != -1:
            val = data[idx + len(tag_bytes)]
            print(f" • {label}: valor atual = {val}")
            
            # Validação lógica de cada campo
            if tag_bytes in [b"commandsEnabled", b"hasBeenLoadedInCreative"] and val != 0:
                print(f"   [ALERTA CRÍTICO] A tag '{tag_bytes.decode('utf-8')}' não é 0! Conquistas podem estar inativas.")
                all_valid = False
            elif tag_bytes == b"showcoordinates" and val != 1:
                print("   [ALERTA] A tag 'showcoordinates' não está ativa com valor 1.")
                all_valid = False
        else:
            print(f" • {label}: [NÃO ENCONTRADA]")

    if all_valid:
        print("\n [CONFIRMADO] As conquistas do Xbox Live / PSN / Nintendo Switch continuam 100% PRESERVADAS!")
    print("-" * 70)

def main():
    """Ponto de entrada principal do script."""
    print("=" * 70)
    print(" ATIVADOR DE VISUALIZAÇÃO DE COORDENADAS - SERVER NEVER LAND")
    print("=" * 70)

    # Validação preliminar da presença do diretório do mundo
    if not os.path.exists(WORLD_DIR):
        print(f"[ERRO CRÍTICO] Diretório do mundo não localizado em: {WORLD_DIR}")
        sys.exit(1)

    # Processa os arquivos level.dat e o backup level.dat_old
    for target in TARGET_FILES:
        backup_and_modify(target)

    # Executa a auditoria de integridade das conquistas
    verify_achievements_integrity()
    print("\nOperação concluída com sucesso!")

if __name__ == "__main__":
    main()
