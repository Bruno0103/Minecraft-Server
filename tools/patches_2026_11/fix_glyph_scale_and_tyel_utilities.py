"""
Script de correção conjunta:
1. Ajusta a escala dos ícones de comida (coxas de fome) e redstone em
   `font/glyph_E0.png` (ColourfulC e NeverLandE) para escala 1x nativa (9x9 e 8x7
   pixels reais da arte), evitando a distorção 2x causada pela página E0.
2. Corrige a exibição da Bússola, Relógio e Bússola de Recuperação no canto do HUD
   em `The Ty-el's UI Pack`:
   - Atualiza `ui/tyel_lib/item_ids.json` com os IDs numéricos exatos do Bedrock
     26.60 / 1.26.65 (compass=394, clock=396, recovery_compass=661, etc.).
   - Corrige erro de parênteses desbalanceados e soma booleana em
     `dynamic_utility_item_renderer_template` (`ui/tyel_modules/wPTYVhlZTkh=`).
   - Elimina deadlock circular de `#visible` em `utility_artifacts_panel`,
     transferindo o controle de visibilidade para `utility_artifacts_bg`,
     corrigindo `padding_hack_middle` e atualizando o fallback do modo Legacy.

Mantém backups originais em `scratch/` e valida todos os PNGs e JSONs gravados.
"""

from __future__ import annotations

import json
import os
import shutil
from typing import Any

import numpy as np
from PIL import Image


# Diretórios de backup em scratch/
BAK_GLYPHS_ORIGINAL = (
    r"C:\Users\Bruno\.gemini\antigravity\brain\d2e23d7a-561a-4729-a2b9-44cf587a4b41\scratch\backup_neverland_glyphs"
)
BAK_TYEL_UTILITIES = (
    r"C:\Users\Bruno\.gemini\antigravity\brain\d2e23d7a-561a-4729-a2b9-44cf587a4b41\scratch\backup_tyel_utilities"
)

# Pacotes locais alvo (Users\Shared + mundo secundário qSzAWL-4eCY=)
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

# Tabela atualizada de IDs numéricos para Bedrock 26.60 / 1.26.63 / 1.26.65
UPDATED_ITEM_IDS: dict[str, int] = {
    "$minecraft:air": -1,
    "$minecraft:stone": 1,
    "$minecraft:web": 30,
    "$minecraft:deadbush": 32,
    "$minecraft:slime": 165,
    "$minecraft:breeze_rod": 879,
    "$minecraft:stick": 323,
    "$minecraft:compass": 394,
    "$minecraft:clock": 396,
    "$minecraft:recovery_compass": 661,
    "$minecraft:potion": 430,
    "$minecraft:splash_potion": 571,
    "$minecraft:lingering_potion": 572,
    "$minecraft:pufferfish": 267,
    "$minecraft:spider_eye": 278,
    "$minecraft:golden_carrot": 283,
    "$minecraft:nether_wart": 294,
    "$minecraft:gunpowder": 331,
    "$minecraft:redstone": 376,
    "$minecraft:glowstone_dust": 397,
    "$minecraft:blaze_powder": 433,
    "$minecraft:sugar": 419,
    "$minecraft:ghast_tear": 428,
    "$minecraft:fermented_spider_eye": 432,
    "$minecraft:magma_cream": 434,
    "$minecraft:glistering_melon_slice": 438,
    "$minecraft:rabbit_foot": 538,
    "$minecraft:dragon_breath": 570,
    "$minecraft:turtle_helmet": 583,
    "$minecraft:phantom_membrane": 584,
    "$minecraft:compound": 610,
    "$minecraft:medicine": 615,
    "$minecraft:element_20": -31,
    "$minecraft:element_27": -38,
    "$minecraft:element_47": -58,
    "$minecraft:element_83": -94,
}


