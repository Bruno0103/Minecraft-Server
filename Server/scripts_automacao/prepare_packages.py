# ==============================================================================
# Script de Preparação Local dos Pacotes para Envio ao Servidor
# ==============================================================================
# Este script:
# 1. Descompacta 'Never-Land-Essencial.mcpack' e monta um arquivo 'NeverLandEssencial.tar.gz'
# 2. Descompacta 'Advanced Dynamic Lighting BP' de 'Advanced Dynamic Lighting v6.1.mcaddon'
#    e monta 'AdvancedDynamicLighting_v6_BP.tar.gz'
# 3. Valida a estrutura dos manifests e arquivos antes de qualquer envio.
# ==============================================================================

import os
import sys
import zipfile
import tarfile
import json
import shutil
from pathlib import Path

DOWNLOADS_DIR = Path(r"C:\Users\Bruno\Downloads")
TEMP_DIR = Path(__file__).parent / "temp_deploy"

ESSENCIAL_MCPACK = DOWNLOADS_DIR / "Never-Land-Essencial.mcpack"
LIGHTING_MCADDON = DOWNLOADS_DIR / "Advanced Dynamic Lighting v6.1.mcaddon"

def prepare_packages():
    # Cria pasta temporária limpa
    if TEMP_DIR.exists():
        shutil.rmtree(TEMP_DIR)
    TEMP_DIR.mkdir(parents=True, exist_ok=True)
    
    print("[1/4] Processando Never Land Essencial...")
    essencial_dir = TEMP_DIR / "NeverLandEssencial"
    essencial_dir.mkdir(parents=True, exist_ok=True)
    
    with zipfile.ZipFile(ESSENCIAL_MCPACK, 'r') as z:
        z.extractall(essencial_dir)
        
    manifest_essencial = essencial_dir / "manifest.json"
    if not manifest_essencial.exists():
        raise FileNotFoundError("manifest.json não encontrado em Never-Land-Essencial.mcpack")
    
    with open(manifest_essencial, 'r', encoding='utf-8-sig') as f:
        data = json.load(f)
    print(f"  -> Validado: {data.get('header', {}).get('name')} (UUID: {data.get('header', {}).get('uuid')})")
    
    # Cria arquivo tar.gz para envio rápido e preservação de permissões Linux
    essencial_tar = TEMP_DIR / "NeverLandEssencial.tar.gz"
    with tarfile.open(essencial_tar, "w:gz") as tar:
        tar.add(essencial_dir, arcname="NeverLandEssencial")
    print(f"  -> Pacote gerado: {essencial_tar} ({essencial_tar.stat().st_size} bytes)")
    
    print("\n[2/4] Processando Advanced Dynamic Lighting V6...")
    lighting_dir = TEMP_DIR / "AdvancedDynamicLighting_v6_BP"
    lighting_dir.mkdir(parents=True, exist_ok=True)
    
    with zipfile.ZipFile(LIGHTING_MCADDON, 'r') as z:
        # Localiza o prefixo da pasta do Behavior Pack
        bp_prefix = "Advanced Dynamic Lighting BP/"
        for member in z.namelist():
            if member.startswith(bp_prefix) and member != bp_prefix:
                rel_path = member[len(bp_prefix):]
                dest_path = lighting_dir / rel_path
                if member.endswith('/'):
                    dest_path.mkdir(parents=True, exist_ok=True)
                else:
                    dest_path.parent.mkdir(parents=True, exist_ok=True)
                    with z.open(member) as src, open(dest_path, 'wb') as dst:
                        shutil.copyfileobj(src, dst)
                        
    manifest_lighting = lighting_dir / "manifest.json"
    if not manifest_lighting.exists():
        raise FileNotFoundError("manifest.json não encontrado no BP de Advanced Dynamic Lighting")
        
    with open(manifest_lighting, 'r', encoding='utf-8-sig') as f:
        data_light = json.load(f)
    print(f"  -> Validado: {data_light.get('header', {}).get('name')} (UUID: {data_light.get('header', {}).get('uuid')})")
    
    lighting_tar = TEMP_DIR / "AdvancedDynamicLighting_v6_BP.tar.gz"
    with tarfile.open(lighting_tar, "w:gz") as tar:
        tar.add(lighting_dir, arcname="AdvancedDynamicLighting_v6_BP")
    print(f"  -> Pacote gerado: {lighting_tar} ({lighting_tar.stat().st_size} bytes)")
    
    print("\n[3/4] Validação de integridade concluída com sucesso!")

if __name__ == "__main__":
    prepare_packages()
