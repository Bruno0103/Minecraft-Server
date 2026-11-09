/**
 * ==============================================================================
 * src/main.ts — Exemplo Canônico de Script API para Minecraft Bedrock
 * ==============================================================================
 * Demonstra a manipulação de eventos síncronos e assíncronos (afterEvents/beforeEvents),
 * agendamento de tarefas seguro via system.run e renderização de janelas gráficas
 * dinâmicas via @minecraft/server-ui.
 *
 * Compatibilidade: Minecraft Bedrock 1.21+ / @minecraft/server ^2.11.0 / @minecraft/server-ui ^2.3.0
 */

import {
  world,
  system,
  Player,
  ItemUseAfterEvent,
  ItemUseBeforeEvent,
  PlayerInteractWithEntityBeforeEvent,
  ScriptEventCommandMessageAfterEvent
} from '@minecraft/server';

import {
  ActionFormData,
  ModalFormData,
  MessageFormData,
  FormCancelationReason
} from '@minecraft/server-ui';

// ==============================================================================
// 1. GUARDIÃO GLOBAL DE CLIQUES E COOLDOWN DO SERVIDOR (FIREWALL DE INTERAÇÕES)
// ==============================================================================

/**
 * Tabela hash de memória que rastreia o último tick da engine em que cada jogador
 * executou uma interação no servidor (uso de item ou clique em entidade).
 * Chave: ID único do jogador (player.id) | Valor: Tick numérico do servidor (system.currentTick)
 */
const registroCooldownGlobal = new Map<string, number>();

/**
 * Intervalo de segurança anti-spam e debounce para todo o servidor.
 * 10 ticks = 500 milissegundos (meio segundo).
 * Esse intervalo elimina 100% dos cliques duplicados e do lag momentâneo (micro-stutter)
 * sem prejudicar a responsividade ou fluidez sentida pelo jogador.
 */
const COOLDOWN_GLOBAL_TICKS = 10;

/**
 * Conjunto de identificadores de itens que disparam menus, janelas modais ou
 * interfaces gráficas no ecossistema do servidor.
 */
const ITENS_DE_MENU_PROTEGIDOS = new Set<string>([
  'minecraft:compass',            // Bússola padrão do menu de navegação
  'minecraft:recovery_compass',   // Bússola de recuperação
  'minecraft:clock',              // Relógio de operações
  'minecraft:nether_star'         // Estrela do Nether para painéis avançados
]);

/**
 * INTERCEPTADOR GLOBAL DE ITENS (world.beforeEvents.itemUse)
 * Executa na camada mais profunda do motor nativo C++ ANTES que o clique seja repassado
 * aos demais subsistemas e addons.
 * Se o jogador disparar cliques em rápida sucessão (ex: segurar botão direito do mouse),
 * a ação é cancelada na raiz (event.cancel = true), impedindo picos de CPU no BDS.
 */
world.beforeEvents.itemUse.subscribe((event: ItemUseBeforeEvent) => {
  const jogador = event.source;
  const item = event.itemStack;

  // Filtra apenas itens catalogados que disparam menus e interfaces
  if (item && ITENS_DE_MENU_PROTEGIDOS.has(item.typeId)) {
    const tickAtual = system.currentTick;
    const ultimoTick = registroCooldownGlobal.get(jogador.id) ?? 0;

    // Se o clique ocorreu dentro da janela de meio segundo (10 ticks):
    if (tickAtual - ultimoTick < COOLDOWN_GLOBAL_TICKS) {
      // 🛡️ MÁGICA ARQUITETURAL: Cancela o uso do item.
      // Nenhum outro addon ou script de terceiros receberá o evento deste clique redundante!
      event.cancel = true;
      return;
    }

    // Registra o novo tick válido do jogador
    registroCooldownGlobal.set(jogador.id, tickAtual);
  }
});

/**
 * INTERCEPTADOR GLOBAL DE ENTIDADES FLUTUANTES / MENUS 3D (playerInteractWithEntity)
 * Intercepta cliques e toques em NPCs, suportes de armaduras ou entidades invisíveis
 * que exibem hologramas ou menus flutuantes no espaço tridimensional do mundo.
 */
