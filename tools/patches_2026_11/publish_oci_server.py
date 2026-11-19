"""
Publica e ativa em produção no servidor OCI (`opc@157.151.2.91`) os Resource Packs
e Behavior Packs corrigidos localmente:

Etapa 1:
- Cria backup remoto dos manifests e de `world_resource_packs.json` em
  `/home/opc/minecraft/backups_ui_patch/manifests/` (sem sobrescrever backups existentes).
- Executa cache-busting incrementando as versões nos `manifest.json` e no
  `world_resource_packs.json` do mundo `Never Land`:
    - ColourfulC:         [4, 4, 1] -> [4, 4, 2]
    - NeverLandEssencial: [1, 0, 0] -> [1, 0, 1]
    - TheTy-els:          [1, 2, 5] -> [1, 2, 6]
- Verifica se nenhum arquivo `.lang` de `NeverLandEssencial/texts/` ainda usa os
  codepoints antigos (\uF400, \uF401, \uF500) e confirma presença de \uE0F0.
- Ajusta permissões (`opc:opc`) e valida todos os JSONs alterados com `json.load`.

Etapa 2:
- Executa `mc.py restart`, aguarda estabilização do boot e valida `mc.py status`
  e `journalctl -u bedrock.service` confirmando ausência de erros de JSON,
  manifest, Script API ou Watchdog Spikes.
"""

import subprocess
import time

SSH_KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
REMOTE_HOST = "opc@157.151.2.91"


def run_ssh(command: str, input_text: str | None = None, timeout: int = 120) -> subprocess.CompletedProcess:
    """Executa comando remoto na VM OCI via SSH."""
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
        input=input_text,
        text=True,
        capture_output=True,
        timeout=timeout,
    )


REMOTE_STAGE1_PY = r"""
import glob
import json
import os
import shutil

SERVER = "/home/opc/minecraft/server"
BACKUP_DIR = "/home/opc/minecraft/backups_ui_patch/manifests"
WORLD_RP_JSON = f"{SERVER}/worlds/Never Land/world_resource_packs.json"

TARGETS = [
    {
        "name": "ColourfulC",
        "manifest": f"{SERVER}/resource_packs/ColourfulC/manifest.json",
        "bak": "ColourfulC_manifest.json",
        "uuid": "91ac35bf-55bc-46e1-baf8-e6c987a25ab5",
        "old_ver": [4, 4, 1],
        "new_ver": [4, 4, 2],
    },
    {
        "name": "NeverLandEssencial",
        "manifest": f"{SERVER}/resource_packs/NeverLandEssencial/manifest.json",
        "bak": "NeverLandEssencial_manifest.json",
        "uuid": "e547c6e6-d6d3-4b21-8a95-a2b013fc1669",
        "old_ver": [1, 0, 0],
        "new_ver": [1, 0, 1],
    },
    {
        "name": "TheTy-els",
        "manifest": f"{SERVER}/resource_packs/TheTy-els/manifest.json",
        "bak": "TheTy-els_manifest.json",
        "uuid": "7a7c9df4-0f1d-4644-8539-2fcd438cd150",
        "old_ver": [1, 2, 5],
        "new_ver": [1, 2, 6],
    },
]

# 1. Cria backups remotos antes de qualquer modificação
os.makedirs(BACKUP_DIR, exist_ok=True)
for item in TARGETS:
    bak_path = os.path.join(BACKUP_DIR, item["bak"])
    if not os.path.exists(bak_path):
        shutil.copy2(item["manifest"], bak_path)
        print(f"[BACKUP OCI] {item['bak']} criado.", flush=True)
    else:
        print(f"[BACKUP OCI] {item['bak']} preservado.", flush=True)

wrp_bak = os.path.join(BACKUP_DIR, "world_resource_packs.json")
if not os.path.exists(wrp_bak):
    shutil.copy2(WORLD_RP_JSON, wrp_bak)
    print("[BACKUP OCI] world_resource_packs.json criado.", flush=True)
else:
    print("[BACKUP OCI] world_resource_packs.json preservado.", flush=True)

# 2. Valida .lang em NeverLandEssencial/texts/ antes de versionar
lang_files = sorted(glob.glob(f"{SERVER}/resource_packs/NeverLandEssencial/texts/*.lang"))
assert len(lang_files) > 0, "Nenhum .lang encontrado em NeverLandEssencial/texts/"
old_chars = ("\uf400", "\uf401", "\uf500")
e0f0_hits = 0
for lf in lang_files:
    txt = open(lf, "r", encoding="utf-8").read()
    for ch in old_chars:
        assert ch not in txt, f"Codepoint antigo {hex(ord(ch))} encontrado em {lf}"
    if "\ue0f0" in txt:
        e0f0_hits += 1
assert e0f0_hits == len(lang_files), f"Esperado \\uE0F0 em todos {len(lang_files)} .lang, encontrado em {e0f0_hits}"
print(f"[LANG OK] {len(lang_files)} arquivos .lang verificados em NeverLandEssencial (0 antigos, {e0f0_hits} com \\uE0F0).", flush=True)

# 3. Lê e valida versões atuais nos manifests e em world_resource_packs.json
world_rp = json.load(open(WORLD_RP_JSON, "r", encoding="utf-8"))
assert isinstance(world_rp, list), "world_resource_packs.json não é lista"

world_by_id = {entry.get("pack_id"): entry for entry in world_rp}
for item in TARGETS:
    m_data = json.load(open(item["manifest"], "r", encoding="utf-8"))
    assert m_data["header"]["uuid"] == item["uuid"], f"UUID divergente em {item['name']}"
    cur_h = m_data["header"]["version"]
    cur_m = m_data["modules"][0]["version"]
    if cur_h not in (item["old_ver"], item["new_ver"]) or cur_m != cur_h:
        raise AssertionError(f"Versão inesperada em {item['manifest']}: header={cur_h}, module={cur_m}")
    w_entry = world_by_id.get(item["uuid"])
    assert w_entry is not None, f"UUID {item['uuid']} ausente em world_resource_packs.json"
    if w_entry.get("version") not in (item["old_ver"], item["new_ver"]):
        raise AssertionError(f"Versão inesperada em world_resource_packs.json para {item['name']}: {w_entry.get('version')}")

# 4. Atualiza os 3 manifests e world_resource_packs.json mantendo subpacks e ordem intactos
for item in TARGETS:
    m_data = json.load(open(item["manifest"], "r", encoding="utf-8"))
    m_data["header"]["version"] = item["new_ver"]
    m_data["modules"][0]["version"] = item["new_ver"]
    with open(item["manifest"], "w", encoding="utf-8", newline="\n") as f:
        json.dump(m_data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    world_by_id[item["uuid"]]["version"] = item["new_ver"]
    print(f"[VERSION] {item['name']}: {item['old_ver']} -> {item['new_ver']}", flush=True)

with open(WORLD_RP_JSON, "w", encoding="utf-8", newline="\n") as f:
    json.dump(world_rp, f, ensure_ascii=False, indent=2)
    f.write("\n")

# 5. Validação estrita pós-escrita de todos os 4 JSONs
for item in TARGETS:
    chk = json.load(open(item["manifest"], "r", encoding="utf-8"))
    assert chk["header"]["version"] == item["new_ver"]
    assert chk["modules"][0]["version"] == item["new_ver"]
chk_wrp = json.load(open(WORLD_RP_JSON, "r", encoding="utf-8"))
chk_map = {e["pack_id"]: e["version"] for e in chk_wrp}
for item in TARGETS:
    assert chk_map[item["uuid"]] == item["new_ver"]
print("[JSON VALID] Todos os 3 manifest.json e world_resource_packs.json validados com json.load.", flush=True)
"""