def strip_js_comments_outside_strings(text: str) -> str:
    """
    Remove comentários // e /* ... */ fora de literais de string JSON.
    Necessário para decodificar arquivos JSON ofuscados do The Ty-el's UI Pack.
    """
    out: list[str] = []
    i = 0
    n = len(text)
    in_string = False

    while i < n:
        ch = text[i]
        if in_string:
            out.append(ch)
            if ch == "\\" and i + 1 < n:
                i += 1
                out.append(text[i])
            elif ch == '"':
                in_string = False
            i += 1
            continue

        if ch == '"':
            in_string = True
            out.append(ch)
            i += 1
            continue

        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            end = text.find("*/", i + 2)
            if end == -1:
                break
            i = end + 2
            continue

        if ch == "/" and i + 1 < n and text[i + 1] == "/":
            end = text.find("\n", i + 2)
            if end == -1:
                break
            i = end
            continue

        out.append(ch)
        i += 1

    return "".join(out)


def load_tyel_json(file_path: str) -> dict[str, Any]:
    """
    Lê e decodifica o JSON raiz de um módulo do The Ty-el's UI Pack usando raw_decode
    para ignorar o `{}` vazio final adicionado pela ofuscação original.
    """
    with open(file_path, "r", encoding="utf-8", errors="strict") as f:
        raw = f.read()
    clean = strip_js_comments_outside_strings(raw).lstrip("\ufeff \t\r\n")
    obj, _ = json.JSONDecoder().raw_decode(clean)
    if not isinstance(obj, dict):
        raise ValueError(f"Raiz JSON inesperada em {file_path}")
    return obj


def backup_before_patch() -> None:
    """
    Garante backup dos arquivos de entrada originais/anteriores antes das modificações.
    Não sobrescreve o backup se já tiver sido criado anteriormente.
    """
    os.makedirs(BAK_TYEL_UTILITIES, exist_ok=True)
    targets = [
        ("glyph_E0.before_scale_fix.png", os.path.join(COLOURFUL_DIRS[0], "font", "glyph_E0.png")),
        ("item_ids.json", os.path.join(TYEL_DIRS[0], "ui", "tyel_lib", "item_ids.json")),
        ("wPTYVhlZTkh=", os.path.join(TYEL_DIRS[0], "ui", "tyel_modules", "wPTYVhlZTkh=")),
    ]
    for dst_name, src_path in targets:
        if not os.path.isfile(src_path):
            raise FileNotFoundError(f"Arquivo de origem ausente para backup: {src_path}")
        dst_path = os.path.join(BAK_TYEL_UTILITIES, dst_name)
        if not os.path.exists(dst_path):
            shutil.copy2(src_path, dst_path)
            print(f"[BACKUP] Salvo: {dst_path}")
        else:
            print(f"[BACKUP] Preservado existente: {dst_path}")


def build_scaled_glyph_e0() -> Image.Image:
    """
    Constrói o `glyph_E0.png` (512x512, células 32x32) inserindo os ícones do
    NeverLandE em escala 1x nativa (sem upscale 2x), centralizados verticalmente
    com o texto da tooltip na linha 15 (F0, F1, F2).
    """
    f4_bak = os.path.join(BAK_GLYPHS_ORIGINAL, "glyph_F4.png")
    f5_bak = os.path.join(BAK_GLYPHS_ORIGINAL, "glyph_F5.png")
    base_e0 = os.path.join(COLOURFUL_DIRS[0], "font", "glyph_E0.png")

    for req in (f4_bak, f5_bak, base_e0):
        if not os.path.isfile(req):
            raise FileNotFoundError(f"Arquivo necessário não encontrado: {req}")

    im_f4 = Image.open(f4_bak).convert("RGBA")
    im_f5 = Image.open(f5_bak).convert("RGBA")
    im_e0 = Image.open(base_e0).convert("RGBA")

    if im_f4.size != (256, 256) or im_f5.size != (256, 256):
        raise ValueError(f"Dimensões inesperadas no backup: F4={im_f4.size}, F5={im_f5.size}")
    if im_e0.size != (512, 512):
        raise ValueError(f"Dimensão inesperada para glyph_E0.png: {im_e0.size}")

    # Limpa apenas as 3 células destino F0, F1 e F2 (linha y=480..512, x=0..96)
    arr = np.array(im_e0)
    arr[480:512, 0:96, :] = 0
    im_e0 = Image.fromarray(arr, mode="RGBA")

    # Recortes exatos da arte original 1x (sem resize):
    # - Coxa cheia (9x9): F4 cell (0,0) bbox (0, 3, 9, 12) -> cola em (0, 491) = offset (0, 11) na célula F0
    # - Meia coxa (9x9):  F4 cell (0,1) bbox (16, 3, 25, 12) -> cola em (32, 491) = offset (0, 11) na célula F1
    # - Redstone (8x7):   F5 cell (0,0) bbox (1, 4, 9, 11) -> cola em (65, 492) = offset (1, 12) na célula F2
    full_hunger_1x = im_f4.crop((0, 3, 9, 12))
    half_hunger_1x = im_f4.crop((16, 3, 25, 12))
    redstone_1x = im_f5.crop((1, 4, 9, 11))

    im_e0.paste(full_hunger_1x, (0, 491))
    im_e0.paste(half_hunger_1x, (32, 491))
    im_e0.paste(redstone_1x, (65, 492))
    return im_e0


