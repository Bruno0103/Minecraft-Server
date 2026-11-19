/**
 * ==============================================================================
 * tools/utilitarios/validate_addon.js — Auditor e Validador Bedrock 26.65+
 * ==============================================================================
 * Ferramenta unificada de validação estática para Add-ons do Minecraft Bedrock
 * Edition 1.26.65+ (26.65+, compatível com 1.21.60+), utilizando o motor Ajv
 * (JSON Schema Draft-07) acoplado aos schemas oficiais da Blockception empacotados
 * com o @minecraft/creator-tools e às regras estritas do BDS / Mojang.
 *
 * Modos de execução via linha de comando:
 *   1. Auditoria automática do projeto (padrão no `npm run validate`):
 *        node tools/utilitarios/validate_addon.js
 *        node tools/utilitarios/validate_addon.js addon
 *
 *   2. Validação pontual arquivo-contra-schema (retrocompatível):
 *        node tools/utilitarios/validate_addon.js <caminho_schema.json> <caminho_alvo.json>
 *
 * Variável de ambiente opcional:
 *   BLOCKCEPTION_SCHEMAS_DIR -> Sobrescreve o diretório raiz dos schemas Blockception.
 *
 * Código de saída (Exit Code):
 *   0: Todos os arquivos e regras arquiteturais do Bedrock 1.26.65+ aprovados.
 *   1: Erros de schema, sintaxe JSON ou violações de regras críticas encontrados.
 */

const fs = require('fs');
const path = require('path');
const Ajv = require('ajv');
const addFormats = require('ajv-formats');

// ==============================================================================
// 1. INICIALIZAÇÃO DO MOTOR AJV PARA SCHEMAS BLOCKCEPTION
// ==============================================================================

/**
 * Cria e configura uma instância do Ajv compatível com os schemas da Blockception.
 * - `unicodeRegExp: false` é obrigatório porque vários padrões regex da Blockception
 *   usam escapes como `\_` e `\-` dentro de colchetes, que o modo `/u` do JS rejeita.
 * - Registra formatos personalizados do ecossistema Bedrock (`molang`, `color-hex`,
 *   e `colox-hex` — typo presente nos schemas upstream da Blockception).
 *
 * @returns {Ajv} Instância configurada do validador Ajv.
 */
function createBedrockAjv() {
  const ajv = new Ajv({
    allErrors: true,
    strict: false,
    verbose: true,
    unicodeRegExp: false
  });

  // Habilita formatos padrão como 'uuid', 'uri' e 'date-time'
  addFormats(ajv);

  // Registra formatos customizados usados pelos schemas da Blockception
  ajv.addFormat('molang', true);
  ajv.addFormat('color-hex', /^#?[0-9a-fA-F]{3,8}$/);
  ajv.addFormat('colox-hex', /^#?[0-9a-fA-F]{3,8}$/);

  return ajv;
}

const ajv = createBedrockAjv();

/**
 * Cache em memória de funções compiladas por caminho de arquivo de schema.
 * Evita recompilações repetidas e previne erro de `$id` duplicado no Ajv.
 * @type {Map<string, import('ajv').ValidateFunction>}
 */
const compiledSchemaCache = new Map();

/**
 * Localiza o diretório local dos schemas da Blockception instalados globalmente
 * pelo pacote `@minecraft/creator-tools` (ou configurado via env).
 *
 * @returns {string|null} Caminho absoluto do diretório de schemas ou null se ausente.
 */
function resolveBlockceptionSchemasDir() {
  const appDataRoaming = process.env.APPDATA || '';
  const candidates = [
    process.env.BLOCKCEPTION_SCHEMAS_DIR,
    'C:/Users/Bruno/AppData/Roaming/npm/node_modules/@minecraft/creator-tools/res/latest/schemas',
    appDataRoaming
      ? path.join(appDataRoaming, 'npm', 'node_modules', '@minecraft', 'creator-tools', 'res', 'latest', 'schemas')
      : null
  ].filter(Boolean);

  for (const dirPath of candidates) {
    if (fs.existsSync(dirPath) && fs.statSync(dirPath).isDirectory()) {
      return dirPath;
    }
  }
  return null;
}

/**
 * Carrega e compila (com cache) um schema JSON da Blockception ou customizado.
 *
 * @param {string} schemaPath - Caminho completo do arquivo de schema JSON.
 * @returns {import('ajv').ValidateFunction} Função de validação compilada.
 */
function getValidatorForSchema(schemaPath) {
  const normalizedPath = path.resolve(schemaPath);
  if (compiledSchemaCache.has(normalizedPath)) {
    return compiledSchemaCache.get(normalizedPath);
  }

  const schemaRaw = fs.readFileSync(normalizedPath, 'utf-8');
  const schema = JSON.parse(schemaRaw);

  // Se outro arquivo com o mesmo $id já tiver sido registrado no Ajv, reutiliza-o
  let validateFn = schema.$id ? ajv.getSchema(schema.$id) : undefined;
  if (!validateFn) {
    validateFn = ajv.compile(schema);
  }

  compiledSchemaCache.set(normalizedPath, validateFn);
  return validateFn;
}

/**
 * Pré-registra schemas auxiliares do repositório Blockception no Ajv caso algum
 * arquivo faça referência por URI/`$id` cruzado (mantendo tolerância a duplicatas).
 *
 * @param {string} schemasRoot - Diretório raiz dos schemas Blockception.
 */
function preloadCrossRefSchemas(schemasRoot) {
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (entry.name.endsWith('.json') && entry.name !== 'index.json') {
        try {
          const content = JSON.parse(fs.readFileSync(fullPath, 'utf-8'));
          if (content.$id && !ajv.getSchema(content.$id)) {
            ajv.addSchema(content);
          }
        } catch {
          // Ignora arquivos auxiliares que não sejam schemas válidos isolados
        }
      }
    }
  };
  walk(schemasRoot);
}

