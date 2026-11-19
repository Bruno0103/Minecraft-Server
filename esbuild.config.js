/**
 * ==============================================================================
 * esbuild.config.js — Bundler de Alta Performance para Scripts Minecraft Bedrock
 * ==============================================================================
 * Transpila o código-fonte TypeScript em JavaScript ESM puro em menos de 20ms,
 * preservando os módulos nativos da engine do Minecraft Bedrock 1.26.65+ (26.65+)
 * como dependências externas.
 *
 * Regra crítica de validação (`[FORBFILE]` no Minecraft Creator Tools):
 *   A pasta `addon/behavior_pack/scripts/` NÃO pode conter arquivos `.js.map`
 *   separados, pois a Mojang rejeita a extensão `.map` na validação de pacotes.
 *   Por isso, em desenvolvimento utilizamos `sourcemap: 'inline'` (embutido no
 *   próprio `main.js`, mantendo rastreabilidade de stack traces sem arquivos extras)
 *   e removemos preventivamente qualquer `.js.map` residual no diretório de saída.
 *
 * Uso:
 *   node esbuild.config.js               -> Compilação padrão com sourcemap inline
 *   node esbuild.config.js --watch       -> Observação contínua de alterações em src/
 *   node esbuild.config.js --production  -> Compilação minificada sem sourcemap
 */

const esbuild = require('esbuild');
const path = require('path');
const fs = require('fs');

const isWatch = process.argv.includes('--watch');
const isProduction = process.argv.includes('--production');

// Diretório de saída para os scripts compilados do Behavior Pack
const scriptsOutDir = path.join(__dirname, 'addon', 'behavior_pack', 'scripts');
const outputFile = path.join(scriptsOutDir, 'main.js');
const legacySourceMapFile = path.join(scriptsOutDir, 'main.js.map');

// Garante a existência do diretório de saída antes de iniciar o esbuild
if (!fs.existsSync(scriptsOutDir)) {
  fs.mkdirSync(scriptsOutDir, { recursive: true });
}

/**
 * Remove arquivos `.js.map` separados dentro de `behavior_pack/scripts/` para
 * evitar erro fatal `[FORBFILE] File Does Not Have Allowed Extension` no `mct`.
 */
function cleanForbiddenSourceMaps() {
  if (fs.existsSync(legacySourceMapFile)) {
    fs.unlinkSync(legacySourceMapFile);
    console.log('🧹 [esbuild] Arquivo proibido removido: addon/behavior_pack/scripts/main.js.map');
  }
}

/** @type {esbuild.BuildOptions} */
const buildOptions = {
  // Ponto de entrada principal da Script API em TypeScript
  entryPoints: [path.join(__dirname, 'addon', 'src', 'main.ts')],
  bundle: true,
  outfile: outputFile,
  // Módulos internos providos nativamente pela engine do Minecraft Bedrock 1.26.65+:
  // Devem ser mantidos como externos para NÃO serem embutidos no bundle final!
  external: [
    '@minecraft/server',
    '@minecraft/server-ui',
    '@minecraft/server-admin',
    '@minecraft/server-net',
    '@minecraft/server-editor',
    '@minecraft/vanilla-data'
  ],
  format: 'esm',
  platform: 'neutral',
  target: 'es2022',
  // Em produção remove sourcemaps; em dev usa 'inline' para não gerar arquivo .map solto
  sourcemap: isProduction ? false : 'inline',
  minify: isProduction,
  logLevel: 'info'
};

/**
 * Executa a compilação única ou inicia o observador contínuo do esbuild.
 */
async function run() {
  try {
    // Limpa qualquer arquivo .js.map legado antes de gerar o novo bundle
    cleanForbiddenSourceMaps();

    if (isWatch) {
      const ctx = await esbuild.context(buildOptions);
      await ctx.watch();
      console.log('👀 [esbuild] Modo watch ativo em addon/src/ para recompilação contínua...');
    } else {
      await esbuild.build(buildOptions);
      console.log('✅ [esbuild] Compilação do Script API concluída com sucesso em addon/behavior_pack/scripts/main.js!');
    }
  } catch (error) {
    console.error('❌ [esbuild] Falha fatal na compilação:', error);
    process.exit(1);
  }
}

run();
