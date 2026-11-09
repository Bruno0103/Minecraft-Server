/**
 * ==============================================================================
 * tools/validate_addon.js — Validação Estática Programática de Add-ons Bedrock
 * ==============================================================================
 * Este script utiliza o motor de validação Ajv (com suporte completo a formatos
 * JSON Schema Draft-07 via ajv-formats) para validar arquivos de configuração,
 * manifestos e componentes de Add-ons Bedrock contra os esquemas formais da comunidade
 * e da Mojang (mantidos pelo projeto Blockception).
 *
 * Uso via linha de comando:
 *   node tools/validate_addon.js <caminho_schema.json> <caminho_alvo.json>
 *
 * Código de saída (Exit Code):
 *   0: Validação bem-sucedida, sem erros de conformidade estrutural.
 *   1: Falha de esquema ou erro de sintaxe detectado no arquivo analisado.
 */

const fs = require('fs');
const path = require('path');
const Ajv = require('ajv');
const addFormats = require('ajv-formats');

// Inicialização do validador Ajv com coleta exaustiva de erros e modo tolerante
const ajv = new Ajv({ 
  allErrors: true, 
  strict: false, 
  verbose: true 
});

// Extensão com formatos adicionais como 'uri', 'uuid', 'date-time'
addFormats(ajv);

/**
 * Valida um arquivo JSON de destino contra um JSON Schema oficial.
 *
 * @param {string} schemaPath - Caminho para o arquivo de esquema JSON.
 * @param {string} targetPath - Caminho para o arquivo de dados/manifesto a ser validado.
 * @returns {boolean} Retorna true se válido, false se houver discrepâncias estruturais.
 */
function validateJson(schemaPath, targetPath) {
  try {
    if (!fs.existsSync(schemaPath)) {
      console.error(`❌ [ERRO] Arquivo de schema não encontrado: ${schemaPath}`);
      return false;
    }
    if (!fs.existsSync(targetPath)) {
      console.error(`❌ [ERRO] Arquivo alvo de validação não encontrado: ${targetPath}`);
      return false;
    }

    const schemaRaw = fs.readFileSync(schemaPath, 'utf-8');
    const targetRaw = fs.readFileSync(targetPath, 'utf-8');

    const schema = JSON.parse(schemaRaw);
    const target = JSON.parse(targetRaw);

    const validate = ajv.compile(schema);
    const valid = validate(target);

    if (valid) {
      console.log(`✅ [VALIDAÇÃO SUCESSO] ${path.basename(targetPath)} está em conformidade estrita com ${path.basename(schemaPath)}!`);
      return true;
    } else {
      console.error(`❌ [FALHA DE ESQUEMA] Erros encontrados em ${path.basename(targetPath)}:`);
      validate.errors.forEach((err, i) => {
        const campo = err.instancePath || '(raiz)';
        console.error(`  #${i + 1} Campo: ${campo} | Erro: ${err.message}`);
      });
      return false;
    }
  } catch (error) {
    console.error(`❌ [ERRO INESPERADO] Falha durante o processamento da validação:`, error.message);
    return false;
  }
}

// Ponto de entrada via linha de comando
if (require.main === module) {
  const args = process.argv.slice(2);
  if (args.length < 2) {
    console.log("Uso: node tools/validate_addon.js <schema.json> <arquivo_para_validar.json>");
    process.exit(1);
  }

  const [schemaFile, targetFile] = args;
  const success = validateJson(schemaFile, targetFile);
  process.exit(success ? 0 : 1);
}

module.exports = { validateJson };

