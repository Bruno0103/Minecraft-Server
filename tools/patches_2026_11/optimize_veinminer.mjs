/**
 * @file optimize_veinminer.mjs
 * @description Otimiza os módulos JavaScript do add-on "Vein Miner & Tree Capitator"
 * eliminando o gargalo síncrono de inicialização responsável pelos alertas
 * [Watchdog] Spike (~347–374ms) no Minecraft Bedrock 1.26.65+.
 *
 * Causa raiz identificada:
 * 1) Os 17 submódulos usam ofuscação "DL's Obfuscator Software v3.5.0" com um loop
 *    síncrono `while(!![]){ ... push(shift()) }` no topo de cada módulo. No carregador
 *    QuickJS do Bedrock (sem JIT), executar 159 rotações de array com ~1.600
 *    descriptografias Base64+RC4 de uma só vez trava o tick inicial por ~350ms.
 * 2) `utils/Notifications.js` executa `system.runInterval(..., 1)` a cada 1 tick (20x/s)
 *    apenas para monitorar o item na mão e atualizar a ActionBar.
 *
 * Estratégia segura e reversível:
 * - Cria backup integral dos scripts originais em `scratch/backup_veinminer_scripts`.
 * - Pré-computa a rotação exata do array de strings em sandbox V8 (`node:vm`) isolada.
 * - Grava o array já na ordem final rotacionada e remove somente a IIFE do loop `while(!![])`.
 * - Auto-verifica equivalência matemática 100% entre o código original e o otimizado antes
 *   de gravar qualquer arquivo em disco.
 * - Ajusta o intervalo de `utils/Notifications.js` de 1 tick (50ms) para 4 ticks (200ms).
 * - Valida a sintaxe de todos os 18 arquivos com `node --check`.
 */

import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { execFileSync } from "node:child_process";
import { performance } from "node:perf_hooks";

// Diretório de origem do add-on local instalado no Minecraft Bedrock (Users\Shared)
const VEIN_MINER_SCRIPTS_DIR =
  "C:\\Users\\Bruno\\AppData\\Roaming\\Minecraft Bedrock\\Users\\Shared\\games\\com.mojang\\behavior_packs\\VeinMiner&\\scripts";

// Diretório de backup na pasta scratch da conversa
const BACKUP_DIR =
  "C:\\Users\\Bruno\\.gemini\\antigravity\\brain\\d2e23d7a-561a-4729-a2b9-44cf587a4b41\\scratch\\backup_veinminer_scripts";

// Expressão ofuscada exata que avalia para `1` tick no final de `utils/Notifications.js`
const NOTIF_INTERVAL_EXPR = "-0xe6a+-0x1cc3*-0x1+-0xe58";
const NOTIF_INTERVAL_NEW = "4";

/**
 * Percorre recursivamente um diretório retornando todos os caminhos de arquivos `.js`.
 * @param {string} dir - Diretório raiz da busca.
 * @returns {string[]} Lista ordenada de caminhos absolutos.
 */
function walkJsFiles(dir) {
  const results = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...walkJsFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".js")) {
      results.push(fullPath);
    }
  }
  return results.sort();
}

/**
 * Copia recursivamente um diretório apenas se o destino ainda não existir,
 * preservando sempre o backup original intacto em execuções repetidas.
 * @param {string} srcDir - Diretório fonte.
 * @param {string} dstDir - Diretório destino do backup.
 */
function createInitialBackup(srcDir, dstDir) {
  if (fs.existsSync(dstDir)) {
    console.log(`[BACKUP] Backup já existe em: ${dstDir} (preservando originais).`);
    return;
  }
  fs.cpSync(srcDir, dstDir, { recursive: true });
  console.log(`[BACKUP] Backup completo criado em: ${dstDir}`);
}

