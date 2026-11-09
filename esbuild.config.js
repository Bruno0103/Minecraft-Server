/**
 * ==============================================================================
 * esbuild.config.js — Bundler de Alta Performance para Scripts Minecraft Bedrock
 * ==============================================================================
 * Transpila o código-fonte TypeScript em JavaScript ESM puro em menos de 20ms,
 * preservando os módulos nativos da engine do Minecraft Bedrock como dependências externas.
 *
 * Uso:
 *   node esbuild.config.js          -> Compilação única de produção/desenvolvimento
 *   node esbuild.config.js --watch  -> Modo de observação contínua de alterações em src/
 */

const esbuild = require('esbuild');
const path = require('path');
const fs = require('fs');

const isWatch = process.argv.includes('--watch');

// Garante a existência do diretório de saída para o pacote de comportamento
const outputDirs = [
  path.join(__dirname, 'addon', 'behavior_pack', 'scripts')
];

outputDirs.forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
});

/** @type {esbuild.BuildOptions} */
const buildOptions = {
  // Ponto de entrada do código-fonte em TypeScript
  entryPoints: [path.join(__dirname, 'addon', 'src', 'main.ts')],
  bundle: true,
  outfile: path.join(__dirname, 'addon', 'behavior_pack', 'scripts', 'main.js'),
  // Módulos internos providos nativamente pela engine do Minecraft Bedrock:
  // Devem ser mantidos como externos para NÃO serem embutidos no pacote compilado!
  external: [
    '@minecraft/server',
    '@minecraft/server-ui',
    '@minecraft/server-admin',
    '@minecraft/server-net',
    '@minecraft/server-editor'
  ],
  format: 'esm',
  platform: 'neutral',
  target: 'es2022',
  sourcemap: true,
  minify: false,
  logLevel: 'info'
};

async function run() {
  try {
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