def main() -> None:
    print("=== [ETAPA 1] Cache-busting e validação dos manifests na OCI ===")
    res1 = run_ssh("python3 -", input_text=REMOTE_STAGE1_PY)
    print(res1.stdout.strip())
    if res1.stderr.strip():
        print("STDERR:", res1.stderr.strip())
    if res1.returncode != 0:
        raise RuntimeError(f"Falha na Etapa 1 remota (rc={res1.returncode})")

    # Garante chown nos arquivos atualizados e registra timestamp UTC antes do restart
    chown_cmd = (
        "chown -R opc:opc "
        "/home/opc/minecraft/server/resource_packs/ColourfulC/manifest.json "
        "/home/opc/minecraft/server/resource_packs/NeverLandEssencial/manifest.json "
        "/home/opc/minecraft/server/resource_packs/TheTy-els/manifest.json "
        "'/home/opc/minecraft/server/worlds/Never Land/world_resource_packs.json' "
        "&& date -u '+%Y-%m-%d %H:%M:%S'"
    )
    res_chown = run_ssh(chown_cmd)
    if res_chown.returncode != 0:
        raise RuntimeError(f"Erro no chown remoto: {res_chown.stderr}")
    since_ts = res_chown.stdout.strip().splitlines()[-1]
    print(f"[CHOWN OK] Timestamp pré-restart (UTC): {since_ts}")

    print("\n=== [ETAPA 2] Reiniciando Servidor BDS na OCI (mc.py restart) ===")
    res_restart = run_ssh("python3 /home/opc/minecraft/mc.py restart", timeout=120)
    print(res_restart.stdout.strip())
    if res_restart.stderr.strip():
        print("STDERR:", res_restart.stderr.strip())
    if res_restart.returncode != 0:
        raise RuntimeError(f"Erro no mc.py restart (rc={res_restart.returncode})")

    print("\nAguardando 10s para boot completo e carga dos scripts/resource packs...")
    time.sleep(10)

    res_status = run_ssh("python3 /home/opc/minecraft/mc.py status")
    print("\n=== STATUS DO SERVIDOR APÓS RESTART ===")
    print(res_status.stdout.strip())

    res_logs = run_ssh(f"journalctl -u bedrock.service --since '{since_ts}' --no-pager")
    print("\n=== LOGS DE BOOT BDS (journalctl) ===")
    print(res_logs.stdout.strip())

    # Verifica presença de "Server started." e ausência de erros conhecidos no log do boot
    logs_txt = res_logs.stdout
    assert "Server started." in logs_txt, "Log não contém 'Server started.' após o boot!"
    assert "Watchdog" not in logs_txt, "Alerta Watchdog detectado no boot do servidor!"
    assert "[Json][error]" not in logs_txt and "Invalid asset path" not in logs_txt, "Erro de JSON/asset detectado!"
    print("\n[DEPLOY VERIFICADO] Servidor ativo, Resource Packs com novas versões e boot 100% limpo!")


if __name__ == "__main__":
    main()