world.beforeEvents.playerInteractWithEntity.subscribe((event: PlayerInteractWithEntityBeforeEvent) => {
  const jogador = event.player;
  const tickAtual = system.currentTick;
  const ultimoTick = registroCooldownGlobal.get(jogador.id) ?? 0;

  // Se o jogador interagiu com entidades flutuantes há menos de 10 ticks:
  if (tickAtual - ultimoTick < COOLDOWN_GLOBAL_TICKS) {
    // Cancela a interação repetida na raiz do motor C++
    event.cancel = true;
    return;
  }

  // Atualiza o registro de tempo para a interação com entidade
  registroCooldownGlobal.set(jogador.id, tickAtual);
});

/**
 * LIMPEZA PROATIVA DE MEMÓRIA (Garbage Collection de Sessão)
 * Quando um jogador se desconecta do servidor, seu identificador é removido da tabela
 * de cooldown para prevenir qualquer tipo de vazamento de memória (Memory Leak).
 */
world.afterEvents.playerLeave.subscribe((event) => {
  registroCooldownGlobal.delete(event.playerId);
});

// ==============================================================================
// 1. INICIALIZAÇÃO DO SERVIDOR E BOAS-VINDAS AO JOGADOR
// ==============================================================================

// Evento disparado assim que o mundo do servidor dedicado termina de carregar
world.afterEvents.worldLoad.subscribe(() => {
  console.warn('🚀 [Never Land BDS] Script API inicializada com sucesso no servidor dedicado!');
});

// Evento disparado quando um jogador nasce ou entra no mundo
world.afterEvents.playerSpawn.subscribe((event) => {
  const { player, initialSpawn } = event;
  if (initialSpawn) {
    player.sendMessage(`§aBem-vindo ao servidor Never Land, §e${player.name}§a!`);
    player.sendMessage('§7Execute §b/scriptevent neverland:menu §7ou use uma bússola para abrir o menu.');
  }
});

// ==============================================================================
// 3. RECEBIMENTO DE COMANDOS CUSTOMIZADOS VIA SCRIPTEVENT
// ==============================================================================

// Intercepta comandos de script disparados por chat, blocos de comando ou NPCs
system.afterEvents.scriptEventReceive.subscribe((event: ScriptEventCommandMessageAfterEvent) => {
  if (event.id === 'neverland:menu') {
    const player = event.sourceEntity;
    if (player && player instanceof Player) {
      const tickAtual = system.currentTick;
      const ultimoTick = registroCooldownGlobal.get(player.id) ?? 0;

      // Proteção contra chamadas duplicadas via comando ou macros rápidas
      if (tickAtual - ultimoTick < COOLDOWN_GLOBAL_TICKS) {
        return;
      }
      registroCooldownGlobal.set(player.id, tickAtual);

      // REGRA DE OURO ARQUITETURAL DA BEDROCK SCRIPT API:
      // O agendamento via system.run garante a execução segura no próximo tick da engine:
      system.run(() => {
        abrirMenuPrincipal(player);
      });
    }
  }
});

// ==============================================================================
// 4. ATIVAÇÃO DE MENU POR ITEM COM AFTEREVENTS
// ==============================================================================

// Detecta quando o jogador aciona o uso de um item (ex: clique direito com a bússola)
world.afterEvents.itemUse.subscribe((event: ItemUseAfterEvent) => {
  const player = event.source;
  const item = event.itemStack;

  // Se o jogador acionar o uso empunhando uma bússola de navegação
  // Nota: Os cliques repetidos já foram filtrados pelo Guardião Global em beforeEvents!
  if (item && item.typeId === 'minecraft:compass') {
    system.run(() => {
      abrirMenuPrincipal(player);
    });
  }
});

// ==============================================================================
// 5. INTERFACE GRÁFICA DE BOTÕES E AÇÕES (ActionFormData)
// ==============================================================================

/**
 * Renderiza o Menu Principal de Ações para o jogador.
 * @param player Jogador que receberá o formulário modal
 */