// ==============================================================================
// 2. VALIDAÇÃO PONTUAL DE ARQUIVO ÚNICO (RETROCOMPATÍVEL)
// ==============================================================================

/**
 * Valida um arquivo JSON de destino contra um JSON Schema específico.
 *
 * @param {string} schemaPath - Caminho para o arquivo de esquema JSON.
 * @param {string} targetPath - Caminho para o arquivo de dados/manifesto a ser validado.
 * @param {boolean} [quietSuccess=false] - Se true, omite log detalhado quando válido.
 * @returns {boolean} Retorna true se válido, false se houver discrepâncias estruturais.
 */
function validateJson(schemaPath, targetPath, quietSuccess = false) {
  try {
    if (!fs.existsSync(schemaPath)) {
      console.error(`❌ [ERRO] Arquivo de schema não encontrado: ${schemaPath}`);
      return false;
    }
    if (!fs.existsSync(targetPath)) {
      console.error(`❌ [ERRO] Arquivo alvo de validação não encontrado: ${targetPath}`);
      return false;
    }

    const targetRaw = fs.readFileSync(targetPath, 'utf-8');
    // Remove comentários // ocasionais em arquivos JSONC de configuração de desenvolvimento
    const sanitizedTarget = targetRaw.replace(/^\s*\/\/.*$/gm, '');
    const target = JSON.parse(sanitizedTarget);

    const validate = getValidatorForSchema(schemaPath);
    const valid = validate(target);

    if (valid) {
      if (!quietSuccess) {
        console.log(
          `✅ [VALIDAÇÃO SUCESSO] ${path.basename(targetPath)} está em conformidade estrita com ${path.basename(schemaPath)}!`
        );
      }
      return true;
    }

    console.error(`❌ [FALHA DE ESQUEMA] Erros encontrados em ${targetPath}:`);
    (validate.errors || []).forEach((err, index) => {
      const campo = err.instancePath || '(raiz)';
      console.error(`  #${index + 1} Campo: ${campo} | Erro: ${err.message}`);
    });
    return false;
  } catch (error) {
    console.error(`❌ [ERRO INESPERADO] Falha ao processar ${targetPath}:`, error.message);
    return false;
  }
}

// ==============================================================================
// 3. REGRAS DE DOMÍNIO BEDROCK 1.26.65+ (26.65+) E MAPEAMENTO DE SCHEMAS
// ==============================================================================

/**
 * Tabela de mapeamento entre caminhos relativos dentro de Behavior/Resource Packs
 * e os respectivos arquivos de schema da Blockception.
 */
