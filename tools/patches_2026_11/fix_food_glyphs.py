"""
Script de correção dos ícones de comida/discos (Never Land Essencial) e
visibilidade do nível de XP no Modo Criativo (The Ty-el's UI Pack).

Problema resolvido:
1. O pacote NeverLandE usava as páginas font/glyph_F4.png (\uF400, \uF401) e
   font/glyph_F5.png (\uF500), mas o Advanced Minimap sobrescreve glyph_E2..glyph_F8
   com tiles do minimapa, fazendo comidas exibirem "(     - )" no lugar das coxas de fome.
2. Migrando os 3 ícones 16x16 (escalados para 32x32 com NEAREST) para os slots livres
   F0, F1 e F2 de font/glyph_E0.png (\uE0F0, \uE0F1, \uE0F2) e removendo glyph_F4.png /
   glyph_F5.png de NeverLandE, elimina-se 100% da colisão de páginas de fonte.
3. Atualiza progress_text_label em TheTy-el's/ui/hud_screen.json para exibir o nível
   atual na barra de XP também no Modo Criativo (onde #level_number_visible é false).
"""

from __future__ import annotations

import glob
import json
import os
import shutil
import numpy as np
from PIL import Image


SCRATCH_BACKUP_DIR = (
    r"C:\Users\Bruno\.gemini\antigravity\brain\d2e23d7a-561a-4729-a2b9-44cf587a4b41\scratch\backup_neverland_glyphs"
)

COLOURFUL_DIRS = [
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC",
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\10331219279869908448\games\com.mojang\minecraftWorlds\qSzAWL-4eCY=\resource_packs\ColourfulC",
]

NEVERLAND_DIRS = [
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\NeverLandE",
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\10331219279869908448\games\com.mojang\minecraftWorlds\qSzAWL-4eCY=\resource_packs\NeverLandE",
]

TYEL_DIRS = [
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\TheTy-el's",
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\10331219279869908448\games\com.mojang\minecraftWorlds\qSzAWL-4eCY=\resource_packs\TheTy-el's",
]


def backup_original_glyphs(src_neverland_dir: str) -> tuple[str, str]:
    """
    Salva uma cópia de segurança de glyph_F4.png e glyph_F5.png em scratch/.
    Retorna os caminhos (backup_f4, backup_f5).
    """
    os.makedirs(SCRATCH_BACKUP_DIR, exist_ok=True)
    f4_src = os.path.join(src_neverland_dir, "font", "glyph_F4.png")
    f5_src = os.path.join(src_neverland_dir, "font", "glyph_F5.png")

    if not os.path.isfile(f4_src):
        raise FileNotFoundError(f"Arquivo original ausente: {f4_src}")
    if not os.path.isfile(f5_src):
        raise FileNotFoundError(f"Arquivo original ausente: {f5_src}")

    f4_bak = os.path.join(SCRATCH_BACKUP_DIR, "glyph_F4.png")
    f5_bak = os.path.join(SCRATCH_BACKUP_DIR, "glyph_F5.png")
    shutil.copy2(f4_src, f4_bak)
    shutil.copy2(f5_src, f5_bak)
    return f4_bak, f5_bak


def build_updated_glyph_e0(base_e0_path: str, f4_path: str, f5_path: str) -> Image.Image:
    """
    Combina os ícones originais do NeverLandE (F400, F401, F500) na página glyph_E0.png
    em 512x512 nas células F0, F1 e F2 (linha 15, colunas 0, 1 e 2).
    """
    if not os.path.isfile(base_e0_path):
        raise FileNotFoundError(f"Base glyph_E0.png não encontrada: {base_e0_path}")

    im_e0 = Image.open(base_e0_path).convert("RGBA")
    if im_e0.size != (512, 512):
        raise ValueError(f"Esperado glyph_E0.png 512x512, encontrado {im_e0.size}")

    im_f4 = Image.open(f4_path).convert("RGBA")
    im_f5 = Image.open(f5_path).convert("RGBA")
    if im_f4.size != (256, 256) or im_f5.size != (256, 256):
        raise ValueError(f"Esperado glyph_F4/F5 256x256, encontrado F4={im_f4.size}, F5={im_f5.size}")

    # Extrai as células 16x16 originais:
    # - F400 (coxa de fome inteira) = célula (0, 0) -> x=0..16, y=0..16
    # - F401 (meia coxa de fome)    = célula (0, 1) -> x=16..32, y=0..16
    # - F500 (redstone de disco)    = célula (0, 0) -> x=0..16, y=0..16
    icon_full_hunger = im_f4.crop((0, 0, 16, 16)).resize((32, 32), Image.Resampling.NEAREST)
    icon_half_hunger = im_f4.crop((16, 0, 32, 16)).resize((32, 32), Image.Resampling.NEAREST)
    icon_redstone = im_f5.crop((0, 0, 16, 16)).resize((32, 32), Image.Resampling.NEAREST)

    # Garante que as células destino F0, F1 e F2 estão limpas antes de colar
    arr_e0 = np.array(im_e0)
    for col in (0, 1, 2):
        cell_alpha = arr_e0[480:512, col * 32 : (col + 1) * 32, 3]
        if cell_alpha.max() > 0:
            raise RuntimeError(f"Célula destino F{col} em {base_e0_path} não está vazia!")

    # Insere na linha 15 (y=480..512), colunas 0, 1 e 2 (\uE0F0, \uE0F1, \uE0F2)
    im_e0.paste(icon_full_hunger, (0, 480))
    im_e0.paste(icon_half_hunger, (32, 480))
    im_e0.paste(icon_redstone, (64, 480))
    return im_e0


