"""
Sincroniza os ajustes de Resource Packs (escala 1x em glyph_E0.png e correção de
Bússola/Relógio/IDs 1.26.65 no The Ty-el's UI Pack) para a VM OCI (`opc@157.151.2.91`)
e executa verificação remota de hashes SHA-256 e JSON estrito.

Não reinicia o servidor BDS, pois todas as alterações estão exclusivamente em
Resource Packs (carregados no cliente ao conectar).
"""

import hashlib
import json
import pathlib
import subprocess

SSH_KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
REMOTE_HOST = "opc@157.151.2.91"

SHARED_RP = pathlib.Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs"
)

FILES_TO_SYNC = [
    (
        SHARED_RP / "ColourfulC" / "font" / "glyph_E0.png",
        "/home/opc/minecraft/server/resource_packs/ColourfulC/font/glyph_E0.png",
        "ColourfulC_glyph_E0.png",
    ),
    (
        SHARED_RP / "NeverLandE" / "font" / "glyph_E0.png",
        "/home/opc/minecraft/server/resource_packs/NeverLandEssencial/font/glyph_E0.png",
        "NeverLandEssencial_glyph_E0.png",
    ),
    (
        SHARED_RP / "TheTy-el's" / "ui" / "tyel_lib" / "item_ids.json",
        "/home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_lib/item_ids.json",
        "TheTy-els_item_ids.json",
    ),
    (
        SHARED_RP / "TheTy-el's" / "ui" / "tyel_modules" / "wPTYVhlZTkh=",
        "/home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_modules/wPTYVhlZTkh=",
        "TheTy-els_wPTYVhlZTkh=",
    ),
]


def sha256_file(path: pathlib.Path) -> str:
    """Calcula SHA-256 de um arquivo local."""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_ssh(command: str, timeout: int = 45) -> subprocess.CompletedProcess:
    """Executa comando SSH na VM OCI."""
    return subprocess.run(
        [
            "ssh",
            "-i",
            SSH_KEY,
            "-o",
            "StrictHostKeyChecking=accept-new",
            "-o",
            "ConnectTimeout=15",
            REMOTE_HOST,
            command,
        ],
        text=True,
        capture_output=True,
        timeout=timeout,
    )


def run_scp(local_path: pathlib.Path, remote_path: str, timeout: int = 60) -> subprocess.CompletedProcess:
    """Envia um arquivo local via SCP para a VM OCI."""
    return subprocess.run(
        [
            "scp",
            "-i",
            SSH_KEY,
            "-o",
            "StrictHostKeyChecking=accept-new",
            "-o",
            "ConnectTimeout=15",
            str(local_path),
            f"{REMOTE_HOST}:{remote_path}",
        ],
        text=True,
        capture_output=True,
        timeout=timeout,
    )