/**
 * Encontra o índice do delimitador de fechamento correspondente (`}` ou `)` ou `]`),
 * respeitando strings com aspas simples, duplas, template strings e escapes `\\`.
 * @param {string} src - Código-fonte completo.
 * @param {number} startIdx - Índice exato do caractere de abertura.
 * @param {string} openCh - Caractere de abertura (`{`, `(`, `[`).
 * @param {string} closeCh - Caractere de fechamento (`}`, `)`, `]`).
 * @returns {number} Índice do fechamento correspondente ou `-1` se não encontrar.
 */
function findMatchingDelimiter(src, startIdx, openCh, closeCh) {
  let depth = 0;
  let quote = null;
  let escaped = false;

  for (let i = startIdx; i < src.length; i++) {
    const ch = src[i];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (quote) {
      if (ch === "\\") {
        escaped = true;
      } else if (ch === quote) {
        quote = null;
      }
      continue;
    }
    if (ch === "'" || ch === '"' || ch === "`") {
      quote = ch;
      continue;
    }
    if (ch === openCh) {
      depth++;
    } else if (ch === closeCh) {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

/**
 * Extrai a declaração completa e as fronteiras de uma função nomeada no código-fonte.
 * @param {string} src - Código-fonte completo.
 * @param {string} fnName - Nome exato da função.
 * @returns {{ start: number, end: number, code: string } | null} Metadados da função.
 */
function extractNamedFunction(src, fnName) {
  const marker = `function ${fnName}(`;
  const start = src.indexOf(marker);
  if (start === -1) return null;

  const braceStart = src.indexOf("{", start);
  if (braceStart === -1) return null;

  const braceEnd = findMatchingDelimiter(src, braceStart, "{", "}");
  if (braceEnd === -1) return null;

  return {
    start,
    end: braceEnd + 1,
    code: src.slice(start, braceEnd + 1),
  };
}

/**
 * Localiza a IIFE de rotação `(function(i,e){...while(!![])...})(arrFn, ...);`
 * que executa o deslocamento cíclico síncrono no início do arquivo.
 * @param {string} src - Código-fonte completo.
 * @returns {{ start: number, end: number, code: string } | null} Fronteiras da IIFE.
 */
function extractRotationIIFE(src) {
  const whileIdx = src.indexOf("while(!![])");
  if (whileIdx === -1) return null;

  // Volta até o início da expressão `(function(` mais próxima antes do `while(!![])`
  const iifeStart = src.lastIndexOf("(function(", whileIdx);
  if (iifeStart === -1) return null;

  const parenEnd = findMatchingDelimiter(src, iifeStart, "(", ")");
  if (parenEnd === -1) return null;

  let iifeEnd = parenEnd + 1;
  if (src[iifeEnd] === ";") {
    iifeEnd++;
  }

  return {
    start: iifeStart,
    end: iifeEnd,
    code: src.slice(iifeStart, iifeEnd),
  };
}

/**
 * Verifica se `rotated` é exatamente um deslocamento cíclico esquerdo de `original`
 * por `shifts` posições (modulo comprimento do array).
 * @param {string[]} original - Array antes das rotações.
 * @param {string[]} rotated - Array após as rotações.
 * @param {number} shifts - Quantidade de `shift()` + `push()` executadas.
 * @returns {boolean} Verdadeiro se for deslocamento exato.
 */
function isExactCyclicShift(original, rotated, shifts) {
  if (original.length !== rotated.length || original.length === 0) return false;
  const n = original.length;
  const k = ((shifts % n) + n) % n;
  for (let i = 0; i < n; i++) {
    if (original[(i + k) % n] !== rotated[i]) {
      return false;
    }
  }
  return true;
}

/**
 * Processa, otimiza e valida um arquivo ofuscado de submódulo do Vein Miner.
 * Não altera o comportamento em runtime: apenas pré-computa o array rotacionado e
 * remove o loop `while(!![])` da inicialização do módulo.
 *
 * @param {string} filePath - Caminho do arquivo `.js`.
 * @param {string} relPath - Caminho relativo ao diretório `scripts/`.
 * @returns {{ optimizedCode: string, arrLen: number, shifts: number, origMs: number, optMs: number }} Resultado verificado.
 */
function processObfuscatedModule(filePath, relPath) {
  const src = fs.readFileSync(filePath, "utf8");

  // Captura as 3 funções globais do ofuscador (`_i`, `_e`, `_S` por arquivo)
  const fnNames = [...src.matchAll(/function (DARKLEXx_VMTC_3_2_0_\w+)\(/g)].map((m) => m[1]);
  if (fnNames.length !== 3) {
    throw new Error(`[${relPath}] Esperadas 3 funções DARKLEXx_VMTC_3_2_0_*, encontradas ${fnNames.length}.`);
  }

  const fnInfos = fnNames.map((name) => {
    const info = extractNamedFunction(src, name);
    if (!info) throw new Error(`[${relPath}] Falha ao extrair função ${name}.`);
    return { name, ...info };
  });

  // Identifica a função geradora do array (sem parâmetros) e as duas decodificadoras
  const arrFnInfo = fnInfos.find((f) => f.code.startsWith(`function ${f.name}(){`));
  if (!arrFnInfo) {
    throw new Error(`[${relPath}] Não foi possível identificar a função geradora do array (0 argumentos).`);
  }
  const decoderFns = fnInfos.filter((f) => f.name !== arrFnInfo.name);

  // Localiza a IIFE de rotação
  const iife = extractRotationIIFE(src);
  if (!iife) {
    throw new Error(`[${relPath}] IIFE de rotação 'while(!![])' não encontrada.`);
  }

  // Sandbox A: Executa as funções + IIFE originais registrando:
  //  - array antes e depois das rotações;
  //  - contagem exata de shifts;
  //  - todas as chamadas decodificadoras da iteração vencedora final.
  const sandboxACode = `
    ${fnInfos.map((f) => f.code).join("\n")}
    const __arrRef = ${arrFnInfo.name}();
    const __origArr = __arrRef.slice();
    let __shifts = 0;
    let __finalCalls = [];

    const __origShift = __arrRef.shift.bind(__arrRef);
    __arrRef.shift = function() {
      __shifts++;
      // Reinicia o log sempre que gira o array: ao fim do loop só ficam as chamadas válidas
      __finalCalls = [];
      return __origShift();
    };

    ${decoderFns
      .map(
        (d) => `
      // Mantém referência mutável para o wrapper porque a função ofuscada
      // se auto-redefine na primeira execução para criar o cache de closure.
      let __cur_${d.name} = ${d.name};
      function ${d.name}_wrapped(a, b) {
        let res;
        try {
          res = __cur_${d.name}(a, b);
        } finally {
          // Mesmo se a tentativa de rotação lançar URIError, captura a closure interna
          if (${d.name} !== ${d.name}_wrapped) {
            __cur_${d.name} = ${d.name};
            ${d.name} = ${d.name}_wrapped;
          }
        }
        __finalCalls.push({ fn: "${d.name}", a, b, res });
        return res;
      }
      ${d.name} = ${d.name}_wrapped;
    `
      )
      .join("\n")}

    const __t0 = __now();
    ${iife.code}
    const __elapsedMs = __now() - __t0;

    ({
      origArr: __origArr,
      rotatedArr: __arrRef.slice(),
      shifts: __shifts,
      finalCalls: __finalCalls,
      origMs: __elapsedMs
    });
  `;

  const t0 = performance.now();
  const resA = vm.runInNewContext(sandboxACode, { __now: () => performance.now() });
  void t0;

  // Verificação 1: Valida deslocamento cíclico exato do array
  if (!isExactCyclicShift(resA.origArr, resA.rotatedArr, resA.shifts)) {
    throw new Error(`[${relPath}] Verificação de rotação cíclica falhou! shifts=${resA.shifts}`);
  }
  if (resA.finalCalls.length === 0) {
    throw new Error(`[${relPath}] Nenhuma chamada decodificadora capturada na iteração final.`);
  }

  // Monta a versão otimizada da função do array substituindo apenas `[...]` pelo literal rotacionado
  const brStart = arrFnInfo.code.indexOf("[");
  const brEnd = findMatchingDelimiter(arrFnInfo.code, brStart, "[", "]");
  if (brStart === -1 || brEnd === -1) {
    throw new Error(`[${relPath}] Literal de array não encontrado dentro de ${arrFnInfo.name}.`);
  }
  const optimizedArrFnCode =
    arrFnInfo.code.slice(0, brStart) + JSON.stringify(resA.rotatedArr) + arrFnInfo.code.slice(brEnd + 1);

  // Sandbox B: Testa a versão otimizada SEM IIFE para provar equivalência bit-a-bit de todas as strings
  const sandboxBCode = `
    ${optimizedArrFnCode}
    ${decoderFns.map((d) => d.code).join("\n")}
    const __t0 = __now();
    const __arrB = ${arrFnInfo.name}();
    const __mismatch = [];
    for (const call of __expectedCalls) {
      const fn = call.fn === "${decoderFns[0].name}" ? ${decoderFns[0].name} : ${decoderFns[1].name};
      const got = fn(call.a, call.b);
      if (got !== call.res) {
        __mismatch.push({ call, got });
      }
    }
    const __elapsedMs = __now() - __t0;
    ({
      arrB: __arrB,
      mismatch: __mismatch,
      optMs: __elapsedMs
    });
  `;

  const resB = vm.runInNewContext(sandboxBCode, {
    __now: () => performance.now(),
    __expectedCalls: resA.finalCalls,
  });

  // Verificação 2: Arrays idênticos e 0 divergências em todas as chamadas decodificadoras
  if (JSON.stringify(resB.arrB) !== JSON.stringify(resA.rotatedArr)) {
    throw new Error(`[${relPath}] Array otimizado difere do array rotacionado da Sandbox A.`);
  }
  if (resB.mismatch.length > 0) {
    throw new Error(
      `[${relPath}] Divergência na decodificação pós-otimização: ${JSON.stringify(resB.mismatch[0])}`
    );
  }

  // Aplica as duas edições pontuais no código original calculando pelos índices originais
  const edits = [
    { start: arrFnInfo.start, end: arrFnInfo.end, replacement: optimizedArrFnCode },
    {
      start: iife.start,
      end: iife.end,
      replacement: "/* [Otimizado] IIFE de rotação pré-computada (anti-Watchdog Spike) */\n",
    },
  ].sort((a, b) => b.start - a.start); // Aplica de trás para frente para preservar offsets!

  let optimizedCode = src;
  for (const edit of edits) {
    optimizedCode = optimizedCode.slice(0, edit.start) + edit.replacement + optimizedCode.slice(edit.end);
  }

  // Ajuste adicional específico para `utils/Notifications.js`: reduz polling de 1 tick -> 4 ticks (200ms)
  if (relPath.replace(/\\/g, "/") === "utils/Notifications.js") {
    const firstIdx = optimizedCode.indexOf(NOTIF_INTERVAL_EXPR);
    const lastIdx = optimizedCode.lastIndexOf(NOTIF_INTERVAL_EXPR);
    if (firstIdx === -1 || firstIdx !== lastIdx) {
      throw new Error(
        `[${relPath}] Expressão de intervalo '${NOTIF_INTERVAL_EXPR}' não encontrada de forma única.`
      );
    }
    const contextAfter = optimizedCode.slice(firstIdx + NOTIF_INTERVAL_EXPR.length, firstIdx + NOTIF_INTERVAL_EXPR.length + 10);
    if (!contextAfter.startsWith(");}")) {
      throw new Error(`[${relPath}] Contexto inesperado após expressão de intervalo: '${contextAfter}'`);
    }
    optimizedCode =
      optimizedCode.slice(0, firstIdx) +
      NOTIF_INTERVAL_NEW +
      optimizedCode.slice(firstIdx + NOTIF_INTERVAL_EXPR.length);
  }

  return {
    optimizedCode,
    arrLen: resA.origArr.length,
    shifts: resA.shifts,
    origMs: resA.origMs,
    optMs: resB.optMs,
    verifiedCalls: resA.finalCalls.length,
  };
}

/**
 * Ponto de entrada principal do script de otimização do Vein Miner.
 */
function main() {
  if (!fs.existsSync(VEIN_MINER_SCRIPTS_DIR)) {
    throw new Error(`Diretório do Vein Miner não encontrado: ${VEIN_MINER_SCRIPTS_DIR}`);
  }

  // Passo 1: Backup dos arquivos originais antes de qualquer leitura/alteração
  createInitialBackup(VEIN_MINER_SCRIPTS_DIR, BACKUP_DIR);

  // Passo 2: Processa a partir do BACKUP_DIR (idempotente) todos os 17 submódulos ofuscados
  const allFiles = walkJsFiles(BACKUP_DIR);
  const subModules = allFiles.filter((f) => path.relative(BACKUP_DIR, f) !== "main.js");

  console.log(`\nEncontrados ${allFiles.length} arquivos JS no backup (${subModules.length} submódulos ofuscados).\n`);

  const stagedWrites = [];
  let sumOrigMs = 0;
  let sumOptMs = 0;
  let sumShifts = 0;

  for (const backupFile of subModules) {
    const rel = path.relative(BACKUP_DIR, backupFile);
    const targetFile = path.join(VEIN_MINER_SCRIPTS_DIR, rel);

    const res = processObfuscatedModule(backupFile, rel);
    stagedWrites.push({ rel, targetFile, content: res.optimizedCode });

    sumOrigMs += res.origMs;
    sumOptMs += res.optMs;
    sumShifts += res.shifts;

    console.log(
      `  [OK] ${rel.padEnd(40)} arr=${String(res.arrLen).padStart(3)} shifts=${String(res.shifts).padStart(3)} ` +
        `callsOK=${String(res.verifiedCalls).padStart(2)} ${res.origMs.toFixed(2)}ms -> ${res.optMs.toFixed(2)}ms`
    );
  }

  // Só grava em disco após TODOS os 17 arquivos passarem pela verificação em sandbox
  console.log("\nTodas as verificações de equivalência passaram. Gravando arquivos otimizados...");
  for (const item of stagedWrites) {
    fs.writeFileSync(item.targetFile, item.content, "utf8");
  }

  // Copia também `main.js` do backup caso precise restaurar consistência (mantendo intacto)
  const targetMain = path.join(VEIN_MINER_SCRIPTS_DIR, "main.js");
  const backupMain = path.join(BACKUP_DIR, "main.js");
  if (!fs.existsSync(targetMain) && fs.existsSync(backupMain)) {
    fs.copyFileSync(backupMain, targetMain);
  }

  // Passo 3: Valida sintaxe JS (ES Module) de TODOS os 18 arquivos gravados usando `node --check`
  console.log("\nValidando sintaxe de todos os 18 arquivos com 'node --check'...");
  const finalFiles = walkJsFiles(VEIN_MINER_SCRIPTS_DIR);
  for (const f of finalFiles) {
    execFileSync(process.execPath, ["--check", f], { stdio: "pipe" });
  }

  console.log(
    `\n=== RESUMO VEIN MINER ===\n` +
      `  Arquivos verificados (node --check): ${finalFiles.length}/18 OK\n` +
      `  Rotações síncronas eliminadas:       ${sumShifts} shifts\n` +
      `  Tempo total em V8 (load IIFE):       ${sumOrigMs.toFixed(2)}ms -> ${sumOptMs.toFixed(2)}ms\n` +
      `  Intervalo utils/Notifications.js:    1 tick -> 4 ticks (200ms)\n`
  );
}

main();