def verify_glyph_e0_bboxes(png_path: str) -> None:
    """
    Valida que o PNG tem 512x512 e bounding boxes exatos nas células F0, F1 e F2.
    """
    im = Image.open(png_path).convert("RGBA")
    if im.size != (512, 512):
        raise AssertionError(f"Tamanho inválido em {png_path}: {im.size}")

    expected_bboxes = {
        0: (0, 11, 9, 20),  # F0: coxa cheia 9x9
        1: (0, 11, 9, 20),  # F1: meia coxa 9x9
        2: (1, 12, 9, 19),  # F2: pó de redstone 8x7
    }
    for col, exp_bbox in expected_bboxes.items():
        cell = im.crop((col * 32, 480, (col + 1) * 32, 512))
        bbox = cell.getbbox()
        if bbox != exp_bbox:
            raise AssertionError(
                f"BBox divergente na célula F{col} de {png_path}: obtido={bbox}, esperado={exp_bbox}"
            )


def patch_item_ids(tyel_dir: str) -> str:
    """
    Atualiza `ui/tyel_lib/item_ids.json` para os IDs numéricos do Bedrock 26.60/1.26.65.
    Retorna o caminho gravado.
    """
    path = os.path.join(tyel_dir, "ui", "tyel_lib", "item_ids.json")
    obj = load_tyel_json(path)

    if obj.get("namespace") != "tyel_lib__item_ids":
        raise ValueError(f"Namespace inesperado em {path}: {obj.get('namespace')}")
    if "index" not in obj or not isinstance(obj["index"], dict):
        raise ValueError(f"Chave 'index' ausente em {path}")

    for k, new_val in UPDATED_ITEM_IDS.items():
        if k not in obj["index"]:
            raise KeyError(f"Chave esperada '{k}' ausente em {path}")
        obj["index"][k] = new_val

    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # Validação estrita pós-gravação
    with open(path, "r", encoding="utf-8") as f:
        reloaded = json.load(f)
    assert reloaded["index"]["$minecraft:compass"] == 394
    assert reloaded["index"]["$minecraft:clock"] == 396
    assert reloaded["index"]["$minecraft:recovery_compass"] == 661
    assert reloaded["file_validation"] == {"$valid": True}
    return path


def verify_balanced_parens_in_json(obj: Any, trail: str = "$") -> None:
    """
    Percorre recursivamente o objeto JSON verificando se todas as strings de binding
    (`source_property_name` / `#*`) possuem parênteses perfeitamente balanceados.
    """
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "source_property_name" and isinstance(v, str):
                depth = 0
                for ch in v:
                    if ch == "(":
                        depth += 1
                    elif ch == ")":
                        depth -= 1
                        if depth < 0:
                            raise AssertionError(f"Parêntese ')' extra em {trail}.{k}: {v}")
                if depth != 0:
                    raise AssertionError(f"Parênteses desbalanceados em {trail}.{k} (saldo={depth}): {v}")
            else:
                verify_balanced_parens_in_json(v, f"{trail}.{k}")
    elif isinstance(obj, list):
        for idx, item in enumerate(obj):
            verify_balanced_parens_in_json(item, f"{trail}[{idx}]")


