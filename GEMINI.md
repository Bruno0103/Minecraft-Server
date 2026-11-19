# Diretrizes do Projeto — Minecraft Bedrock Server & Add-ons

## 1. Fontes Oficiais de Documentação e Referência
Ao desenvolver, revisar ou depurar Behavior Packs, Resource Packs, JSON UI, Molang, Schemas e Script API, utilize exclusivamente como fontes de referência:
- **Documentação Oficial da Mojang** (Microsoft Learn / Minecraft Creator Docs)
- **Bedrock Wiki:** https://wiki.bedrock.dev/
- **Bedrock.dev:** https://bedrock.dev/
- **GitHub Oficial da Mojang:** https://github.com/Mojang (`bedrock-samples`, `minecraft-scripting-libraries`, `creator-tools`)
- **Ecossistema e Ferramentas Bedrock OSS:** `bridge.`, `Regolith` e `mctools` (`@minecraft/creator-tools` / `mct`)

## 2. Script API, Tipagens TypeScript (`index.d.ts`) e Versão do Servidor
- **Sincronização com a Versão Atual do Servidor:** Sempre verifique a versão atual do servidor Minecraft Bedrock (BDS) antes de definir `"min_engine_version"`, `"format_version"` ou versões de dependências em `BP/manifest.json` e `package.json` (`npm install @minecraft/server @minecraft/server-ui`).
- **Inspeção Obrigatória de `node_modules/@minecraft/`:** Os arquivos de definição TypeScript locais (`node_modules/@minecraft/server/index.d.ts`, `node_modules/@minecraft/server-ui/index.d.ts`, `node_modules/@minecraft/vanilla-data/index.d.ts`, etc.) são a **fonte primária e definitiva de verdade** para classes, métodos, eventos e componentes da Script API.
- Sempre consulte os arquivos `index.d.ts` em `node_modules/@minecraft/` e valide com `npm run type-check` (`tsc --noEmit`) para evitar o uso de APIs depreciadas (1.x) ou incompatíveis com a versão atual do servidor.