const PACK_SCHEMA_RULES = [
  // Manifestos (BP e RP)
  { match: /(^|\/)manifest\.json$/i, schema: 'general/manifest.json', label: 'Manifesto' },

  // Behavior Pack (BP)
  { match: /behavior_packs?\/blocks\/.*\.json$/i, schema: 'behavior/blocks/blocks.json', label: 'Bloco BP' },
  { match: /behavior_packs?\/items\/.*\.json$/i, schema: 'behavior/items/items.json', label: 'Item BP' },
  { match: /behavior_packs?\/entities\/.*\.json$/i, schema: 'behavior/entities/entities.json', label: 'Entidade BP' },
  { match: /behavior_packs?\/recipes\/.*\.json$/i, schema: 'behavior/recipes/recipes.json', label: 'Receita BP' },
  { match: /behavior_packs?\/loot_tables\/.*\.json$/i, schema: 'behavior/loot_tables/loot_tables.json', label: 'Loot Table BP' },
  { match: /behavior_packs?\/spawn_rules\/.*\.json$/i, schema: 'behavior/spawn_rules/spawn_rules.json', label: 'Spawn Rule BP' },
  { match: /behavior_packs?\/trading\/.*\.json$/i, schema: 'behavior/trading/trading.json', label: 'Trading BP' },
  { match: /behavior_packs?\/animations\/.*\.json$/i, schema: 'behavior/animations/animations.json', label: 'Animação BP' },
  { match: /behavior_packs?\/animation_controllers\/.*\.json$/i, schema: 'behavior/animation_controllers/animation_controller.json', label: 'Anim Controller BP' },
  { match: /behavior_packs?\/worldgen\/jigsaw_structures\/.*\.json$/i, schema: 'worldgen/jigsaw_structures/jigsaw.json', label: 'Worldgen Jigsaw' },
  { match: /behavior_packs?\/worldgen\/structure_sets\/.*\.json$/i, schema: 'worldgen/structure_sets/structure_set.json', label: 'Worldgen Structure Set' },
  { match: /behavior_packs?\/worldgen\/template_pools\/.*\.json$/i, schema: 'worldgen/template_pools/template_pool.json', label: 'Worldgen Template Pool' },
  { match: /behavior_packs?\/worldgen\/processors\/.*\.json$/i, schema: 'worldgen/processors/processor_list.json', label: 'Worldgen Processor' },

  // Resource Pack (RP)
  { match: /resource_packs?\/blocks\.json$/i, schema: 'resource/blocks.json', label: 'Registro RP blocks.json' },
  { match: /resource_packs?\/entity\/.*\.json$/i, schema: 'resource/entity/entity.json', label: 'Client Entity RP' },
  { match: /resource_packs?\/animations\/.*\.json$/i, schema: 'resource/animations/actor_animation.json', label: 'Animação RP' },
  { match: /resource_packs?\/animation_controllers\/.*\.json$/i, schema: 'resource/animation_controllers/animation_controller.json', label: 'Anim Controller RP' },
  { match: /resource_packs?\/particles\/.*\.json$/i, schema: 'resource/particles/particles.json', label: 'Partícula Snowstorm' },
  { match: /resource_packs?\/render_controllers\/.*\.json$/i, schema: 'resource/render_controllers/render_controllers.json', label: 'Render Controller' },
  { match: /resource_packs?\/attachables\/.*\.json$/i, schema: 'resource/attachables/attachables.json', label: 'Attachable RP' },
  { match: /resource_packs?\/models\/entity\/.*\.geo\.json$/i, schema: 'resource/models/entity/model_entity.json', label: 'Geometria 3D' },
  { match: /resource_packs?\/textures\/item_texture\.json$/i, schema: 'resource/textures/item_texture.json', label: 'Atlas item_texture.json' },
  { match: /resource_packs?\/textures\/terrain_texture\.json$/i, schema: 'resource/textures/terrain_texture.json', label: 'Atlas terrain_texture.json' },
  { match: /resource_packs?\/textures\/flipbook_textures\.json$/i, schema: 'resource/textures/flipbook_textures.json', label: 'Flipbook Textures' },
  { match: /resource_packs?\/sounds\/sound_definitions\.json$/i, schema: 'resource/sounds/sound_definitions.json', label: 'Sound Definitions' },

  // Vibrant Visuals / Render Dragon (1.21.60+ / 1.26.65+)
  { match: /resource_packs?\/pbr\/.*\.json$/i, schema: 'resource/pbr/pbr.json', label: 'Vibrant Visuals PBR' },
  { match: /resource_packs?\/point_lights\/.*\.json$/i, schema: 'resource/point_lights/point_lights.json', label: 'Point Lights' },
  { match: /resource_packs?\/lighting\/.*\.json$/i, schema: 'resource/lighting/lighting.json', label: 'Lighting' },
  { match: /resource_packs?\/shadows\/.*\.json$/i, schema: 'resource/shadows/shadows.json', label: 'Shadows' },
  { match: /resource_packs?\/water\/.*\.json$/i, schema: 'resource/water/water.json', label: 'Water' },
  { match: /resource_packs?\/fogs\/.*\.json$/i, schema: 'resource/fog/fog.json', label: 'Fog' },

  // Texture Sets PBR em qualquer subpasta (inclusive assets de teste)
  { match: /\.texture_set\.json$/i, schema: 'resource/textures/texture_set.json', label: 'Texture Set PBR' }
];

