/**
 * ==============================================================================
 * api/status.js — Endpoint Serverless de Telemetria e Verificação do BDS (Root)
 * ==============================================================================
 * Espelho do endpoint site/api/status.js para garantir funcionamento caso o
 * projeto na Vercel utilize a raiz do repositório como Root Directory.
 * ==============================================================================
 */

const statusHandler = require('./site/api/status.js');
module.exports = statusHandler;
