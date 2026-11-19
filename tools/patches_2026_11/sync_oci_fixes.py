"""
Sincroniza as correções locais (áudio bogged/step5 + otimizações do Vein Miner e
Effortless Building) para o servidor Bedrock Dedicated Server (BDS) na VM OCI.

Etapas realizadas:
1. Empacota os 18 arquivos de script do Vein Miner e os 6 scripts otimizados do
   Effortless Building em arquivos `.tar.gz` temporários dentro da pasta `scratch`.
2. Cria backup remoto em `/home/opc/minecraft/backups_scripts_patch/` (se ainda
   não existir) antes de sobrescrever qualquer arquivo em produção.
3. Envia via SCP `step5.fsb` para `NeverLandEssencial/sounds/mob/bogged/` e os dois
   arquivos tar.gz para `/tmp/` remoto, extraindo-os sobre os diretórios corretos.
4. Verifica status de `bedrock.service` via `mc.py status`; se ativo, executa
   `mc.py restart`, aguarda estabilização e inspeciona `journalctl` e hashes/tamanhos.
"""

import pathlib
import subprocess
import tarfile
import time

SSH_KEY = r"Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
REMOTE_HOST = "opc@157.151.2.91"

SCRATCH_DIR = pathlib.Path(
    r"C:\Users\Bruno\.gemini\antigravity\brain\d2e23d7a-561a-4729-a2b9-44cf587a4b41\scratch"
)
STEP5_LOCAL = pathlib.Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\NeverLandE\sounds\mob\bogged\step5.fsb"
)
VM_SCRIPTS_LOCAL = pathlib.Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\behavior_packs\VeinMiner&\scripts"
)
EF_SCRIPTS_LOCAL = pathlib.Path(
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\behavior_packs\Effortless\scripts"
)
EF_MODIFIED_RELS = [
    "core/EntityPanel.js",
    "core/GuideBook.js",
    "core/LanguagePicker.js",
    "core/Coach.js",
    "core/Preview.js",
    "main.js",
]


