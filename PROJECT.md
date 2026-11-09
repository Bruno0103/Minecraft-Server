# Project: Fusão Colourful Containers BE + The Ty-el's UI Pack

## Architecture
Integração direta e modular das ajudas utilitárias do **The Ty-el's UI Pack** diretamente na framework VDX do **Colourful Containers BE: Mixed (v4.4.1)** (`ColourfulC`), mantendo 100% dos contêineres coloridos com texturas temáticas de madeira e materiais, desbloqueando suporte híbrido completo para PC (Teclado/Mouse) e Mobile (Toque) e organizando os utilitários em uma aba lateral vertical compacta.

- **Camada Visual Base**: `Colourful Containers BE: Mixed` define as telas de contêineres (`chest`, `inventory`, `crafting_table`, `villager`, `furnace`, etc.) sob o namespace `vdx/`.
- **Aba Lateral Compacta**: Componente `container_side_tools_tab` em `vdx/ui_data/container/_modules/container_touch_buttons.uidx` ancorado verticalmente na lateral do contêiner (`anchor_from: "top_right"`, `anchor_to: "top_right"` com offset compacto ou ancorado lateralmente), agregando os seletores dos modos `Normal`, `Auto Place` e `Drop Place`.
- **Motor de Eventos de Item**: `vdx/ui_data/container/_modules/container_item.uidx` gerencia a rota de clique dos slots de itens com base no modo selecionado.
- **Mecanismo de Quick Craft**: Setas interativas em `crafting_table.uidx` (3x3) e `inventory.uidx` (2x2) acionando `button.crafting_output_tertiary` com tooltips explicativos de hover.
- **Rótulo Universal de Durabilidade**: Injeção de label numérico em `vdx/ui_data/_common/durability_bar.uidx`, propagando automaticamente para todos os slots do jogo (`container_item_dx.container_item`).
- **Assistente de Trocas Expressas**: `vdx/ui_data/container/villager.uidx` com o botão `quick_trade_button` cobrindo a seta com binding de validação `#trade_button_enabled`.
- **Ordem de Prioridade no Mundo**: `world_resource_packs.json` assegurando que `ColourfulC` (`91ac35bf-55bc-46e1-baf8-e6c987a25ab5`) prevaleça sobre `TheTy-el's` (`7a7c9df4-0f1d-4644-8539-2fcd438cd150`).

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Desbloqueio Híbrido PC/Mobile | Remover o bloqueio `$touch_ref_buttons: "$touch"` tornando os botões e alternadores utilizáveis em PC e Mobile | M1 | Survey Colourful & Ty-el |
| 2 | Aba Lateral Vertical Compacta | Criar painel vertical compacto com botões `Normal`, `Auto Place` e `Drop Place` ancorado à lateral do contêiner | M1 | Survey Colourful & Ty-el |
| 3 | Resposta de Slots aos Modos | Vincular eventos dos slots em `container_item.uidx` aos botões de controle para PC, Toque e Controle | M1 | Survey Colourful & Ty-el |
| 4 | Quick Craft 3x3 (Bancada) | Botão interativo na seta de craft 3x3 com tooltip flutuante e atalho para `button.crafting_output_tertiary` | M2 | Survey Colourful & Ty-el |
| 5 | Quick Craft 2x2 (Inventário) | Seta de craft 2x2 com tooltip e injeção de `$auto_place_mode_button_name` no slot de saída de inventário | M2 | Survey Colourful & Ty-el |
| 6 | Durabilidade Numérica em Slots | Label numérico exibindo durabilidade restante sobre ferramentas e armaduras danificadas em `durability_bar.uidx` | M3 | Survey Colourful & Ty-el |
| 7 | Durabilidade Numérica em Cursor | Label numérico no item segurado pelo cursor (`durability_bar_selected_icon`) | M3 | Survey Colourful & Ty-el |
| 8 | Assistente de Troca Contínua (Aldeões) | Botão express de troca cobrindo a seta de comércio com tooltip explicativo e validação `#trade_button_enabled` | M4 | Survey Colourful & Ty-el |
| 9 | Validação de Ordem dos Resource Packs | Garantir precedência do `ColourfulC` sobre o `TheTy-el's` no mundo de teste `SoEV1X58JbA=` | M5 | Survey World |
| 10 | Verificação Integrada E2E | Validação sintática JSON/UIDX, integridade de bindings, integridade de texturas e ausência de regressões | M5 | Survey World & Plan |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Aba Lateral Vertical e Modos de Inserção | Refatorar `container_touch_buttons.uidx` e `container_item.uidx` para criar a aba lateral compacta e desbloquear Normal/Auto/Drop modes no PC/Mobile | Survey | DONE (Gate PASS: 2 Reviewers, 2 Challengers, Auditor CLEAN) |
| M2 | Quick Crafting na Bancada e Inventário | Ajustar `crafting_table.uidx` e `inventory.uidx` com hover, tooltip "Fabricar Tudo" e bindings corretos na seta e slot de saída | M1 | DONE (Gate PASS: 2 Reviewers, 2 Challengers, Auditor CLEAN) |
| M3 | Durabilidade Numérica dos Itens | Injetar rótulo numérico com bindings de coleção e sombra em `durability_bar.uidx` para slots e cursor | M1 | DONE (Gate PASS: 2 Reviewers, 2 Challengers, Auditor CLEAN) |
| M4 | Assistente de Trocas com Aldeões | Implementar tooltip, bindings e botão de troca expressa em `villager.uidx` | M1 | PLANNED |
| M5 | Ordem de Packs no Mundo e Validação E2E | Validar ordem em `world_resource_packs.json`, testes sintáticos e conformidade integrada | M1, M2, M3, M4 | PLANNED |