def verify_glyph_e0_cells(e0_path: str) -> None:
    """
    Valida que o arquivo PNG salvo existe, é 512x512 e tem pixels não-transparentes em F0, F1, F2.
    """
    im = Image.open(e0_path).convert("RGBA")
    if im.size != (512, 512):
        raise ValueError(f"Dimensão inesperada em {e0_path}: {im.size}")
    arr = np.array(im)
    for col in (0, 1, 2):
        cell_alpha = arr[480:512, col * 32 : (col + 1) * 32, 3]
        if cell_alpha.max() == 0:
            raise RuntimeError(f"Célula F{col} ficou vazia após salvar {e0_path}")


def patch_neverland_langs(neverland_dir: str) -> int:
    """
    Substitui \uF400 -> \uE0F0, \uF401 -> \uE0F1 e \uF500 -> \uE0F2 em todos texts/*.lang.
    Retorna a quantidade de arquivos atualizados.
    """
    lang_files = sorted(glob.glob(os.path.join(neverland_dir, "texts", "*.lang")))
    if not lang_files:
        raise FileNotFoundError(f"Nenhum .lang encontrado em {neverland_dir}")

    replacements = {
        "\uf400": "\ue0f0",
        "\uf401": "\ue0f1",
        "\uf500": "\ue0f2",
    }

    updated = 0
    for lang_path in lang_files:
        with open(lang_path, "r", encoding="utf-8") as f:
            content = f.read()
        new_content = content
        for old_ch, new_ch in replacements.items():
            new_content = new_content.replace(old_ch, new_ch)
        if new_content != content:
            with open(lang_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(new_content)
            updated += 1
    return updated


def patch_tyel_creative_xp_level(tyel_dir: str) -> None:
    """
    Atualiza progress_text_label em ui/hud_screen.json para exibir o nível atual
    também no Modo Criativo (onde #level_number_visible nativo é false).
    """
    hud_path = os.path.join(tyel_dir, "ui", "hud_screen.json")
    if not os.path.isfile(hud_path):
        raise FileNotFoundError(f"hud_screen.json não encontrado: {hud_path}")

    with open(hud_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    data["progress_text_label"] = {
        "bindings": [
            {
                "binding_name": "#level_number",
                "binding_type": "global",
            },
            {
                "binding_type": "view",
                "source_property_name": "(not (#level_number = '') and not (#level_number = '0'))",
                "target_property_name": "#visible",
            },
        ]
    }

    with open(hud_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # Validação pós-escrita
    with open(hud_path, "r", encoding="utf-8") as f:
        json.load(f)


def main() -> None:
    # 1. Faz backup dos glifos originais a partir do NeverLandE principal
    f4_bak, f5_bak = backup_original_glyphs(NEVERLAND_DIRS[0])
    print(f"Backup criado em: {SCRATCH_BACKUP_DIR}")

    # 2. Gera o novo glyph_E0.png a partir do ColourfulC base
    base_e0_path = os.path.join(COLOURFUL_DIRS[0], "font", "glyph_E0.png")
    updated_e0 = build_updated_glyph_e0(base_e0_path, f4_bak, f5_bak)

    # 3. Salva o glyph_E0.png em todas as cópias de ColourfulC e NeverLandE
    for pack_dir in COLOURFUL_DIRS + NEVERLAND_DIRS:
        if not os.path.isdir(pack_dir):
            raise FileNotFoundError(f"Diretório não encontrado: {pack_dir}")
        font_dir = os.path.join(pack_dir, "font")
        os.makedirs(font_dir, exist_ok=True)
        e0_dest = os.path.join(font_dir, "glyph_E0.png")
        updated_e0.save(e0_dest, format="PNG")
        verify_glyph_e0_cells(e0_dest)
        print(f"glyph_E0.png salvo e validado: {e0_dest}")

    # 4 e 5. Remove glyph_F4.png / glyph_F5.png e atualiza texts/*.lang no NeverLandE
    for nl_dir in NEVERLAND_DIRS:
        for old_glyph in ("glyph_F4.png", "glyph_F5.png"):
            old_path = os.path.join(nl_dir, "font", old_glyph)
            if os.path.exists(old_path):
                os.remove(old_path)
                print(f"Removido glifo conflitante: {old_path}")
        count = patch_neverland_langs(nl_dir)
        print(f"Arquivos .lang atualizados em {nl_dir}: {count}")

    # 6. Verifica item.pumpkin_pie.name no pt_BR.lang de cada NeverLandE
    for nl_dir in NEVERLAND_DIRS:
        pt_br = os.path.join(nl_dir, "texts", "pt_BR.lang")
        with open(pt_br, "r", encoding="utf-8") as f:
            lines = [ln for ln in f.read().splitlines() if ln.startswith("item.pumpkin_pie.name=")]
        expected = "item.pumpkin_pie.name=Torta de Abóbora §f(\ue0f0\ue0f0\ue0f0\ue0f0)"
        if lines != [expected]:
            raise AssertionError(f"Linha inesperada em {pt_br}: {lines!r}")
        print(f"Verificado pt_BR.lang ({pt_br}): {lines[0]} -> codepoints {[hex(ord(c)) for c in lines[0] if ord(c) >= 0xE000]}")

    # 7. Atualiza progress_text_label no TheTy-el's para exibir nível no Criativo
    for tyel_dir in TYEL_DIRS:
        if not os.path.isdir(tyel_dir):
            raise FileNotFoundError(f"Diretório Ty-el não encontrado: {tyel_dir}")
        patch_tyel_creative_xp_level(tyel_dir)
        print(f"progress_text_label atualizado e validado em: {tyel_dir}")


if __name__ == "__main__":
    main()
