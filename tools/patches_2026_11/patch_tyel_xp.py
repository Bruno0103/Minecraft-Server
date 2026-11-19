"""
Script de correção da exibição de XP e nível atual no pacote The Ty-el's UI Pack.

Aplica os ajustes em:
1. ui/hud_screen.json
2. ui/tyel_modules/yaGJwwiA3Jy=
3. ui/tyel_modules/rEo71U9cEyA=
4. texts/pt_BR.lang e texts/pt_PT.lang

Mantém compatibilidade com Minecraft Bedrock 1.26.65+ e valida todos os JSONs
gravados com json.load após a escrita.
"""

from __future__ import annotations

import json
import os
import re
from typing import Any


# Lista dos diretórios locais onde o pacote The Ty-el's UI Pack está instalado
TARGET_PACK_DIRS = [
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\TheTy-el's",
    r"C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\10331219279869908448\games\com.mojang\minecraftWorlds\qSzAWL-4eCY=\resource_packs\TheTy-el's",
]


def strip_js_comments_outside_strings(text: str) -> str:
    """
    Remove comentários // e /* ... */ apenas fora de literais de string JSON.

    Os arquivos do The Ty-el's UI Pack usam comentários de bloco ofuscados entre
    chaves e valores JSON; esta varredura garante que nada dentro de strings reais
    seja alterado.
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
                # Preserva caractere escapado dentro da string
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

        # Comentário de bloco /* ... */
        if ch == "/" and i + 1 < n and text[i + 1] == "*":
            end = text.find("*/", i + 2)
            if end == -1:
                break
            i = end + 2
            continue

        # Comentário de linha // ...
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
    Lê e decodifica um arquivo JSON ofuscado do Ty-el's UI Pack.

    Ignora o objeto vazio final `{}` usado no pacote original após o JSON raiz.
    """
    with open(file_path, "r", encoding="utf-8", errors="strict") as f:
        raw = f.read()

    cleaned = strip_js_comments_outside_strings(raw).lstrip()
    decoder = json.JSONDecoder()
    obj, idx = decoder.raw_decode(cleaned)
    trailing = cleaned[idx:].strip()

    # Garante que não descartamos conteúdo inesperado no fim do arquivo
    if trailing not in ("", "{}"):
        raise ValueError(f"Conteúdo residual inesperado em {file_path}: {trailing[:80]!r}")
    if not isinstance(obj, dict):
        raise ValueError(f"Objeto raiz inválido em {file_path}: esperado dict")
    return obj


def save_and_validate_json(file_path: str, data: dict[str, Any], compact: bool = False) -> None:
    """
    Grava um dicionário como JSON UTF-8 válido e revalida com json.load.
    """
    with open(file_path, "w", encoding="utf-8", newline="\n") as f:
        if compact:
            # Modo compacto para módulos gigantes (ex: mapa de 29k níveis)
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        else:
            json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # Validação obrigatória pós-gravação
    with open(file_path, "r", encoding="utf-8") as f:
        reloaded = json.load(f)
    if not isinstance(reloaded, dict):
        raise ValueError(f"Falha na validação JSON pós-escrita: {file_path}")