---

## Interface Contracts

### Aba Lateral de Modos (`touch_buttons_dx`) ↔ Slots de Itens (`container_item_dx`)
- **Toggle Group**: `$toggle_name`: `"inventory_mode_toggle"`, grupo de rádio (`$radio_toggle_group: true`).
- **Controles de Visibilidade (Bindings de Visualização)**:
  - `normal_place_toggle`: Modo Normal (padrão)
  - `auto_place_toggle`: Modo Auto Place (`to_button_id: "$auto_place_mode_button_name"`)
  - `drop_place_toggle`: Modo Drop Place (`to_button_id: "button.drop_all"`)
- **Variável de Controle Global**: `$touch_ref_buttons` com valor `true` (desacoplado de `$touch`).

### Seta de Crafting (`crafting_table_mixed_dx` / `inventory_mixed_dx`) ↔ Motor de Crafting
- **Evento Disparado**: `to_button_id: "button.crafting_output_tertiary"` no `button.menu_select` e `button.menu_ok`.
- **Property Bag**: `{ "#collection_name": "crafting_output_items", "#collection_index": 0 }`.
- **Tooltip**: `tooltip_dx.hover_text_panel` com texto localizável `"controller.buttonTip.craftAll"`.

### Barra de Durabilidade (`durability_bar_dx`) ↔ Coleção de Itens Bedrock
- **Collection**: `$item_collection_name` (definida em cada contêiner).
- **Bindings**:
  - `#item_durability_current_amount` -> `#durability_number_text` (texto do rótulo)
  - `#touch_progress_bar_visible` -> `#visible` (visível apenas em itens danificados)
- **Camada**: `layer: 12`, `font_size: "small"`, `shadow: true`, `anchor: "top_middle"`.

### Tela de Aldeão (`villager_mixed_dx`) ↔ Sistema de Negociação
- **Evento Disparado**: `to_button_id: "button.container_auto_place"`.
- **Property Bag**: `{ "#collection_name": "trade2_result_item", "#collection_index": 0 }`.
- **Validação de Estoque**: Binding `#trade_button_enabled` -> `#enabled`.

---

## Code Layout
Todos os arquivos a serem modificados pertencem ao pacote `Colourful Containers BE: Mixed` localizado em:
`C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\Shared\games\com.mojang\resource_packs\ColourfulC\`

- `vdx/ui_data/container/_modules/container_touch_buttons.uidx`: Aba lateral e botões de modo.
- `vdx/ui_data/container/_modules/container_item.uidx`: Mapeamentos de slots e variável `$touch_ref_buttons`.
- `vdx/ui_data/container_mixed/crafting_table.uidx`: Seta de craft 3x3 e tooltip.
- `vdx/ui_data/container_mixed/inventory.uidx`: Seta de craft 2x2, tooltip e slot de saída.
- `vdx/ui_data/_common/durability_bar.uidx`: Label numérico de durabilidade em itens danificados.
- `vdx/ui_data/container/villager.uidx`: Quick trade na tela de aldeões.
- `C:\Users\Bruno\AppData\Roaming\Minecraft Bedrock\Users\10331219279869908448\games\com.mojang\minecraftWorlds\SoEV1X58JbA=\world_resource_packs.json`: Ordem dos resource packs no mundo de teste.

**Regra de Comentários**: Todo código inserido ou modificado deve ser extensamente comentado e explicado em português do Brasil, mantendo os créditos e licença do autor original (CrisXolt).