def patch_hud_utilities_module(tyel_dir: str) -> str:
    """
    Corrige a lógica de detecção e visibilidade em `ui/tyel_modules/wPTYVhlZTkh=`
    (namespace `tyel_hud_utilities`). Retorna o caminho gravado.
    """
    path = os.path.join(tyel_dir, "ui", "tyel_modules", "wPTYVhlZTkh=")
    obj = load_tyel_json(path)

    if obj.get("namespace") != "tyel_hud_utilities":
        raise ValueError(f"Namespace inesperado em {path}: {obj.get('namespace')}")

    required_keys = [
        "hud_utilities_panel",
        "utility_artifacts_panel",
        "dynamic_utility_item_renderer_template@tyel_common.item_renderer",
        "non_dynamic_utility_item_renderer_stack@tyel_lib__item_ids.index",
    ]
    for rk in required_keys:
        if rk not in obj:
            raise KeyError(f"Controle esperado '{rk}' não encontrado em {path}")

    # 1. Corrige expressão booleana com parêntese extra em dynamic_utility_item_renderer_template
    duir_tpl = obj["dynamic_utility_item_renderer_template@tyel_common.item_renderer"]
    duir_bindings = duir_tpl["bindings"]
    if len(duir_bindings) != 1 or duir_bindings[0].get("target_property_name") != "#visible":
        raise ValueError(f"Estrutura inesperada em bindings de dynamic_utility_item_renderer_template: {duir_bindings}")
    clean_visible_expr = "(" + " or ".join(f"(#id{i:02d} = $idx)" for i in range(36)) + ")"
    assert clean_visible_expr.count("(") == 37 and clean_visible_expr.count(")") == 37
    duir_bindings[0]["source_property_name"] = clean_visible_expr

    # 2. Remove deadlock circular de #visible do painel pai e transfere para `utility_artifacts_bg`
    uap = obj["utility_artifacts_panel"]
    uap.pop("bindings", None)

    new_controls = []
    bg_replaced = False
    for entry in uap.get("controls", []):
        if "background" in entry or "utility_artifacts_bg" in entry:
            new_controls.append(
                {
                    "utility_artifacts_bg": {
                        "type": "image",
                        "texture": "textures/ui/hud_tip_text_background",
                        "size": ["100%sm + 6px", "100%sm + 3px"],
                        "alpha": 0.6,
                        "bindings": [
                            {
                                "binding_type": "view",
                                "source_control_name": "duir_compass",
                                "source_property_name": "#visible",
                                "target_property_name": "#condition_1",
                            },
                            {
                                "binding_type": "view",
                                "source_control_name": "duir_clock",
                                "source_property_name": "#visible",
                                "target_property_name": "#condition_2",
                            },
                            {
                                "binding_type": "view",
                                "source_control_name": "duir_recovery_compass",
                                "source_property_name": "#visible",
                                "target_property_name": "#condition_3",
                            },
                            {
                                "binding_type": "view",
                                "source_property_name": "((not ($hud_utility_artifacts_mode = 0)) or #condition_1 or #condition_2 or #condition_3)",
                                "target_property_name": "#visible",
                            },
                        ],
                    }
                }
            )
            bg_replaced = True
        else:
            new_controls.append(entry)
    if not bg_replaced:
        raise KeyError(f"Controle filho 'background' não encontrado em utility_artifacts_panel de {path}")
    uap["controls"] = new_controls

    # 3. Corrige `padding_hack_middle` para referenciar `utility_artifacts_bg`
    hup = obj["hud_utilities_panel"]
    pad_updated = False
    for entry in hup.get("controls", []):
        if "padding_hack_middle" in entry:
            entry["padding_hack_middle"]["bindings"] = [
                {
                    "binding_type": "view",
                    "source_control_name": "utility_artifacts_bg",
                    "source_property_name": "#visible",
                    "target_property_name": "#visible",
                }
            ]
            pad_updated = True
    if not pad_updated:
        raise KeyError(f"Controle 'padding_hack_middle' não encontrado em hud_utilities_panel de {path}")

    # 4. Atualiza fallback do modo Legacy para usar as variáveis atualizadas de item_ids
    legacy_stack = obj["non_dynamic_utility_item_renderer_stack@tyel_lib__item_ids.index"]
    compass_updated = False
    clock_updated = False
    for entry in legacy_stack.get("controls", []):
        if "compass@tyel_common.item_renderer" in entry:
            entry["compass@tyel_common.item_renderer"]["property_bag"] = {
                "#item_id_aux": "(($minecraft:compass + $item_id_aux_offset) * 65536)"
            }
            compass_updated = True
        elif "clock@tyel_common.item_renderer" in entry:
            entry["clock@tyel_common.item_renderer"]["property_bag"] = {
                "#item_id_aux": "(($minecraft:clock + $item_id_aux_offset) * 65536)"
            }
            clock_updated = True
    if not (compass_updated and clock_updated):
        raise KeyError(f"Controles legacy compass/clock não encontrados em {path}")

    # 5. Corrige parêntese ')' excedente em `arrows_counter_tool_label` (74 '(' vs 75 ')' no pacote original)
    if "arrows_counter_tool_label" in obj:
        arrows_bindings = obj["arrows_counter_tool_label"].get("bindings", [])
        if arrows_bindings:
            src_expr = arrows_bindings[0].get("source_property_name", "")
            if src_expr.endswith("* #i35)))"):
                arrows_bindings[0]["source_property_name"] = src_expr[:-1]
                assert (
                    arrows_bindings[0]["source_property_name"].count("(")
                    == arrows_bindings[0]["source_property_name"].count(")")
                    == 74
                )

    # Verifica balanceamento de parênteses em toda a árvore antes de gravar
    verify_balanced_parens_in_json(obj)

    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # Validação estrita pós-gravação
    with open(path, "r", encoding="utf-8") as f:
        reloaded = json.load(f)
    verify_balanced_parens_in_json(reloaded)
    assert reloaded.get("file_validation") == {"$valid": True}
    return path