def patch_hud_screen(pack_dir: str) -> None:
    """
    Corrige progress_text_label e exp_progress_percentage_panel em ui/hud_screen.json.
    """
    hud_path = os.path.join(pack_dir, "ui", "hud_screen.json")
    data = load_tyel_json(hud_path)

    # 1. Restaura o binding nativo #level_number_visible em progress_text_label
    # (antes usava `#level_number * 1 = 0`, que ocultava o nível quando #level_number é string)
    data["progress_text_label"] = {
        "bindings": [
            {
                "binding_name": "#level_number",
                "binding_type": "global",
            },
            {
                "binding_name": "#level_number_visible",
                "binding_type": "global",
                "binding_name_override": "#visible",
            },
        ]
    }

    # 2. Mantém o nível sempre centralizado no topo da barra de XP (Desktop e Pocket)
    centered_label_cfg = {
        "$offset|default": [0, 0],
        "$anchor_from|default": "top_middle",
        "$anchor_to|default": "bottom_middle",
        "offset": "$offset",
        "anchor_from": "$anchor_from",
        "anchor_to": "$anchor_to",
    }
    for key in (
        "exp_progress_bar_and_hotbar/resizing_xp_bar_with_hotbar/progress_text_label",
        "exp_progress_bar_and_hotbar_pocket/resizing_xp_bar_with_hotbar/progress_text_label",
    ):
        if key not in data:
            raise KeyError(f"Chave esperada ausente em {hud_path}: {key}")
        data[key] = dict(centered_label_cfg)

    # 3. Atualiza a condição de visibilidade do painel de porcentagem de XP
    resizing_bar = data.get("exp_progress_bar_and_hotbar/resizing_xp_bar_with_hotbar")
    if not isinstance(resizing_bar, dict):
        raise KeyError(f"Chave ausente em {hud_path}: exp_progress_bar_and_hotbar/resizing_xp_bar_with_hotbar")

    updated_pct_visibility = False
    for mod in resizing_bar.get("modifications", []):
        for item in mod.get("value", []):
            if isinstance(item, dict) and "exp_progress_percentage_panel" in item:
                panel = item["exp_progress_percentage_panel"]
                for binding in panel.get("bindings", []):
                    if binding.get("target_property_name") == "#visible":
                        binding["source_property_name"] = (
                            "((not (#level_number = '')) or ((1 - $progress_binding_name) > 0))"
                        )
                        updated_pct_visibility = True
    if not updated_pct_visibility:
        raise RuntimeError(f"Binding #visible de exp_progress_percentage_panel não encontrado em {hud_path}")

    save_and_validate_json(hud_path, data, compact=False)


def patch_xp_calc_map(pack_dir: str) -> None:
    """
    Corrige a conversão de #level_number (string) e adiciona #lvl_num:0..5000 em yaGJwwiA3Jy=.
    """
    map_path = os.path.join(pack_dir, "ui", "tyel_modules", "yaGJwwiA3Jy=")
    data = load_tyel_json(map_path)

    xp_calc_map = data.get("xp_calc_map")
    if not isinstance(xp_calc_map, dict) or not isinstance(xp_calc_map.get("property_bag"), dict):
        raise KeyError(f"Estrutura xp_calc_map inválida em {map_path}")

    # Substitui `('0' + #level_number)` que quebrava níveis >= 1 gerando '01', '02', etc.
    xp_calc_map["bindings"] = [
        {
            "binding_name": "#level_number",
            "binding_type": "global",
        },
        {
            "source_property_name": "(('Z' + (#level_number = '') + ':0' + #level_number) - 'Zfalse:0' - 'Ztrue:')",
            "target_property_name": "#current_level",
            "binding_type": "view",
        },
        {
            "source_property_name": "('#total_xp_lvl:' + #current_level)",
            "target_property_name": "#total_xp_score",
            "binding_type": "view",
        },
        {
            "source_property_name": "('#lvl_num:' + #current_level)",
            "target_property_name": "#lvl_num",
            "binding_type": "view",
        },
    ]

    # Adiciona entradas inteiras #lvl_num:0 .. #lvl_num:5000 preservando #total_xp_lvl:*
    pb = xp_calc_map["property_bag"]
    for lvl in range(0, 5001):
        pb[f"#lvl_num:{lvl}"] = lvl

    save_and_validate_json(map_path, data, compact=True)