/**
 * Compara duas versões semânticas em tupla `[major, minor, patch]`.
 *
 * @param {number[]} a - Versão atual.
 * @param {number[]} b - Versão mínima exigida.
 * @returns {number} Negativo se a < b, 0 se igual, positivo se a > b.
 */
function compareVersionArrays(a, b) {
  for (let i = 0; i < 3; i++) {
    const va = Number(a?.[i] ?? 0);
    const vb = Number(b?.[i] ?? 0);
    if (va !== vb) return va - vb;
  }
  return 0;
}

/**
 * Coleta recursivamente todos os arquivos dentro de um diretório ignorando `node_modules` e `.git`.
 *
 * @param {string} rootDir - Diretório inicial de varredura.
 * @returns {string[]} Lista de caminhos absolutos encontrados.
 */
function collectFiles(rootDir) {
  const results = [];
  const walk = (currentDir) => {
    for (const entry of fs.readdirSync(currentDir, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name === '.git') continue;
      const fullPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else {
        results.push(fullPath);
      }
    }
  };
  walk(rootDir);
  return results;
}

/**
 * Executa regras de integridade específicas do Minecraft Bedrock 1.26.65+ (26.65+)
 * que vão além da validação puramente sintática do JSON Schema.
 *
 * @param {string} projectDir - Diretório raiz do add-on (contendo BP/RP).
 * @param {string[]} allFiles - Lista completa de arquivos no projeto.
 * @returns {{ errors: string[], warnings: string[] }} Achados de auditoria.
 */