def run_ssh(command: str, timeout: int = 60) -> subprocess.CompletedProcess:
    """Executa comando remoto na VM OCI utilizando a chave SSH do projeto."""
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
    """Envia um arquivo local para a VM OCI via SCP."""
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
    # 1. Valida presença local dos arquivos antes de abrir conexão remota
    if not STEP5_LOCAL.exists() or STEP5_LOCAL.stat().st_size != 13280:
        raise FileNotFoundError(f"Arquivo step5.fsb inválido: {STEP5_LOCAL}")

    # 2. Cria tarballs em scratch contendo apenas os diretórios/arquivos de destino
    vm_tar = SCRATCH_DIR / "veinminer_scripts_opt.tar.gz"
    with tarfile.open(vm_tar, "w:gz") as tar:
        for p in sorted(VM_SCRIPTS_LOCAL.rglob("*.js")):
            rel = p.relative_to(VM_SCRIPTS_LOCAL).as_posix()
            tar.add(p, arcname=rel)
    print(f"[TAR] VeinMiner scripts empacotado: {vm_tar.name} ({vm_tar.stat().st_size} bytes)")

    ef_tar = SCRATCH_DIR / "effortless_scripts_opt.tar.gz"
    with tarfile.open(ef_tar, "w:gz") as tar:
        for rel in EF_MODIFIED_RELS:
            p = EF_SCRIPTS_LOCAL / rel
            tar.add(p, arcname=rel)
    print(f"[TAR] Effortless scripts empacotado: {ef_tar.name} ({ef_tar.stat().st_size} bytes)")

    # 3. Cria diretórios de backup e destino de áudio na OCI
    prep_cmd = """
set -e
mkdir -p /home/opc/minecraft/server/resource_packs/NeverLandEssencial/sounds/mob/bogged
mkdir -p /home/opc/minecraft/backups_scripts_patch
if [ ! -d /home/opc/minecraft/backups_scripts_patch/VeinMiner_BP_scripts ]; then
    cp -a /home/opc/minecraft/server/behavior_packs/VeinMiner_BP/scripts /home/opc/minecraft/backups_scripts_patch/VeinMiner_BP_scripts
    echo "[BACKUP] VeinMiner_BP/scripts salvo na OCI."
fi
if [ ! -d /home/opc/minecraft/backups_scripts_patch/EffortlessBuilding_BP_scripts ]; then
    cp -a /home/opc/minecraft/server/behavior_packs/EffortlessBuilding_BP/scripts /home/opc/minecraft/backups_scripts_patch/EffortlessBuilding_BP_scripts
    echo "[BACKUP] EffortlessBuilding_BP/scripts salvo na OCI."
fi
"""
    res_prep = run_ssh(prep_cmd)
    print(res_prep.stdout.strip())
    if res_prep.returncode != 0:
        raise RuntimeError(f"Falha no preparo remoto: {res_prep.stderr}")

    # 4. Envia step5.fsb e os pacotes tar.gz
    for local_f, remote_f in [
        (STEP5_LOCAL, "/home/opc/minecraft/server/resource_packs/NeverLandEssencial/sounds/mob/bogged/step5.fsb"),
        (vm_tar, "/tmp/veinminer_scripts_opt.tar.gz"),
        (ef_tar, "/tmp/effortless_scripts_opt.tar.gz"),
    ]:
        r = run_scp(local_f, remote_f)
        if r.returncode != 0:
            raise RuntimeError(f"Erro SCP {local_f.name}: {r.stderr}")
        print(f"[SCP] Enviado: {local_f.name} -> {remote_f}")

    # 5. Extrai os pacotes e ajusta permissões nos pacotes alterados
    apply_cmd = """
set -e
tar -xzf /tmp/veinminer_scripts_opt.tar.gz -C /home/opc/minecraft/server/behavior_packs/VeinMiner_BP/scripts
tar -xzf /tmp/effortless_scripts_opt.tar.gz -C /home/opc/minecraft/server/behavior_packs/EffortlessBuilding_BP/scripts
rm -f /tmp/veinminer_scripts_opt.tar.gz /tmp/effortless_scripts_opt.tar.gz
chown -R opc:opc \
    /home/opc/minecraft/server/resource_packs/NeverLandEssencial \
    /home/opc/minecraft/server/behavior_packs/VeinMiner_BP \
    /home/opc/minecraft/server/behavior_packs/EffortlessBuilding_BP
echo "=== REMOTE VERIFY FILES ==="
ls -lh /home/opc/minecraft/server/resource_packs/NeverLandEssencial/sounds/mob/bogged/step5.fsb
grep -c "while(!!\\[\\])" /home/opc/minecraft/server/behavior_packs/VeinMiner_BP/scripts/configs/configs.js || true
grep -n "SWEEP_TICKS" /home/opc/minecraft/server/behavior_packs/EffortlessBuilding_BP/scripts/core/EntityPanel.js
"""
    res_apply = run_ssh(apply_cmd)
    print(res_apply.stdout.strip())
    if res_apply.returncode != 0:
        raise RuntimeError(f"Falha ao extrair na OCI: {res_apply.stderr}")

    # 6. Verifica status do servidor e reinicia apenas se estiver ativo
    res_status = run_ssh("python3 /home/opc/minecraft/mc.py status")
    print("\n=== STATUS ANTES DO RESTART ===")
    print(res_status.stdout.strip())
    if res_status.stderr:
        print("STDERR:", res_status.stderr.strip())

    status_lower = res_status.stdout.lower()
    if "active" in status_lower or "running" in status_lower or res_status.returncode == 0:
        print("\n=== REINICIANDO SERVIDOR BDS (mc.py restart) ===")
        res_restart = run_ssh("python3 /home/opc/minecraft/mc.py restart", timeout=90)
        print(res_restart.stdout.strip())
        if res_restart.stderr:
            print("STDERR:", res_restart.stderr.strip())

        time.sleep(6)
        res_status_after = run_ssh("python3 /home/opc/minecraft/mc.py status")
        print("\n=== STATUS APÓS RESTART ===")
        print(res_status_after.stdout.strip())

        res_logs = run_ssh("journalctl -u bedrock.service -n 30 --no-pager")
        print("\n=== LOGS RECENTES BDS (journalctl -n 30) ===")
        print(res_logs.stdout.strip())
    else:
        print("\n[AVISO] Servidor BDS não está ativo no momento; arquivos já sincronizados.")


if __name__ == "__main__":
    main()
