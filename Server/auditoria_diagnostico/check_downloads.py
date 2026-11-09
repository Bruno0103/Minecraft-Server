# ==============================================================================
# Script de Inspeção de Manifests de Pacotes Locais Baixados
# ==============================================================================
# Inspeciona os arquivos .mcpack e .mcaddon na pasta Downloads para identificar
# os UUIDs, versões, módulos e metadados de cada pacote relevante para o Never Land.
# ==============================================================================

import zipfile
import json
import os

FILES_TO_CHECK = [
    r"C:\Users\Bruno\Downloads\Never-Land-Essencial.mcpack",
    r"C:\Users\Bruno\Downloads\Never-Land-texture-pack.mcpack",
    r"C:\Users\Bruno\Downloads\Advanced Dynamic Lighting v6.1.mcaddon",
    r"C:\Users\Bruno\Downloads\Advanced-Dynamic-Light-v0.2.7-Behavior-By-DARKLEXx.mcpack",
    r"C:\Users\Bruno\Downloads\Advanced-Dynamic-Light-v0.2.7-Resource-By-DARKLEXx.mcpack"
]

def inspect_packs():
    for file_path in FILES_TO_CHECK:
        print("=" * 60)
        print("ARQUIVO:", os.path.basename(file_path))
        if not os.path.exists(file_path):
            print("  [ERRO] Arquivo não encontrado.")
            continue
        try:
            with zipfile.ZipFile(file_path, "r") as zf:
                manifest_files = [n for n in zf.namelist() if n.endswith("manifest.json")]
                print(f"  Manifests encontrados ({len(manifest_files)}): {manifest_files}")
                for mf in manifest_files:
                    try:
                        raw = zf.read(mf).decode("utf-8-sig", errors="ignore")
                        data = json.loads(raw)
                        hdr = data.get("header", {})
                        print(f"  --- [{mf}] ---")
                        print(f"    Nome: {hdr.get('name')}")
                        print(f"    Descrição: {hdr.get('description')}")
                        print(f"    UUID: {hdr.get('uuid')}")
                        print(f"    Versão: {hdr.get('version')}")
                        if "subpacks" in data:
                            print(f"    Subpacks: {[s.get('folder_name') for s in data.get('subpacks', [])]}")
                        for mod in data.get("modules", []):
                            print(f"    Module: type={mod.get('type')}, uuid={mod.get('uuid')}, version={mod.get('version')}")
                    except Exception as e:
                        print(f"    Erro lendo manifest '{mf}': {e}")
        except Exception as e:
            print(f"  Erro ao abrir arquivo zip: {e}")

if __name__ == "__main__":
    inspect_packs()