function runBedrock2665DomainChecks(projectDir, allFiles) {
  const errors = [];
  const warnings = [];
  const manifestRecords = [];

  // 1. Auditoria dos manifestos (BP e RP) e detecção de `pack_icon.png`
  for (const filePath of allFiles) {
    if (path.basename(filePath).toLowerCase() !== 'manifest.json') continue;

    const packDir = path.dirname(filePath);
    const relPack = path.relative(projectDir, packDir).replace(/\\/g, '/') || '.';
    const iconPath = path.join(packDir, 'pack_icon.png');
    if (!fs.existsSync(iconPath)) {
      errors.push(`[CPACKICON] pack_icon.png ausente na raiz do pacote "${relPack}".`);
    }

    let json;
    try {
      json = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
    } catch {
      // Erros de sintaxe JSON já são capturados na etapa de schema
      continue;
    }

    const moduleTypes = new Set((json.modules || []).map((m) => m.type));
    const isBehaviorPack = moduleTypes.has('data') || moduleTypes.has('script');
    const isResourcePack = moduleTypes.has('resources');
    manifestRecords.push({ filePath, relPack, json, isBehaviorPack, isResourcePack });

    // Valida min_engine_version >= [1, 26, 60] (versão base de schema para 1.26.65+)
    const minEngine = json.header?.min_engine_version;
    if (!Array.isArray(minEngine) || compareVersionArrays(minEngine, [1, 26, 60]) < 0) {
      errors.push(
        `[MINENGINEVER] "${relPack}/manifest.json" usa min_engine_version [${(minEngine || []).join(', ')}]; o alvo Bedrock 1.26.65+ exige no mínimo [1, 26, 60].`
      );
    }

    // Valida metadata.product_type === "addon" obrigatório no Behavior Pack (preserva conquistas)
    if (isBehaviorPack && json.metadata?.product_type !== 'addon') {
      errors.push(
        `[CONQUISTAS] "${relPack}/manifest.json" deve declarar metadata.product_type = "addon" para manter conquistas ativas no Bedrock 1.26.65+.`
      );
    }
  }

  // Valida vínculos UUID entre BP e RP e proíbe dependência reversa RP -> BP
  const bpUuids = new Set(
    manifestRecords
      .filter((m) => m.isBehaviorPack)
      .map((m) => String(m.json.header?.uuid || '').toLowerCase())
      .filter(Boolean)
  );
  const rpUuids = new Set(
    manifestRecords
      .filter((m) => m.isResourcePack)
      .map((m) => String(m.json.header?.uuid || '').toLowerCase())
      .filter(Boolean)
  );

  for (const record of manifestRecords) {
    const deps = record.json.dependencies || [];
    for (const dep of deps) {
      if (!dep.uuid) continue;
      const depUuid = String(dep.uuid).toLowerCase();

      // Proibição explícita de RP depender de BP (evita reordenação do Pack Stack no BDS)
      if (record.isResourcePack && bpUuids.has(depUuid)) {
        errors.push(
          `[PACKSTACK] Dependência reversa RP -> BP proibida detectada em "${record.relPack}/manifest.json" (uuid ${dep.uuid}).`
        );
      }

      // Garante que a dependência aponta para um pacote existente no projeto
      if (!bpUuids.has(depUuid) && !rpUuids.has(depUuid)) {
        warnings.push(
          `[PACKDEP] "${record.relPack}/manifest.json" referencia uuid ${dep.uuid} que não corresponde a nenhum pacote local.`
        );
      }
    }
  }

  // 2. Auditoria de arquivos proibidos em scripts/ (ex.: `.js.map` causa `[FORBFILE]`)
  for (const filePath of allFiles) {
    const rel = path.relative(projectDir, filePath).replace(/\\/g, '/');
    if (/behavior_packs?\/scripts\/.*\.map$/i.test(rel)) {
      errors.push(
        `[FORBFILE] Arquivo source map separado proibido em scripts/: "${rel}". Use sourcemap inline ou false no esbuild.`
      );
    }
  }

  // 3. Regras de conteúdo (itens, animações e texture_set)
  for (const filePath of allFiles) {
    if (!filePath.endsWith('.json')) continue;
    const rel = path.relative(projectDir, filePath).replace(/\\/g, '/');
    let data;
    try {
      data = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
    } catch {
      continue;
    }

    // 3a. Itens: prefixo de namespace obrigatório em menu_category.group (1.21.60+ / 1.26.65+)
    const itemGroup = data?.['minecraft:item']?.description?.menu_category?.group;
    if (typeof itemGroup === 'string' && !itemGroup.includes(':')) {
      errors.push(
        `[ITEMGROUP] "${rel}": description.menu_category.group ("${itemGroup}") exige namespace (ex.: "minecraft:${itemGroup}").`
      );
    }

    // 3b. Animações: proibir objetos em timeline e Molang dinâmico em keyframes catmullrom
    if (/animations\/.*\.animation\.json$/i.test(rel) && data?.animations) {
      for (const [animName, anim] of Object.entries(data.animations)) {
        if (anim.timeline && typeof anim.timeline === 'object') {
          for (const [ts, entry] of Object.entries(anim.timeline)) {
            if (typeof entry !== 'string' && !Array.isArray(entry)) {
              errors.push(
                `[ANIM_TIMELINE] "${rel}" (${animName} @ ${ts}): timeline aceita apenas string ou array de strings; use sound_effects/particle_effects fora de timeline.`
              );
            }
          }
        }
        if (anim.bones && typeof anim.bones === 'object') {
          for (const [boneName, bone] of Object.entries(anim.bones)) {
            for (const channel of ['rotation', 'position', 'scale']) {
              const keyframes = bone?.[channel];
              if (!keyframes || typeof keyframes !== 'object' || Array.isArray(keyframes)) continue;
              for (const [kfTime, kfVal] of Object.entries(keyframes)) {
                if (kfVal && typeof kfVal === 'object' && !Array.isArray(kfVal) && kfVal.lerp_mode === 'catmullrom') {
                  const hasDynamic = ['pre', 'post'].some(
                    (side) => Array.isArray(kfVal[side]) && kfVal[side].some((v) => typeof v === 'string')
                  );
                  if (hasDynamic) {
                    errors.push(
                      `[ANIM_CATMULLROM] "${rel}" (${animName} -> ${boneName}.${channel} @ ${kfTime}): keyframes catmullrom exigem números constantes (sem Molang).`
                    );
                  }
                }
              }
            }
          }
        }
      }
    }

    // 3c. Texture sets: checagem explícita de exclusividade mútua (MER vs MERS / normal vs heightmap)
    if (rel.endsWith('.texture_set.json') && data?.['minecraft:texture_set']) {
      const ts = data['minecraft:texture_set'];
      if (ts.normal && ts.heightmap) {
        errors.push(`[PBR_CONFLICT] "${rel}": "normal" e "heightmap" são mutuamente exclusivos.`);
      }
      if (ts.metalness_emissive_roughness && ts.metalness_emissive_roughness_subsurface) {
        errors.push(
          `[PBR_CONFLICT] "${rel}": "metalness_emissive_roughness" (MER) e "metalness_emissive_roughness_subsurface" (MERS) são mutuamente exclusivos.`
        );
      }
    }
  }

  return { errors, warnings };
}