def patch_accurate_exp_module(pack_dir: str) -> None:
    """
    Corrige #lvl, o texto de exibição direta de nível e a visibilidade em rEo71U9cEyA=.
    """
    mod_path = os.path.join(pack_dir, "ui", "tyel_modules", "rEo71U9cEyA=")
    data = load_tyel_json(mod_path)

    # 1. Localiza accurate_exp_progress_label_content dentro de accurate_exp_progress_stack
    stack = data.get("accurate_exp_progress_stack")
    if not isinstance(stack, dict):
        raise KeyError(f"accurate_exp_progress_stack ausente em {mod_path}")

    label_content: dict[str, Any] | None = None
    for ctrl in stack.get("controls", []):
        if isinstance(ctrl, dict) and "accurate_exp_progress_label_content" in ctrl:
            label_content = ctrl["accurate_exp_progress_label_content"]
            break
    if label_content is None:
        raise KeyError(f"accurate_exp_progress_label_content ausente em {mod_path}")

    updated_lvl = False
    updated_text = False
    new_bindings: list[dict[str, Any]] = []
    for binding in label_content.get("bindings", []):
        target = binding.get("target_property_name")
        if target == "#lvl":
            # Lê o inteiro convertido do property_bag de vanilla_xp_calc_map
            new_bindings.append(
                {
                    "binding_type": "view",
                    "source_control_name": "vanilla_xp_calc_map",
                    "source_property_name": "#lvl_num",
                    "target_property_name": "#lvl",
                }
            )
            updated_lvl = True
        elif target == "#text":
            # Formata diretamente como "Nível Atual: <lvl> (<atual>/<total> XP) [<total geral>]"
            new_bindings.append(
                {
                    "binding_type": "view",
                    "source_property_name": (
                        "('%hudScreen.levelHeaderAbbreviated: §a' + #lvl + "
                        "' §7(§f' + #current_lvlxp_float_one + '/' + #total_lvlxp + ' XP§7) "
                        "§r§o§7[' + #total_xp_score_unsigned + ']§r')"
                    ),
                    "target_property_name": "#text",
                }
            )
            updated_text = True
        else:
            new_bindings.append(binding)

    if not updated_lvl or not updated_text:
        raise RuntimeError(
            f"Bindings esperados não encontrados em {mod_path} "
            f"(updated_lvl={updated_lvl}, updated_text={updated_text})"
        )
    label_content["bindings"] = new_bindings

    # 2. Atualiza a condição de visibilidade do painel accurate_exp_progress
    acc_panel = data.get("accurate_exp_progress")
    if not isinstance(acc_panel, dict):
        raise KeyError(f"accurate_exp_progress ausente em {mod_path}")

    updated_cond = False
    for binding in acc_panel.get("bindings", []):
        if binding.get("target_property_name") == "#condition":
            binding["source_property_name"] = "((not (#level_number = '')) or ((1 - #exp_progress) > 0))"
            updated_cond = True
    if not updated_cond:
        raise RuntimeError(f"Binding #condition ausente em {mod_path}")

    save_and_validate_json(mod_path, data, compact=False)


def patch_lang_files(pack_dir: str) -> None:
    """
    Atualiza hudScreen.levelHeaderAbbreviated para 'Nível Atual' em pt_BR.lang e pt_PT.lang.
    """
    for lang_name in ("pt_BR.lang", "pt_PT.lang"):
        lang_path = os.path.join(pack_dir, "texts", lang_name)
        if not os.path.exists(lang_path):
            raise FileNotFoundError(f"Arquivo de idioma não encontrado: {lang_path}")

        with open(lang_path, "r", encoding="utf-8") as f:
            lines = f.read().splitlines()

        found = False
        new_lines: list[str] = []
        for line in lines:
            if line.startswith("hudScreen.levelHeaderAbbreviated="):
                new_lines.append("hudScreen.levelHeaderAbbreviated=Nível Atual\t#")
                found = True
            else:
                new_lines.append(line)

        if not found:
            # Insere logo após hudScreen.hideHudTooltip se existir, senão ao final
            inserted = False
            patched: list[str] = []
            for line in new_lines:
                patched.append(line)
                if not inserted and line.startswith("hudScreen.hideHudTooltip="):
                    patched.append("hudScreen.levelHeaderAbbreviated=Nível Atual\t#")
                    inserted = True
            if not inserted:
                patched.append("hudScreen.levelHeaderAbbreviated=Nível Atual\t#")
            new_lines = patched

        with open(lang_path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(new_lines) + "\n")


def main() -> None:
    """Executa todas as correções nas cópias locais configuradas."""
    for pack_dir in TARGET_PACK_DIRS:
        if not os.path.isdir(pack_dir):
            raise FileNotFoundError(f"Diretório do pacote não encontrado: {pack_dir}")
        print(f"Aplicando correções em: {pack_dir}")
        patch_hud_screen(pack_dir)
        patch_xp_calc_map(pack_dir)
        patch_accurate_exp_module(pack_dir)
        patch_lang_files(pack_dir)
        print("  -> OK (todos os JSONs validados)")


if __name__ == "__main__":
    main()