def main() -> None:
    # 1. Garante existência local e calcula hashes esperados
    local_hashes: dict[str, str] = {}
    for local_p, remote_p, _ in FILES_TO_SYNC:
        if not local_p.is_file():
            raise FileNotFoundError(f"Arquivo local não encontrado: {local_p}")
        local_hashes[remote_p] = sha256_file(local_p)
        print(f"[LOCAL] {local_p.name:18s} size={local_p.stat().st_size:6d} sha256={local_hashes[remote_p][:12]}...")

    # 2. Cria backup remoto antes de sobrescrever qualquer arquivo
    bak_lines = ["set -e", "mkdir -p /home/opc/minecraft/backups_ui_patch"]
    for _, remote_p, bak_name in FILES_TO_SYNC:
        bak_dest = f"/home/opc/minecraft/backups_ui_patch/{bak_name}"
        bak_lines.append(
            f'if [ -f "{remote_p}" ] && [ ! -f "{bak_dest}" ]; then cp -a "{remote_p}" "{bak_dest}"; echo "[BACKUP OCI] Criado: {bak_name}"; fi'
        )
    res_bak = run_ssh("\n".join(bak_lines))
    if res_bak.stdout.strip():
        print(res_bak.stdout.strip())
    if res_bak.returncode != 0:
        raise RuntimeError(f"Erro no backup remoto: {res_bak.stderr}")

    # 3. Envia os 4 arquivos via SCP
    for local_p, remote_p, _ in FILES_TO_SYNC:
        r = run_scp(local_p, remote_p)
        if r.returncode != 0:
            raise RuntimeError(f"Erro SCP ({local_p.name} -> {remote_p}): {r.stderr}")
        print(f"[SCP] Enviado: {local_p.name} -> {remote_p}")

    # 4. Ajusta permissões, valida JSON estrito, confere parênteses/IDs e calcula SHA-256 remoto
    verify_cmd = r"""
set -e
chown -R opc:opc \
    /home/opc/minecraft/server/resource_packs/ColourfulC \
    /home/opc/minecraft/server/resource_packs/NeverLandEssencial \
    /home/opc/minecraft/server/resource_packs/TheTy-els

python3 -m json.tool /home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_lib/item_ids.json >/dev/null
python3 -m json.tool "/home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_modules/wPTYVhlZTkh=" >/dev/null

python3 - <<'PY'
import json, hashlib

ids = json.load(open("/home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_lib/item_ids.json", encoding="utf-8"))
assert ids["index"]["$minecraft:compass"] == 394
assert ids["index"]["$minecraft:clock"] == 396
assert ids["index"]["$minecraft:recovery_compass"] == 661
print("[OCI JSON] item_ids.json OK -> compass=394, clock=396, recovery_compass=661")

w = json.load(open("/home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_modules/wPTYVhlZTkh=", encoding="utf-8"))
def check_parens(node, trail="$"):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "source_property_name" and isinstance(v, str):
                d = 0
                for ch in v:
                    if ch == "(": d += 1
                    elif ch == ")": d -= 1; assert d >= 0, f"Extra ) at {trail}"
                assert d == 0, f"Unbalanced at {trail}"
            else:
                check_parens(v, f"{trail}.{k}")
    elif isinstance(node, list):
        for i, x in enumerate(node):
            check_parens(x, f"{trail}[{i}]")
check_parens(w)
print("[OCI JSON] wPTYVhlZTkh= OK -> 0 parênteses desbalanceados")

for p in [
    "/home/opc/minecraft/server/resource_packs/ColourfulC/font/glyph_E0.png",
    "/home/opc/minecraft/server/resource_packs/NeverLandEssencial/font/glyph_E0.png",
    "/home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_lib/item_ids.json",
    "/home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_modules/wPTYVhlZTkh=",
]:
    with open(p, "rb") as f:
        print(f"HASH {p} {hashlib.sha256(f.read()).hexdigest()}")
PY

ls -lh \
    /home/opc/minecraft/server/resource_packs/ColourfulC/font/glyph_E0.png \
    /home/opc/minecraft/server/resource_packs/NeverLandEssencial/font/glyph_E0.png \
    /home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_lib/item_ids.json \
    "/home/opc/minecraft/server/resource_packs/TheTy-els/ui/tyel_modules/wPTYVhlZTkh="
"""
    res_ver = run_ssh(verify_cmd)
    print("\n=== VALIDAÇÃO REMOTA OCI ===")
    print(res_ver.stdout.strip())
    if res_ver.stderr.strip():
        print("STDERR:", res_ver.stderr.strip())
    if res_ver.returncode != 0:
        raise RuntimeError(f"Falha na validação remota (rc={res_ver.returncode})")

    # 5. Confere igualdade exata dos hashes locais vs remotos
    for line in res_ver.stdout.splitlines():
        if line.startswith("HASH "):
            _, rpath, rhash = line.split(" ", 2)
            assert local_hashes[rpath] == rhash, f"Hash divergente para {rpath}!"
    print("\n[HASH MATCH] Todos os 4 arquivos na OCI estão 100% idênticos aos arquivos locais!")


if __name__ == "__main__":
    main()