/**
 * Executa auditoria completa de um diretório de projeto Add-on (padrão: `addon/`),
 * validando todos os JSONs mapeados contra os schemas locais da Blockception e
 * verificando as regras do Minecraft Bedrock 1.26.65+ (26.65+).
 *
 * @param {string} targetDir - Caminho do diretório raiz do Add-on.
 * @returns {boolean} True se aprovado sem erros críticos; false caso contrário.
 */
function auditAddonProject(targetDir) {
  const projectDir = path.resolve(targetDir);
  if (!fs.existsSync(projectDir)) {
    console.error(`❌ [ERRO] Diretório do add-on não encontrado: ${projectDir}`);
    return false;
  }

  const schemasDir = resolveBlockceptionSchemasDir();
  if (!schemasDir) {
    console.error(
      '❌ [ERRO] Diretório de schemas da Blockception (@minecraft/creator-tools/res/latest/schemas) não encontrado. Defina BLOCKCEPTION_SCHEMAS_DIR.'
    );
    return false;
  }

  // Registra todos os schemas disponíveis para suportar referências internas/cruzadas
  preloadCrossRefSchemas(schemasDir);

  console.log(`🔍 [AUDITORIA 26.65+] Projeto: ${projectDir}`);
  console.log(`📚 [SCHEMAS] Blockception local: ${schemasDir}`);

  const allFiles = collectFiles(projectDir);
  let schemaCheckedCount = 0;
  let schemaFailedCount = 0;

  // 1. Validação por JSON Schema da Blockception para cada arquivo mapeado
  for (const filePath of allFiles) {
    if (!filePath.endsWith('.json')) continue;
    const relNormalized = path.relative(projectDir, filePath).replace(/\\/g, '/');
    const rule = PACK_SCHEMA_RULES.find((r) => r.match.test(relNormalized));
    if (!rule) continue;

    const schemaFullPath = path.join(schemasDir, rule.schema);
    if (!fs.existsSync(schemaFullPath)) {
      console.warn(`⚠️ [AVISO] Schema ausente no pacote local: ${rule.schema}`);
      continue;
    }

    schemaCheckedCount++;
    const ok = validateJson(schemaFullPath, filePath, true);
    if (ok) {
      console.log(`  ✅ [${rule.label}] ${relNormalized}`);
    } else {
      schemaFailedCount++;
    }
  }

  // 2. Validação das regras arquiteturais de domínio Bedrock 1.26.65+
  const { errors: domainErrors, warnings: domainWarnings } = runBedrock2665DomainChecks(projectDir, allFiles);

  if (domainWarnings.length > 0) {
    console.log('\n⚠️ Avisos de integridade:');
    domainWarnings.forEach((w) => console.warn(`  - ${w}`));
  }

  if (domainErrors.length > 0) {
    console.error('\n❌ Violações Bedrock 1.26.65+ (26.65+) detectadas:');
    domainErrors.forEach((e) => console.error(`  - ${e}`));
  }

  const totalErrors = schemaFailedCount + domainErrors.length;
  console.log(
    `\n📊 [RESUMO] ${schemaCheckedCount} arquivo(s) validado(s) contra schemas Blockception | ${totalErrors} erro(s) | ${domainWarnings.length} aviso(s).`
  );

  return totalErrors === 0;
}

// ==============================================================================
// 4. PONTO DE ENTRADA VIA LINHA DE COMANDO
// ==============================================================================

if (require.main === module) {
  const args = process.argv.slice(2);

  if (args.length === 0) {
    // Chamada padrão sem argumentos (`npm run validate`): audita a pasta `addon/`
    const defaultAddonDir = path.resolve(process.cwd(), 'addon');
    const success = auditAddonProject(defaultAddonDir);
    process.exit(success ? 0 : 1);
  } else if (args.length === 1) {
    // Chamada com um único argumento: audita o diretório informado
    const success = auditAddonProject(args[0]);
    process.exit(success ? 0 : 1);
  } else {
    // Modo clássico pontual: <schema.json> <arquivo_alvo.json>
    const [schemaFile, targetFile] = args;
    const success = validateJson(schemaFile, targetFile);
    process.exit(success ? 0 : 1);
  }
}

module.exports = { validateJson, auditAddonProject };