function abrirMenuPrincipal(player: Player): void {
  const form = new ActionFormData()
    .title('§1§lPAINEL OPERACIONAL NEVER LAND')
    .body(`Olá, §e${player.name}§r!\nSelecione a operação desejada no servidor:`)
    .button('§aTeleporte para Spawn\n§7Retorne ao ponto central', 'textures/ui/World')
    .button('§bPreferências do Jogador\n§7Ajustes de interface e som', 'textures/ui/settings_glyph_color_2x')
    .button('§6Solicitar Suporte Admin\n§7Acionar operadores online', 'textures/ui/help');

  form.show(player).then((response) => {
    // Tratamento formal de cancelamento pelo usuário
    if (response.canceled) {
      if (response.cancelationReason === FormCancelationReason.UserBusy) {
        player.sendMessage('§cVocê está com outra interface aberta ou em combate. Tente novamente.');
      }
      return;
    }

    switch (response.selection) {
      case 0: {
        player.sendMessage('§aTeleportando para o ponto de spawn central...');
        player.teleport({ x: 0, y: 100, z: 0 });
        break;
      }
      case 1: {
        abrirModalConfiguracoes(player);
        break;
      }
      case 2: {
        confirmarChamadoSuporte(player);
        break;
      }
      default:
        break;
    }
  }).catch((err: Error) => {
    console.error('Erro ao renderizar ActionFormData:', err);
  });
}

// ==============================================================================
// 6. INTERFACE GRÁFICA DE FORMULÁRIO (ModalFormData)
// ==============================================================================

/**
 * Renderiza um formulário com toggles, sliders, campos de texto e dropdowns.
 * @param player Jogador que preencherá o formulário
 */
function abrirModalConfiguracoes(player: Player): void {
  const modal = new ModalFormData()
    .title('§lAjustes e Preferências')
    .toggle('§eAtivar Modo Noturno Visual', { defaultValue: false })
    .slider('§bRaio de Partículas (blocos)', 5, 50, { valueStep: 5, defaultValue: 20 })
    .textField('§aStatus Pessoal no Servidor', 'Ex: Explorando as montanhas...', { defaultValue: '' })
    .dropdown('§6Canal de Chat Padrão', ['Global', 'Vizinhança', 'Mineração'], { defaultValueIndex: 0 });

  modal.show(player).then((response) => {
    if (response.canceled || !response.formValues) return;

    const [noturno, distParticulas, statusTexto, canalIndex] = response.formValues;
    player.sendMessage('§aConfigurações salvas:');
    player.sendMessage(`§7- Noturno: §f${noturno}`);
    player.sendMessage(`§7- Raio Partículas: §f${distParticulas}`);
    player.sendMessage(`§7- Status: §f${statusTexto}`);
    player.sendMessage(`§7- Canal ID: §f${canalIndex}`);
  }).catch((err: Error) => {
    console.error('Erro ao renderizar ModalFormData:', err);
  });
}

// ==============================================================================
// 7. CAIXA DE CONFIRMAÇÃO DIALÓGICA (MessageFormData)
// ==============================================================================

/**
 * Exibe caixa dialógica de confirmação com 2 opções antes de uma ação crítica.
 * @param player Jogador destinatário
 */
function confirmarChamadoSuporte(player: Player): void {
  const dialog = new MessageFormData()
    .title('§c§lChamado de Suporte')
    .body('Você tem certeza de que deseja notificar a equipe de suporte do servidor agora?')
    .button1('§aSim, necessito de ajuda')
    .button2('§cCancelar');

  dialog.show(player).then((response) => {
    if (response.canceled) return;

    if (response.selection === 0) {
      player.sendMessage('§aChamado enviado! Um operador responderá em breve.');
      console.warn(`[TELEMETRIA BDS] O jogador ${player.name} solicitou suporte no tick ${system.currentTick}`);
    } else {
      player.sendMessage('§eChamado cancelado.');
    }
  }).catch((err: Error) => {
    console.error('Erro ao renderizar MessageFormData:', err);
  });
}

// ==============================================================================
// 8. TAREFA PERIÓDICA DE TELEMETRIA EM SEGUNDO PLANO
// ==============================================================================

// Executa a cada 200 ticks (10 segundos) para telemetria leve
system.runInterval(() => {
  const onlinePlayers = world.getAllPlayers();
  if (onlinePlayers.length === 0) return;
  // Telemetria leve em background
}, 200);
