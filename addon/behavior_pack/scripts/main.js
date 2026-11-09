// addon/src/main.ts
import {
  world,
  system,
  Player
} from "@minecraft/server";
import {
  ActionFormData,
  ModalFormData,
  MessageFormData,
  FormCancelationReason
} from "@minecraft/server-ui";
world.afterEvents.worldLoad.subscribe(() => {
  console.warn("\u{1F680} [Never Land BDS] Script API inicializada com sucesso no servidor dedicado!");
});
world.afterEvents.playerSpawn.subscribe((event) => {
  const { player, initialSpawn } = event;
  if (initialSpawn) {
    player.sendMessage(`\xA7aBem-vindo ao servidor Never Land, \xA7e${player.name}\xA7a!`);
    player.sendMessage("\xA77Execute \xA7b/scriptevent neverland:menu \xA77ou use uma b\xFAssola para abrir o menu.");
  }
});
system.afterEvents.scriptEventReceive.subscribe((event) => {
  if (event.id === "neverland:menu") {
    const player = event.sourceEntity;
    if (player && player instanceof Player) {
      system.run(() => {
        abrirMenuPrincipal(player);
      });
    }
  }
});
world.afterEvents.itemUse.subscribe((event) => {
  const player = event.source;
  const item = event.itemStack;
  if (item && item.typeId === "minecraft:compass") {
    system.run(() => {
      abrirMenuPrincipal(player);
    });
  }
});
function abrirMenuPrincipal(player) {
  const form = new ActionFormData().title("\xA71\xA7lPAINEL OPERACIONAL NEVER LAND").body(`Ol\xE1, \xA7e${player.name}\xA7r!
Selecione a opera\xE7\xE3o desejada no servidor:`).button("\xA7aTeleporte para Spawn\n\xA77Retorne ao ponto central", "textures/ui/World").button("\xA7bPrefer\xEAncias do Jogador\n\xA77Ajustes de interface e som", "textures/ui/settings_glyph_color_2x").button("\xA76Solicitar Suporte Admin\n\xA77Acionar operadores online", "textures/ui/help");
  form.show(player).then((response) => {
    if (response.canceled) {
      if (response.cancelationReason === FormCancelationReason.UserBusy) {
        player.sendMessage("\xA7cVoc\xEA est\xE1 com outra interface aberta ou em combate. Tente novamente.");
      }
      return;
    }
    switch (response.selection) {
      case 0: {
        player.sendMessage("\xA7aTeleportando para o ponto de spawn central...");
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
  }).catch((err) => {
    console.error("Erro ao renderizar ActionFormData:", err);
  });
}
function abrirModalConfiguracoes(player) {
  const modal = new ModalFormData().title("\xA7lAjustes e Prefer\xEAncias").toggle("\xA7eAtivar Modo Noturno Visual", { defaultValue: false }).slider("\xA7bRaio de Part\xEDculas (blocos)", 5, 50, { valueStep: 5, defaultValue: 20 }).textField("\xA7aStatus Pessoal no Servidor", "Ex: Explorando as montanhas...", { defaultValue: "" }).dropdown("\xA76Canal de Chat Padr\xE3o", ["Global", "Vizinhan\xE7a", "Minera\xE7\xE3o"], { defaultValueIndex: 0 });
  modal.show(player).then((response) => {
    if (response.canceled || !response.formValues) return;
    const [noturno, distParticulas, statusTexto, canalIndex] = response.formValues;
    player.sendMessage("\xA7aConfigura\xE7\xF5es salvas:");
    player.sendMessage(`\xA77- Noturno: \xA7f${noturno}`);
    player.sendMessage(`\xA77- Raio Part\xEDculas: \xA7f${distParticulas}`);
    player.sendMessage(`\xA77- Status: \xA7f${statusTexto}`);
    player.sendMessage(`\xA77- Canal ID: \xA7f${canalIndex}`);
  }).catch((err) => {
    console.error("Erro ao renderizar ModalFormData:", err);
  });
}
function confirmarChamadoSuporte(player) {
  const dialog = new MessageFormData().title("\xA7c\xA7lChamado de Suporte").body("Voc\xEA tem certeza de que deseja notificar a equipe de suporte do servidor agora?").button1("\xA7aSim, necessito de ajuda").button2("\xA7cCancelar");
  dialog.show(player).then((response) => {
    if (response.canceled) return;
    if (response.selection === 0) {
      player.sendMessage("\xA7aChamado enviado! Um operador responder\xE1 em breve.");
      console.warn(`[TELEMETRIA BDS] O jogador ${player.name} solicitou suporte no tick ${system.currentTick}`);
    } else {
      player.sendMessage("\xA7eChamado cancelado.");
    }
  }).catch((err) => {
    console.error("Erro ao renderizar MessageFormData:", err);
  });
}
system.runInterval(() => {
  const onlinePlayers = world.getAllPlayers();
  if (onlinePlayers.length === 0) return;
}, 200);
//# sourceMappingURL=main.js.map
