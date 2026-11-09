# 🌐 Never Land — Portal Web Oficial (Stitch Integration)

> 🚀 **Site no ar em produção:** [https://neverland-bedrock.vercel.app](https://neverland-bedrock.vercel.app)

Este módulo contém o portal web oficial do ecossistema **Never Land**, exportado e sincronizado a partir do projeto **Neverland** no **Google Stitch**.

---

## 📌 Visão Geral do Portal

O site foi concebido com uma estética de **Neo-Brutalism & Gamified Cyber-Fantasy (Obsidian Voxel Dark Theme)**, casando perfeitamente com a identidade visual do servidor de Minecraft Bedrock (BDS) hospedado na Oracle Cloud Infrastructure (OCI Always Free).

### Principais Objetivos da Interface:
1. **Conexão Instantânea**: Permitir que qualquer jogador no Android, iOS, Windows ou Consoles copie o IP (`never-land.rocks`) e a porta (`19132`) com 1 clique.
2. **Transparência de Add-ons**: Listar todos os 10 complementos e pacotes de texturas ativos no servidor, fornecendo links oficiais (CurseForge), status de compatibilidade e guias detalhados de funcionamento.
3. **Tutorial Visual Guiado**: Carrossel explicativo em 5 passos ilustrado com capturas reais do Minecraft Bedrock, detalhando como adicionar o servidor na aba *Servidores*.
4. **Governança Comunitária**: Apresentação clara do Código de Honra (anti-griefing, cultivo mútuo, ajuda comunitária e respeito mútuo) e votação interativa de obras públicas.

---

## 📂 Estrutura de Arquivos

```text
site/
├── index.html                   # Documento HTML principal com Tailwind CSS, JavaScript modular e temas
├── vercel.json                  # Configuração de rotas limpas e cache estático para a Vercel
├── README.md                    # Esta documentação do portal
└── assets/
    └── images/                  # Imagens e capturas estáticas otimizadas para carregamento offline/local
        ├── minecraft_realm_icon.jpg # Ícone oficial do reino
        ├── never_land_hero.jpg      # Imagem hero do Spawn Comunitário
        └── tutorial/            # Capturas passo a passo para o guia de conexão
            ├── passo1_menu_jogar.png         # Passo 1: Menu inicial e botão 'Jogar'
            ├── passo2_aba_servidores.png     # Passo 2: Seleção da aba 'Servidores'
            ├── passo3_adicionar_servidor.png # Passo 3: Botão '+ Adicionar servidor'
            ├── passo4_preencher_dados.png    # Passo 4: Campos IP e Porta
            └── passo5_salvar_jogar.png       # Passo 5: Salvar e conectar
```

---

## 🎨 Design System: Neverland Obsidian Voxel

O portal utiliza uma paleta escura inspirada em blocos do subterrâneo do Minecraft (Obsidiana, Deepslate e Bedrock), realçada por iluminação bioluminescente:

| Nome do Token | Hexadecimal | Uso Principal |
| :--- | :---: | :--- |
| **Dark Surface** | `#0B0F17` | Fundo principal da página e base de contraste |
| **Dark Surface Container** | `#151C28` | Superfície de cartões, barras fixas e modais |
| **Dark Primary** | `#4ADE80` | Destaque verde esmeralda, botões de ação e status ativo |
| **Accent Cyan** | `#00E5FF` | Destaque de endereços IP e identificadores de rede |
| **Gold / Warning** | `#FBBF24` | Porta do servidor (19132), conquistas Xbox e tags de destaque |
| **Success** | `#22C55E` | Indicador LED de servidor online |
| **Error** | `#EF4444` | Alertas de griefing e regras mandatórias |

### Tipografia
- **Títulos e Logotipo**: `Silkscreen` / `Space Grotesk` (estilo pixelado/geométrico).
- **Corpo de Texto**: `Plus Jakarta Sans` / `Chivo` (alta legibilidade em telas escuras).
- **Telemetria, Código e IPs**: `JetBrains Mono` (caracteres tabulares monoespaçados).

---

## 🛠️ Tecnologias Utilizadas

- **HTML5 Semântico**: Estruturado com tags `<header>`, `<nav>`, `<section>`, `<article>` e `<footer>`.
- **Tailwind CSS (via CDN)**: Configuração personalizada com extensões de temas, cores customizadas, sombras com brilho volumétrico e suporte a breakpoints responsivos (Mobile, Tablet, Desktop).
- **Google Fonts & Material Symbols**: Ícones vetoriais modernos e conjunto tipográfico refinado.
- **JavaScript Vanilla (ES6+)**:
  - `copyServerDetails()`: Copia credenciais com animação de toast e fallback robusto.
  - `testServerPing()`: Simulação interativa de ping/latência em milissegundos para o host OCI.
  - `filterDesktopAddons()` & `searchDesktopAddons()`: Sistema instantâneo de busca e filtragem de addons.
  - `setDesktopHelpStep()`: Navegação entre os 5 slides do carrossel do tutorial.
  - `zoomImage()`: Lightbox em tela cheia com ampliação de capturas de tela.
  - `voteProject()`: Sistema de votação reativo para expansões do reino.

---

## 🚀 Como Visualizar e Testar Localmente

### Opção 1: Servidor HTTP Local com Node.js / npx
Execute na raiz do projeto:
```bash
npm run site:serve
```
Ou diretamente:
```bash
npx serve site -l 3000
```
Depois, acesse no navegador: `http://localhost:3000`

### Opção 2: Python HTTP Server
```bash
python -m http.server 8000 --directory site
```
Depois, acesse no navegador: `http://localhost:8000`

### Opção 3: Extensão Live Server (VS Code)
Abra o arquivo `site/index.html` no VS Code e clique no botão **"Go Live"** no rodapé.

---

## ☁️ Publicação e Deploy na Vercel

O diretório `site/` já conta com o arquivo `vercel.json` configurado para rotas limpas e cache estático de alta performance. Existem 3 formas de publicar:

### Método 1: Integração Contínua com GitHub (Recomendado para Produção)
1. Conecte este repositório do GitHub ao painel da [Vercel](https://vercel.com).
2. Na criação do projeto, configure:
   - **Framework Preset**: `Other`
   - **Root Directory**: `site`
3. Qualquer commit enviado para a branch principal disparará um deploy automático em produção com certificado SSL gratuito.

### Método 2: Deploy Direto via Vercel CLI com Token
1. Gere um Token em [Vercel Account Tokens](https://vercel.com/account/tokens).
2. Defina a variável de ambiente no seu terminal:
   ```powershell
   $env:VERCEL_TOKEN="seu_token_aqui"
   ```
3. O assistente de IA ou você pode executar diretamente:
   ```powershell
   npx vercel deploy site --prod --yes --token $env:VERCEL_TOKEN
   ```

### Método 3: Plugin Oficial Vercel no Antigravity
Instale o plugin Vercel através do Marketplace para autenticar com sua conta e permitir que a IA realize operações e deploys nativamente via MCP.