def main() -> None:
    print("=== [1/3] Criando backups em scratch ===")
    backup_before_patch()

    print("\n=== [2/3] Corrigindo escala 1x dos ícones em glyph_E0.png ===")
    scaled_e0 = build_scaled_glyph_e0()
    for pack_dir in COLOURFUL_DIRS + NEVERLAND_DIRS:
        if not os.path.isdir(pack_dir):
            raise FileNotFoundError(f"Diretório de pacote não encontrado: {pack_dir}")
        dst_png = os.path.join(pack_dir, "font", "glyph_E0.png")
        os.makedirs(os.path.dirname(dst_png), exist_ok=True)
        scaled_e0.save(dst_png, format="PNG")
        verify_glyph_e0_bboxes(dst_png)
        print(f"  [OK] glyph_E0.png salvo e validado (1x): {dst_png}")

    print("\n=== [3/3] Atualizando IDs 1.26.65 e utility_artifacts em The Ty-el's UI Pack ===")
    for tyel_dir in TYEL_DIRS:
        if not os.path.isdir(tyel_dir):
            raise FileNotFoundError(f"Diretório Ty-el não encontrado: {tyel_dir}")
        p_ids = patch_item_ids(tyel_dir)
        p_hud = patch_hud_utilities_module(tyel_dir)
        print(f"  [OK] item_ids.json validado: {p_ids}")
        print(f"  [OK] wPTYVhlZTkh= validado:  {p_hud}")

    print("\nTodas as modificações locais e validações foram concluídas com sucesso!")


if __name__ == "__main__":
    main()
