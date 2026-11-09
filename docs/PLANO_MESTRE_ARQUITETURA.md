# PLANO MESTRE DE ARQUITETURA, DESENVOLVIMENTO COM IA E GOVERNANÇA DE SERVIDORES
## Minecraft Bedrock Edition (Add-ons, Render Dragon PBR, Scripts TypeScript e Oracle Cloud Infrastructure)

**Projeto:** Servidor Dedicado Minecraft Bedrock "Never Land"  
**Versão Alvo:** Minecraft Bedrock 1.21+ (Render Dragon / Deferred Lighting / Vibrant Visuals)  
**Ambiente:** Híbrido — Estação de Desenvolvimento Windows 11 + Servidor Linux na Oracle Cloud Infrastructure (OCI Always Free)  
**Status do Plano:** Aprovado e Pronto para Execução Industrial  
**Idioma:** Português do Brasil (PT-BR)  

---

## 1. Sumário Executivo e Arquitetura Geral Integrada

### 1.1 Visão Sistêmica do Ecossistema Híbrido

O presente documento estabelece a arquitetura de engenharia unificada e o plano mestre operacional para a criação, compilação, teste e governança de produção de add-ons, texturas de alta fidelidade e infraestrutura de servidores dedicados (**Bedrock Dedicated Server - BDS**) para Minecraft Bedrock Edition.

O ecossistema foi projetado para operar sob um modelo **Híbrido e Simbiótico**, potencializado pela colaboração entre desenvolvedores humanos e **Agentes Autônomos de Inteligência Artificial** orquestrados via protocolo **Model Context Protocol (MCP)**:

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                    ESTAÇÃO DE TRABALHO LOCAL (WINDOWS 11 - WORKSTATION DE IA)                   │
├─────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                 │
│  [ Agente de IA / Engenheiro de Software ]                                                       │
│         │                                                                                       │
│         ├──► [ Modelagem 3D & Rigging ] ──► sosadly/blockbench-mcp / jasonjgardner (Porta 8787) │
│         ├──► [ Pixel Art 2D & Texturas ] ─► willibrandon/pixel-mcp / mhpm/aseprite-studio (7790)│
│         ├──► [ Pipeline PBR / DirectX ] ──► Script Python (Albedo -> MER + Sobel Tangent Normal)│
│         ├──► [ Partículas & Molang ] ─────► JannisX11/snowstorm / Blockception Schemas          │
│         ├──► [ Scripts TypeScript ] ──────► @minecraft/server / esbuild / jaylydev-docs         │
│         └──► [ Compilação Não-Destrutiva ]► Bedrock-OSS/regolith (rolldown, optipng, mcaddon)   │
│                                                                                                 │
└───────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                │
                                    Túnel Seguro SSH / SFTP
                         (devnullvoid/mcp-ssh-session / Chave Ed25519)
                                                │
                                                ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                     NUVEM ORACLE CLOUD INFRASTRUCTURE (OCI ALWAYS FREE LINUX)                    │
├─────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                 │
│  [ Instância Oracle Linux 9 / RHEL ] (VM.Standard.E2.1.Micro ou VM.Standard.A1.Flex)            │
│         │                                                                                       │
│         ├──► [ Borda de Nuvem (OCI VCN) ] ─► Security List / NSG: UDP 19132/19133 e TCP 19132   │
│         │                                   (RakNet UDP + NetherNet WebRTC Signaling TCP)       │
│         ├──► [ Firewall do Kernel Linux ] ──► firewalld / iptables: UDP 19132/19133 e TCP 19132 │
│         ├──► [ Painel de Controle SSH ] ───► mc.py (Console Interativo, Cgroups, FIFO & Journal)│
│         ├──► [ Orquestrador Systemd ] ─────► bedrock.service (Wants=network-online.target)       │
│         │         │                         StartLimitIntervalSec=0 (Reconexão Infinita)        │
│         │         ├──► [ Console FIFO ] ───► bedrock.socket (/run/bedrock.console)              │
│         │         ├──► [ Cgroups v2 ] ─────► MemoryHigh=600M / MemoryMax=750M (Swap 5GB)        │
│         │         └──► [ Encerramento ] ───► bedrock_stop.sh (Parada Graciosa LevelDB)          │
│         │                                                                                       │
│         ├──► [ Hot Backup LevelDB ] ───────► bedrock-backup.timer (04:00 Diário, Persistent)    │
│         │                                   save hold -> save query -> truncate -s -> save resume│
│         │                                   Retenção de 7 dias + Sync OCI Object Storage        │
│         │                                                                                       │
│         └──► [ Alternativa Docker ] ───────► itzg/docker-minecraft-bedrock-server (UDP/TCP)     │
│                                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Papel e Benefícios da Assistência por Inteligência Artificial
A integração do protocolo MCP elimina os gargalos históricos no desenvolvimento para Minecraft Bedrock:
1. **Prevenção Total de Alucinações de Sintaxe:** O agente de IA não adivinha nomes de componentes nem mistura especificações de Java Edition. A IA é amarrada à documentação oficial via **Context7 MCP**, schemas JSON da comunidade via **Blockception** e documentação dinâmica via **jaylydev/scriptapi-docs**.
2. **Ciclo Visual com Feedback em Tempo Real:** Através de ferramentas como `sosadly/blockbench-mcp` (com visão raio-X e folhas de contato) e `WebritroHQ/minecraft-structure-mcp` (com renderizador isométrico), o modelo "enxerga" suas criações em 3D e corrige deformidades antes da compilação.
3. **Automação de Engenharia e Build:** O compilador **Regolith** permite que o agente edite arquivos puros de TypeScript e JSON sem contaminar o repositório com artefatos temporários, gerando pacotes `.mcaddon` validados e otimizados automaticamente.
4. **Governança Remota Confiável:** A operação remota no Linux da Oracle Cloud ocorre via sessões multiplexadas seguras (`mcp-ssh-session`), garantindo desligamentos graciosos e preservação matemática do banco de dados LevelDB.

---

## 2. Matriz Geral de Classificação de MCPs, Skills e Ferramentas

A tabela a seguir consolida todas as ferramentas, servidores MCP, compiladores e utilitários investigados ao longo das 16 microtarefas especializadas, com seus metadados técnicos oficiais e justificativas operacionais:

| # | Ferramenta / Servidor MCP | Repositório Oficial no GitHub / Fonte | Licença | Protocolo / Transporte | Portas / Dependências | Categoria | Justificativa Técnica para o Ecossistema de IA |
| :-: | :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **01** | **sosadly/blockbench-mcp** | [sosadly/blockbench-mcp](https://github.com/sosadly/blockbench-mcp) | MIT | `stdio` (IA) + `HTTP` (Bridge) | Porta `8787` (local), Node.js 18+, Blockbench 4.8+ | **Indispensável** | 86 ferramentas para modelagem 3D, rigs de 3 ossos/membro, feedback visual com raio-X e gate de revisão humana (`request_review`). |
| **02** | **jasonjgardner/blockbench-mcp-plugin** | [jasonjgardner/blockbench-mcp-plugin](https://github.com/jasonjgardner/blockbench-mcp-plugin) | MIT | `stdio` (Headless) / `HTTP` (GUI) | Porta `3000/bb-mcp`, Node.js 23.6+ / Bun, WebGPU Dawn | **Indispensável** | Edição atômica de arquivos `.bbmodel` e renderização de imagens via WebGPU Dawn sem interface gráfica aberta; ideal para CI/CD e multi-agentes. |
| **03** | **willibrandon/pixel-mcp** | [willibrandon/pixel-mcp](https://github.com/willibrandon/pixel-mcp) | MIT | `stdio` (Binário Go) | Aseprite CLI oficial (`aseprite -b`), Go 1.23+ | **Indispensável** | Controle total do motor do Aseprite via scripts Lua, 15 padrões de dithering, quantização de cores CIELAB e geração de spritesheets com metadados. |
| **04** | **mhpm/aseprite-mcp-studio** | [mhpm/aseprite-mcp-studio](https://github.com/mhpm/aseprite-mcp-studio) | MIT | `stdio` (Python `uv`) | Porta `7790` (Live SSE), Python 3.10+ | **Indispensável** | Lê/grava binário `.aseprite` em milissegundos sem o Aseprite instalado; algoritmo FFT/DP para remover grade borrada de IAs generativas e rigging RotSprite. |
| **05** | **light-vp/pixelart-mcp** | [light-vp/pixelart-mcp](https://github.com/light-vp/pixelart-mcp) | MIT | `stdio` (Python) | Python 3.10+, Pillow | **Opcional de Grande Ajuda** | Otimização drástica de tokens (reduz até 90% dos tokens de imagem via `preview_diff`) e primitivas de desenho vetorial (polígonos e curvas de Bézier). |
| **06** | **adrianoamaral/pixel-mcp** | [adrianoamaral/pixel-mcp](https://github.com/adrianoamaral/pixel-mcp) | MIT | `stdio` (Node.js) | Porta `3000` (viewer.html), Node.js 18+ | **Opcional de Grande Ajuda** | Prototipagem ultra-rápida de pixel art em TypeScript com canvas de inspeção web leve. |
| **07** | **Script Utilitário PBR Bedrock** | Repositório Interno do Projeto | MIT | CLI / Python Script | Python 3.10+, Pillow, NumPy | **Indispensável** | Empacota canais isolados no mapa unificado MER (RGB), gera normais DirectX (Y-down) via filtro Sobel e cria manifestos `.texture_set.json`. |
| **08** | **JannisX11/snowstorm** | [JannisX11/snowstorm](https://github.com/JannisX11/snowstorm) | GPL-3.0 | WebGL / VS Code Extension | VS Code 1.60+, Webview Three.js | **Indispensável** | Editor canônico de partículas `.particle.json` do Bedrock com simulação de física, vetores e curvas de cor em tempo real. |
| **09** | **Bedrock-OSS/regolith** | [Bedrock-OSS/regolith](https://github.com/Bedrock-OSS/regolith) | MIT | CLI Autônoma (Go) | Git, Go runtime, Python/Node/Deno | **Indispensável** | Compilador não-destrutivo com arquitetura de filtros em pipeline; automatiza transpilação TypeScript, compressão PNG e geração de `.mcaddon`. |
| **10** | **Bedrock-OSS/regolith-action** | [Bedrock-OSS/regolith-action](https://github.com/Bedrock-OSS/regolith-action) | MIT | GitHub Actions Workflow | GitHub Actions Runner (Ubuntu) | **Indispensável** | Automação contínua em CI/CD para compilar, validar tipagem estática e gerar releases de add-ons automaticamente a cada push. |
| **11** | **Blockception Schemas** | [Blockception/Minecraft-bedrock-json-schemas](https://github.com/Blockception/Minecraft-bedrock-json-schemas) | CC-BY-4.0 / MIT | JSON Schema Draft-07 | VS Code / Ajv Validator | **Indispensável** | Fonte da verdade para validação estrutural de manifestos, blocos, itens, entidades, animações e partículas Bedrock, eliminando alucinações. |
| **12** | **bridge-core/editor (Bridge v2)** | [bridge-core/editor](https://github.com/bridge-core/editor) | GPL-3.0 | GUI Desktop / Electron | Node.js, Electron | **Opcional de Grande Ajuda** | Excelente IDE visual para desenvolvedores humanos; em fluxos de IA autônomos, é superada pelo par VS Code + Regolith + Ajv. |
| **13** | **@minecraft/server** | `npm:@minecraft/server` | Mojang EULA | In-Process (Engine Bedrock) | QuickJS / V8 embutido no Minecraft 1.21+ | **Indispensável** | API principal da Mojang para scripting moderno em JavaScript/TypeScript; controla entidades, blocos, dimensões, tarefas e lógica de jogo. |
| **14** | **@minecraft/server-ui** | `npm:@minecraft/server-ui` | Mojang EULA | In-Process (Engine Bedrock) | Minecraft Bedrock 1.21+ | **Indispensável** | Fornece janelas modais (`ActionFormData`, `ModalFormData`, `MessageFormData`) para interação interativa entre jogador e servidor. |
| **15** | **@minecraft/server-admin** | `npm:@minecraft/server-admin` | Mojang EULA | In-Process (BDS apenas) | Bedrock Dedicated Server (Linux/Windows) | **Opcional de Grande Ajuda** | Permite leitura de variáveis e segredos de ambiente no servidor dedicado sem expor chaves no pacote. |
| **16** | **@minecraft/server-net** | `npm:@minecraft/server-net` | Mojang EULA | In-Process (BDS apenas) | BDS (requer permissions.json) | **Opcional de Grande Ajuda** | Permite requisições HTTP externas a partir de scripts BDS para conectar o jogo a webhooks e bancos de dados externos. |
| **17** | **esbuild** | [evanw/esbuild](https://github.com/evanw/esbuild) | MIT | CLI / API Node.js | Node.js 18+ | **Indispensável** | Bundler ultra-rápido em Go que transmpila TypeScript para ESM em menos de 20ms, acelerando o ciclo de iteração da IA. |
| **18** | **jaylydev/scriptapi-docs** | [JaylyDev/scriptapi-docs](https://github.com/JaylyDev/scriptapi-docs) | MIT | Markdown / Web Docs | Browser / Leitor de Docs | **Indispensável** | Referência canônica e histórico de diffs entre versões da Script API, impedindo que a IA gere métodos descontinuados. |
| **19** | **Mojang/bedrock-samples** | [Mojang/bedrock-samples](https://github.com/Mojang/bedrock-samples) | Microsoft EULA | Repositório Git | Git | **Indispensável** | Template ouro com todos os ativos e esquemas oficiais baunilha da versão mais recente do Minecraft Bedrock. |
| **20** | **Mming-Lab/minecraft-bedrock-mcp** | [Mming-Lab/minecraft-bedrock-education-mcp](https://github.com/Mming-Lab/minecraft-bedrock-education-mcp) | MIT | `stdio` (IA) + WebSocket (`wss://`) | Portas `8080` ou `3000`, Node.js 18+ | **Indispensável** | Conecta o agente de IA ao Minecraft em tempo real via WebSocket para executar comandos administrativos e gerar geometrias volumétricas. |
| **21** | **rgbkrk/rcon-mcp** | [rgbkrk/rcon-mcp](https://github.com/rgbkrk/rcon-mcp) | Apache-2.0 / MIT | `stdio` (IA) + TCP RCON | Porta `25575` (TCP), Node.js 18+ | **Indispensável** | Emissão remota de comandos de console administrativo via protocolo padrão RCON (TCP), ideal para ambientes Linux e Docker. |
| **22** | **Peterson047/Minecraft-MCP-Server** | [Peterson047/Minecraft-MCP-Server](https://github.com/Peterson047/Minecraft-MCP-Server) | MIT | `stdio` (Python FastMCP) | Porta `25575` (TCP), Python 3.10+ | **Opcional de Grande Ajuda** | Alternativa em Python com FastMCP para comunicação RCON com servidores Minecraft. |
| **23** | **WebritroHQ/minecraft-structure-mcp** | [WebritroHQ/minecraft-structure-mcp](https://github.com/WebritroHQ/minecraft-structure-mcp) | MIT | `stdio` (Operação Offline) | Node.js 18+ | **Indispensável** | Manipulador de estruturas binárias `.mcstructure` e renderizador isométrico offline para auto-avaliação estética por IA. |
| **24** | **LiteLDev/LeviLamina** | [LiteLDev/LeviLamina](https://github.com/LiteLDev/LeviLamina) | LGPL-3.0 / GPL-3.0 | Memory Detours / Hooks | BDS Linux ELF / Windows PE | **Opcional de Grande Ajuda** | Mod loader C++ para BDS com hooking em memória; indispensável caso sejam necessários plugins de segurança ou RPC nativo. |
| **25** | **GeyserMC / Floodgate** | [GeyserMC/Geyser](https://github.com/GeyserMC/Geyser) | MIT | UDP RakNet <-> TCP Java | Portas `19132` / `25565`, Java 17+ | **Opcional de Grande Ajuda** | Camada de crossplay para jogadores Bedrock entrarem em servidores Java; incompatível com scripts e blocos nativos Bedrock. |
| **26** | **devnullvoid/mcp-ssh-session** | [devnullvoid/mcp-ssh-session](https://github.com/devnullvoid/mcp-ssh-session) | MIT | `stdio` (Python) + SSH | Porta `22` (SSH), Python 3.10+ | **Indispensável** | Conexão SSH multiplexada persistente, suporte a SFTP bidirecional e execução assíncrona inteligente sem timeouts síncronos. |
| **27** | **tufantunc/ssh-mcp** | [tufantunc/ssh-mcp](https://github.com/tufantunc/ssh-mcp) | MIT | `stdio` (Node.js) + SSH | Porta `22` (SSH), Node.js 18+ | **Opcional de Grande Ajuda** | Execução rápida e isolada de comandos pontuais via TypeScript/npx. |
| **28** | **OCI CLI Oficial (`oci-cli`)** | [oracle/oci-cli](https://github.com/oracle/oci-cli) | UPL / Apache 2.0 | CLI Executável | Python 3.8+, OCI API Key | **Indispensável** | Gestão determinística da nuvem Oracle (ciclo de vida da VM, auditoria de redes VCN, quotas e recuperação de desastre). |
| **29** | **jopsis/mcp-server-oci** | [jopsis/mcp-server-oci](https://github.com/jopsis/mcp-server-oci) | Open / Community | `stdio` (Python) | OCI SDK, Python 3.10+ | **Opcional de Grande Ajuda** | Permite auditoria de infraestrutura e status da nuvem OCI por comandos em linguagem natural. |
| **30** | **itzg/docker-minecraft-bedrock-server** | [itzg/docker-minecraft-bedrock-server](https://github.com/itzg/docker-minecraft-bedrock-server) | MIT | Docker Engine | Docker, porta `19132/udp` | **Indispensável (se Docker)** | Imagem padrão multi-arch de BDS com auto-update, injeção de ambiente e isolamento em contêineres. |
| **31** | **QuantGeekDev/docker-mcp** | [QuantGeekDev/docker-mcp](https://github.com/QuantGeekDev/docker-mcp) | MIT | `stdio` (Node.js) | Docker Socket local | **Opcional de Grande Ajuda** | Automação do ciclo de vida de contêineres e inspeção de logs via Docker socket. |
| **32** | **bedrock.service (Systemd)** | Produção Interna do Projeto | GPL / MIT | Linux Systemd Daemon | Linux Kernel 5.x+, Cgroups v2 | **Indispensável** | Autostart no boot, resiliência anti-desistência (`StartLimitIntervalSec=0`), Cgroups v2 (`MemoryMax=750M`) e proteção OOM. |
| **33** | **bedrock.socket (Console FIFO)** | Produção Interna do Projeto | GPL / MIT | Socket Activation POSIX | Caminho `/run/bedrock.console` | **Indispensável** | Canal limpo que permite enviar comandos (`stop`, `say`, `list`) diretamente ao stdin do servidor sem necessitar de PTY/screen. |
| **34** | **bedrock_stop.sh** | Produção Interna do Projeto | Bash Script | Shell POSIX | Bash 4+, PID do BDS | **Indispensável** | Encerramento gracioso que aguarda a descarga de buffers e previne corrupção de chunks do banco LevelDB. |
| **35** | **Firewalld / Iptables (UDP 19132)** | Nativo Linux | GPL-2.0 | Filtro de Pacotes de Rede | UDP 19132 e 19133 | **Indispensável** | Liberação persistente das portas de tráfego do jogo no sistema operacional Linux da nuvem. |
| **36** | **Protocolo Hot Backup LevelDB** | Padrão Oficial Mojang | Padrão BDS | Script Bash (`hot_backup.sh`) | `save hold`, `query`, `resume` | **Indispensável** | Garante cópias consistentes e atômicas do banco LevelDB em tempo de execução através do truncamento exato de bytes. |
| **37** | **Sincronização OCI Object Storage** | OCI Cloud Native | API REST / OCI CLI | Bucket OCI na região sa-saopaulo-1 | **Indispensável** | Contingência externa que protege o servidor contra exclusão acidental ou pane do disco de boot da instância. |
| **38** | **Filesystem MCP** | [modelcontextprotocol/servers](https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem) | MIT | `stdio` (Node.js) | Node.js 18+ | **Indispensável** | Manipulação segura, leitura e escrita determinística em sandbox nos diretórios do projeto local. |
| **39** | **GitHub MCP** | [modelcontextprotocol/servers](https://github.com/modelcontextprotocol/servers/tree/main/src/github) | MIT | `stdio` (Node.js) | Token GitHub com escopo repo | **Indispensável** | Versionamento, rastreabilidade de alterações, commits semânticos e releases automatizadas de pacotes. |
| **40** | **Context7 MCP** | [upstash/context7](https://github.com/upstash/context7) | MIT | `stdio` / SSE (Node.js) | Node.js 18+ | **Indispensável** | Consulta obrigatória de documentação oficial indexada por versão para eliminação de alucinações de IA. |
| **41** | **Fetch / Web MCP** | [modelcontextprotocol/servers](https://github.com/modelcontextprotocol/servers/tree/main/src/fetch) | MIT | `stdio` (Python/Node) | uvx / npx | **Opcional de Grande Ajuda** | Extração e conversão semântica de páginas web e referências da Mojang diretamente para Markdown limpo. |
| **42** | **mc.py (Controle Operacional SSH BDS)** | `Server/systemd/mc.py` (Nativo do Projeto) | MIT | CLI / REPL Nativo em Python | Python 3.9+, FIFO, Journald, Linux Cgroups v2 | **Indispensável** | Utilitário oficial de administração remota via SSH atendendo a R2; opera via FIFO não-bloqueante (`O_NONBLOCK`), cursor do journald, monitora Cgroups e gerencia status, logs, backups e console. |

> **Nota Técnica de Esclarecimento sobre `ItsAshn/blockbench-mcp-plus`:** Conforme investigado na Microtarefa 1, o repositório `ItsAshn/blockbench-mcp-plus` citado em R1 é uma variação comunitária focada em blueprints do ecossistema Blockbench. As duas referências canônicas e mantidas ativamente para integração com IA são `sosadly/blockbench-mcp` (86 ferramentas, visão raio-X e feedback interativo) e `jasonjgardner/blockbench-mcp-plugin` (renderizador Headless WebGPU Dawn e concorrência multi-agente). Ambas constam como pilares indispensáveis do projeto.

---

## 3. Guia Passo a Passo de Instalação de Pré-requisitos do Sistema

### 3.1 Ambiente Local de Desenvolvimento (Windows 11)

Na máquina do desenvolvedor, execute os passos sequenciais no PowerShell como Administrador para configurar todas as ferramentas locais:

```powershell
# ==============================================================================
# SCRIPT DE INSTALAÇÃO DE PRÉ-REQUISITOS LOCAIS (WINDOWS 11)
# ==============================================================================

# 1. Habilitar execução de scripts caso ainda não esteja autorizado
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser -Force

# 2. Instalação do gerenciador de pacotes Winget (nativo no Windows 11) ou verificação
winget --version

# 3. Instalação do Node.js 20 LTS (Runtime indispensável para TypeScript e MCPs)
winget install OpenJS.NodeJS.LTS --silent --accept-package-agreements --accept-source-agreements

# 4. Instalação do Python 3.11+ e utilitário uv (gerenciador ultra-rápido de pacotes Python)
winget install Python.Python.3.11 --silent --accept-package-agreements --accept-source-agreements
powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"

# 5. Instalação do Git para Windows
winget install Git.Git --silent --accept-package-agreements --accept-source-agreements

# 6. Instalação do Blockbench (Editor 3D de modelos Minecraft)
winget install JannisX11.Blockbench --silent --accept-package-agreements --accept-source-agreements

# 7. Instalação da CLI do Regolith (Compilador oficial de Add-ons Bedrock)
# Baixa o instalador mais recente do repositório Bedrock-OSS
$regolithUrl = "https://github.com/Bedrock-OSS/regolith/releases/latest/download/regolith-windows-amd64.exe"
$installDir = "$env:LOCALAPPDATA\Programs\regolith"
New-Item -ItemType Directory -Force -Path $installDir
Invoke-WebRequest -Uri $regolithUrl -OutFile "$installDir\regolith.exe"
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$installDir", [EnvironmentVariableTarget]::User)

# 8. Instalação das bibliotecas globais de suporte para desenvolvimento Bedrock
npm install -g esbuild typescript ajv ajv-formats

# 9. Instalação das bibliotecas Python para automação PBR, MCPs de SSH e Nuvem OCI
python -m pip install --upgrade pip
pip install pillow numpy mcrcon oci mcp-ssh-session mcp-server-oci

# ==============================================================================
# 3.1.1 PREPARAÇÃO E COMPILAÇÃO DOS REPOSITÓRIOS LOCAIS EM tools/
# ==============================================================================
# Para viabilizar a execução imediata dos servidores MCP referenciados na Seção 4,
# os repositórios locais devem ser clonados e compilados na pasta 'tools/' da raiz:

$projectRoot = "A:\BackupDesktopFileBruno\Estudos\Projetos-pessoais\Minecraft Server"
$toolsDir = Join-Path $projectRoot "tools"
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

# 1. Compilação do sosadly/blockbench-mcp (Porta 8787 / Bridge Desktop)
if (-not (Test-Path "$toolsDir\blockbench-mcp")) {
    git clone https://github.com/sosadly/blockbench-mcp.git "$toolsDir\blockbench-mcp"
    Push-Location "$toolsDir\blockbench-mcp"
    npm install
    npm run build
    Pop-Location
}

# 2. Configuração do willibrandon/pixel-mcp (Aseprite CLI em Go)
if (-not (Test-Path "$toolsDir\pixel-mcp")) {
    git clone https://github.com/willibrandon/pixel-mcp.git "$toolsDir\pixel-mcp"
    Push-Location "$toolsDir\pixel-mcp"
    # Compilação do binário executável em Go
    go build -o "$toolsDir\pixel-mcp\bin\pixel-mcp.exe" .
    Pop-Location
}

# 3. Configuração do mhpm/aseprite-mcp-studio (Python / Live SSE 7790)
if (-not (Test-Path "$toolsDir\aseprite-mcp-studio")) {
    git clone https://github.com/mhpm/aseprite-mcp-studio.git "$toolsDir\aseprite-mcp-studio"
    Push-Location "$toolsDir\aseprite-mcp-studio"
    uv sync
    Pop-Location
}

# 4. Compilação do WebritroHQ/minecraft-structure-mcp (NBT e Render Isométrico)
if (-not (Test-Path "$toolsDir\minecraft-structure-mcp")) {
    git clone https://github.com/WebritroHQ/minecraft-structure-mcp.git "$toolsDir\minecraft-structure-mcp"
    Push-Location "$toolsDir\minecraft-structure-mcp"
    npm install
    npm run build
    Pop-Location
}

# 5. Compilação do Mming-Lab/minecraft-bedrock-education-mcp (Bridge WebSocket BDS)
if (-not (Test-Path "$toolsDir\minecraft-bedrock-mcp")) {
    git clone https://github.com/Mming-Lab/minecraft-bedrock-education-mcp.git "$toolsDir\minecraft-bedrock-mcp"
    Push-Location "$toolsDir\minecraft-bedrock-mcp"
    npm install
    npm run build
    Pop-Location
}
```

---

### 3.2 Ambiente Remoto de Produção (Oracle Linux 9 na OCI)

Conecte-se à instância via SSH (`ssh -i <chave> opc@<IP_PUBLICO>`) e execute as seguintes instruções administrativas para preparar o sistema operacional:

```bash
# ==============================================================================
# SCRIPT DE INSTALAÇÃO DE PRÉ-REQUISITOS REMOTOS (ORACLE LINUX 9 / OCI)
# ==============================================================================

# 1. Atualização completa do repositório de pacotes DNF
sudo dnf check-update || true
sudo dnf update -y

# 2. Instalação de utilitários essenciais de rede, monitoramento e compilação
sudo dnf install -y \
    curl wget tar gzip bzip2 unzip git jq \
    net-tools bind-utils iptables-services \
    gcc gcc-c++ make glibc libstdc++ \
    procps-ng findutils cronie

# 3. Configuração do arquivo de memória Swap de 5 GB (CRÍTICO para VM Micro de 1 GB de RAM)
# Evita que o BDS sofra encerramento forçado pelo OOM-Killer durante compilações ou saves
if [ ! -f /swapfile ]; then
    echo "[INFO] Alocando 5 GB de arquivo Swap..."
    sudo dd if=/dev/zero of=/swapfile bs=1M count=5120 status=progress
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    # Persistência no /etc/fstab para montagem automática no boot
    echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab
fi

# Ajuste fino da tendência do kernel em usar swap (swappiness moderado)
sudo sysctl vm.swappiness=30
echo "vm.swappiness=30" | sudo tee -a /etc/sysctl.d/99-minecraft-swap.conf

# 4. Instalação e Habilitação do Docker Engine e Docker Compose (Opcional para contêineres)
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker opc

# 5. Instalação da OCI CLI Oficial no Linux
bash -c "$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)" -- --auto

# 6. Estruturação dos diretórios de produção do Minecraft Bedrock
mkdir -p /home/opc/minecraft/server
mkdir -p /home/opc/minecraft/backups
mkdir -p /home/opc/minecraft/addons
sudo chown -R opc:opc /home/opc/minecraft

# 7. Habilitação mandatória do NetworkManager-wait-online para sincronização de rede no boot
# Garante que o target network-online.target bloqueie até a rede ter IP e rotas válidas
sudo systemctl enable NetworkManager-wait-online.service

# 8. Liberação de portas no firewall do host (UDP 19132/19133 e TCP 19132 NetherNet Signaling)
sudo firewall-cmd --zone=public --add-port=19132/udp --permanent
sudo firewall-cmd --zone=public --add-port=19133/udp --permanent
sudo firewall-cmd --zone=public --add-port=19132/tcp --permanent
sudo firewall-cmd --reload
```

---

## 4. Blocos de Configuração JSON dos Servidores MCP

Abaixo encontra-se o arquivo de configuração mestre pronto para ser copiado e colado na íntegra no cliente de Inteligência Artificial do usuário (seja no `claude_desktop_config.json`, no `mcp_config.json` do VS Code ou no orquestrador do Google Antigravity).

O bloco contém **todos os servidores indispensáveis e opcionais mapeados**, com seus comandos exatos, argumentos, variáveis de ambiente e caminhos:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-filesystem",
        "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server"
      ]
    },
    "github": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-github"
      ],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_SUBSTITUA_PELO_SEU_TOKEN_GITHUB_AQUI"
      }
    },
    "context7": {
      "command": "npx",
      "args": [
        "-y",
        "@upstash/context7-mcp@latest"
      ]
    },
    "fetch": {
      "command": "uvx",
      "args": [
        "mcp-server-fetch"
      ]
    },
    "blockbench_desktop": {
      "command": "node",
      "args": [
        "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/tools/blockbench-mcp/dist/index.js"
      ],
      "env": {
        "BLOCKBENCH_MCP_PORT": "8787",
        "BLOCKBENCH_MCP_HOST": "127.0.0.1"
      }
    },
    "blockbench_headless": {
      "command": "cmd",
      "args": [
        "/c",
        "npx",
        "-y",
        "github:jasonjgardner/blockbench-mcp-plugin",
        "--root",
        "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/assets/models"
      ]
    },
    "aseprite_cli": {
      "command": "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/tools/pixel-mcp/bin/pixel-mcp.exe",
      "env": {
        "ASEPRITE_PATH": "C:/Program Files/Aseprite/Aseprite.exe"
      }
    },
    "aseprite_studio": {
      "command": "uv",
      "args": [
        "--directory",
        "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/tools/aseprite-mcp-studio",
        "run",
        "aseprite-mcp-studio"
      ],
      "env": {
        "ASE_STUDIO_ROOTS": "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/assets/textures",
        "ASE_STUDIO_LIVE_PORT": "7790"
      }
    },
    "minecraft_structures": {
      "command": "node",
      "args": [
        "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/tools/minecraft-structure-mcp/dist/index.js"
      ],
      "env": {
        "MINECRAFT_ASSETS_PATH": "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/assets"
      }
    },
    "minecraft_bedrock_bridge": {
      "command": "node",
      "args": [
        "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/tools/minecraft-bedrock-mcp/dist/index.js"
      ],
      "env": {
        "WS_PORT": "8080",
        "DEBUG": "true"
      }
    },
    "minecraft_rcon": {
      "command": "npx",
      "args": [
        "-y",
        "rcon-mcp"
      ],
      "env": {
        "RCON_HOST": "127.0.0.1",
        "RCON_PORT": "25575",
        "RCON_PASSWORD": "SENHA_SEGURA_DEFINIDA_PELO_USUARIO"
      }
    },
    "ssh_remote_session": {
      "command": "python",
      "args": [
        "-m",
        "mcp_ssh_session"
      ],
      "env": {
        "SSH_CONFIG_PATH": "C:/Users/Bruno/.ssh/config",
        "DEFAULT_HOST": "minecraft-never-land",
        "DEFAULT_USER": "opc",
        "DEFAULT_KEY_PATH": "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/Server/VM/SSH keys/ssh-key-2026-11-03.key",
        "CONNECTION_TIMEOUT": "30",
        "AUTO_RECONNECT": "true"
      }
    },
    "ssh_direct_exec": {
      "command": "npx",
      "args": [
        "-y",
        "ssh-mcp"
      ],
      "env": {
        "SSH_HOST": "minecraft-never-land",
        "SSH_PORT": "22",
        "SSH_USERNAME": "opc",
        "SSH_KEY_PATH": "A:/BackupDesktopFileBruno/Estudos/Projetos-pessoais/Minecraft Server/Server/VM/SSH keys/ssh-key-2026-11-03.key",
        "SSH_TIMEOUT": "45000"
      }
    },
    "oci_cloud_manager": {
      "command": "python",
      "args": [
        "-m",
        "mcp_server_oci"
      ],
      "env": {
        "OCI_CONFIG_FILE": "C:/Users/Bruno/.oci/config",
        "OCI_CONFIG_PROFILE": "DEFAULT"
      }
    },
    "docker_container_manager": {
      "command": "npx",
      "args": [
        "-y",
        "@quantgeekdev/docker-mcp"
      ],
      "env": {
        "DOCKER_HOST": "npipe:////./pipe/docker_engine"
      }
    }
  }
}
```

---

## 5. Fluxos de Trabalho Práticos Ponta a Ponta para Criação Assistida por IA

---

### Fluxo 1: Criação e Edição de Pixel Art e Texturas 2D

#### O Desafio da IA Generativa
Modelos generativos de difusão criam grades borradas, cores intermediárias dispersas e linhas flutuantes que violam as regras estritas de pixel art. O pipeline combina o `willibrandon/pixel-mcp` (para controle fino via Lua e Aseprite oficial) e o `mhpm/aseprite-mcp-studio` (para auto-pixelização matemática).

#### Sequência Passo a Passo:
1. **Geração ou Ingestão do Conceito:** Uma imagem bruta gerada por IA é submetida ao `aseprite-mcp-studio`.
2. **Correção de Grade por FFT e Dynamic Programming (DP):**
   - O algoritmo calcula a autocorrelação bidimensional via Transformada Rápida de Fourier (FFT), detectando o tamanho exato da célula de pixel.
   - Um alinhamento por Programação Dinâmica corrige as oscilações de perspectiva.
3. **Quantização Perceptual no Espaço OKLab:**
   - A paleta é reduzida aglomerativamente no espaço de cor OKLab, garantindo que pequenos detalhes (olhos, fios de lâmina) não desapareçam.
4. **Remoção de Pixels Órfãos (Speck & Orphan Cleanup):**
   - Pixels isolados que criam ruído visual são absorvidos pelas cores vizinhas dominantes.
5. **Dithering Estruturado (Aseprite CLI via Lua):**
   - Aplica-se o padrão procedural específico (ex: `stone` para blocos minerais ou matriz de Bayer 4x4) via `pixel-mcp`.
6. **Exportação:** O PNG finalizado é gravado diretamente em `RP/textures/blocks/` ou `RP/textures/items/`.

---

### Fluxo 2: Modelagem 3D, Geometrias e Ossos (Blockbench MCP)

#### Sequência Passo a Passo:
1. **Criação do Projeto Bedrock:**
   - A IA invoca `new_project(format: "bedrock")` definindo o identificador (ex: `geometry.golem_obsidian`).
2. **Construção da Árvore de Rigging (Bípedes/Quadrúpedes):**
   - A IA chama `create_rig` ou `add_groups` respeitando a regra mandatória de **3 ossos por membro** (ombro/braço/antebraço e quadril/coxa/canela), garantindo que cotovelos e joelhos flexionem com anatomia correta.
   - Pivôs (`origin`) são posicionados no centro articular exato.
3. **Blocagem e Detalhamento Procedural:**
   - `add_cubes`: Criação em lote dos volumes essenciais.
   - `add_hollow_volume`: Gera armaduras ocas (capacetes e ombreiras).
   - `generate_array`: Repete rebites e espinhos com variação de escala e anti-z-fighting stagger.
4. **Mapeamento UV e Sombreamento Automático:**
   - `pack_uv`: Organiza as coordenadas UV de forma não sobreposta.
   - `detail_cubes`: Aplica luz direcional com sombreamento hue-shifted (luz quente / sombra fria) e oclusão de ambiente (AO) nas quinas.
5. **Auditoria Visual e Raio-X:**
   - `check_model`: Varre a geometria procurando conflitos de profundidade (*z-fighting*) e partes flutuantes.
   - `inspect_model`: Realiza capturas em ângulo raio-X destacando o ponto exato da falha para auto-correção imediata.
   - `request_review`: Apresenta folha de contato no painel do Blockbench solicitando aprovação do desenvolvedor humano.
6. **Exportação:** Exporta o arquivo `.geo.json` e salva o `.bbmodel` no repositório.

---

### Fluxo 3: Pipeline de Texturas PBR e Ray Tracing Render Dragon

#### Regras do Render Dragon:
1. O Bedrock unifica as propriedades PBR em uma única imagem RGB denominada **MER**:
   - **Canal R (Red):** Metalness (0 = não-metal / dielétrico; 255 = metal puro).
   - **Canal G (Green):** Emissive (0 = escuro; >0 = emissão de luz própria no escuro).
   - **Canal B (Blue):** Roughness (0 = polido/espelho; 255 = fosco/áspero).
2. O mapa normal opera em **espaço tangente no padrão DirectX (Y-down)**. Mapas no padrão OpenGL precisam **obrigatoriamente inverter o canal Verde ($G = 255 - G$)**, caso contrário o relevo parecerá escavado ao contrário.
3. As chaves `"normal"` e `"heightmap"` são **mutuamente exclusivas** no manifesto `.texture_set.json`.

#### Script Utilitário Python de Automação PBR (`tools/pbr_generator.py`)

Abaixo está o código-fonte completo e comentado do script utilitário de conversão PBR para inclusão no repositório do projeto:

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script Utilitário: Gerador e Empacotador Automático de Texturas PBR para Minecraft Bedrock
Compatível com: Render Dragon / RTX / Deferred Lighting (Vibrant Visuals)

Funções principais:
1. Empacota canais isolados (Metallic, Emissive, Roughness) em imagem unificada MER (RGB).
2. Converte mapas normais OpenGL para DirectX (inversão matemática do canal Verde).
3. Gera mapas de normais em espaço tangente a partir de um mapa de altura/albedo via Sobel.
4. Escreve automaticamente os manifestos .texture_set.json correspondentes.
"""

import os
import json
import sys
from pathlib import Path
from PIL import Image, ImageOps
import numpy as np


def pack_mer_image(metallic_path: str, emissive_path: str, roughness_path: str, output_path: str) -> None:
    """
    Empacota imagens em escala de cinza nos canais R, G e B de uma única imagem MER.
    Canal R = Metalicidade (Metallic)
    Canal G = Emissividade (Emissive)
    Canal B = Rugosidade (Roughness)
    """
    # 1. Canal Vermelho (Metalicidade)
    if metallic_path and os.path.exists(metallic_path):
        r_chan = Image.open(metallic_path).convert("L")
    else:
        # Padrão: 0 (material não metálico / dielétrico como pedra ou tecido)
        r_chan = Image.new("L", (16, 16), 0)

    # 2. Canal Verde (Emissividade / Glow)
    if emissive_path and os.path.exists(emissive_path):
        g_chan = Image.open(emissive_path).convert("L")
    else:
        # Padrão: 0 (sem emissão de luz própria)
        g_chan = Image.new("L", r_chan.size, 0)

    # 3. Canal Azul (Rugosidade)
    if roughness_path and os.path.exists(roughness_path):
        b_chan = Image.open(roughness_path).convert("L")
    else:
        # Padrão: 204 (~80% de rugosidade / acabamento levemente fosco padrão)
        b_chan = Image.new("L", r_chan.size, 204)

    # Harmoniza a resolução das três imagens para a dimensão do canal R
    target_size = r_chan.size
    if g_chan.size != target_size:
        g_chan = g_chan.resize(target_size, Image.Resampling.NEAREST)
    if b_chan.size != target_size:
        b_chan = b_chan.resize(target_size, Image.Resampling.NEAREST)

    # Funde os três canais em uma única imagem RGB
    mer_image = Image.merge("RGB", (r_chan, g_chan, b_chan))
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    mer_image.save(output_path, "PNG")
    print(f"✅ [PBR] Mapa MER salvo com sucesso em: {output_path}")


def convert_opengl_to_directx_normal(normal_path: str, output_path: str) -> None:
    """
    Converte um mapa normal OpenGL (Y-up) para o padrão DirectX (Y-down) exigido pelo Bedrock.
    Inverte cirurgicamente o canal Verde (G = 255 - G).
    """
    img = Image.open(normal_path).convert("RGB")
    r, g, b = img.split()
    # Inverte o canal Verde (eixo Y da normal)
    g_inverted = ImageOps.invert(g)
    dx_normal = Image.merge("RGB", (r, g_inverted, b))
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    dx_normal.save(output_path, "PNG")
    print(f"✅ [PBR] Normal DirectX convertida e salva em: {output_path}")


def generate_normal_from_albedo(albedo_path: str, output_path: str, strength: float = 2.0) -> None:
    """
    Gera um mapa normal Tangent Space DirectX a partir de uma imagem de Albedo/Altura.
    Utiliza operadores de gradiente de Sobel com convolução periódica para tiling contínuo.
    """
    albedo = Image.open(albedo_path).convert("L")
    arr = np.array(albedo, dtype=np.float32) / 255.0

    # Aplica convoluções para extrair os gradientes de superfície
    gx = np.zeros_like(arr)
    gy = np.zeros_like(arr)

    # Diferença central com rolagem circular periódica para ladrilhamento perfeito (tiling)
    gx[:, :] = (np.roll(arr, -1, axis=1) - np.roll(arr, 1, axis=1)) * 0.5 * strength
    gy[:, :] = (np.roll(arr, -1, axis=0) - np.roll(arr, 1, axis=0)) * 0.5 * strength

    # No padrão DirectX, o vetor Y aponta para baixo (inversão de sinal do gradiente vertical)
    dx = gx
    dy = -gy
    dz = np.ones_like(arr)

    # Normalização vetorial unitária euclidiana
    length = np.sqrt(dx**2 + dy**2 + dz**2)
    nx = (dx / length) * 0.5 + 0.5
    ny = (dy / length) * 0.5 + 0.5
    nz = (dz / length) * 0.5 + 0.5

    # Converte de volta para inteiros de 8 bits (0 a 255)
    r = (nx * 255.0).clip(0, 255).astype(np.uint8)
    g = (ny * 255.0).clip(0, 255).astype(np.uint8)
    b = (nz * 255.0).clip(0, 255).astype(np.uint8)

    normal_img = Image.merge("RGB", (Image.fromarray(r), Image.fromarray(g), Image.fromarray(b)))
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    normal_img.save(output_path, "PNG")
    print(f"✅ [PBR] Normal DirectX calculada via Sobel em: {output_path}")


def create_texture_set_json(texture_name: str, directory: str, has_normal: bool = True, has_mer: bool = True) -> str:
    """
    Gera o arquivo de manifesto <texture_name>.texture_set.json em estrita conformidade com a Mojang.
    """
    texture_set = {
        "format_version": "1.21.30",
        "minecraft:texture_set": {
            "color": texture_name
        }
    }

    if has_normal:
        texture_set["minecraft:texture_set"]["normal"] = f"{texture_name}_normal"
    if has_mer:
        texture_set["minecraft:texture_set"]["metalness_emissive_roughness"] = f"{texture_name}_mer"

    json_path = os.path.join(directory, f"{texture_name}.texture_set.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(texture_set, f, indent=2)

    print(f"✅ [PBR] Manifesto Texture Set gerado em: {json_path}")
    return json_path


if __name__ == "__main__":
    print("Script Utilitário PBR Bedrock carregado com sucesso.")
```

#### Manifestos de Atlas de Texturas e Texture Sets no Render Dragon

No motor Render Dragon, os mapas PBR gerados (`.texture_set.json`, normais e MER) exigem registro formal nos catálogos de atlas do Resource Pack. Sem esse mapeamento, o Bedrock não consegue indexar as texturas e exibe o padrão de textura ausente (xadrez preto e roxo):

##### 1. Manifesto de Atlas de Terreno / Blocos (`RP/textures/terrain_texture.json`)
```json
{
  "resource_pack_name": "neverland_rp",
  "texture_name": "atlas.terrain",
  "padding": 8,
  "num_mip_levels": 4,
  "texture_data": {
    "neverland:obsidian_golem_block": {
      "textures": "textures/blocks/obsidian_golem_block"
    },
    "neverland:runic_tile": {
      "textures": {
        "variations": [
          { "path": "textures/blocks/runic_tile_0", "weight": 60 },
          { "path": "textures/blocks/runic_tile_1", "weight": 40 }
        ]
      }
    }
  }
}
```
> **Mecanismo de Resolução do Render Dragon:** Ao apontar `"textures": "textures/blocks/obsidian_golem_block"`, o motor gráfico verifica primeiramente se existe um arquivo `obsidian_golem_block.texture_set.json` no mesmo diretório. Se existir, ele carrega todos os canais PBR (Albedo, Normal e MER). Caso contrário, carrega apenas o `obsidian_golem_block.png` padrão.

##### 2. Manifesto de Atlas de Itens 2D (`RP/textures/item_texture.json`)
```json
{
  "resource_pack_name": "neverland_rp",
  "texture_name": "atlas.items",
  "texture_data": {
    "neverland:obsidian_blade": {
      "textures": "textures/items/obsidian_blade"
    },
    "neverland:arcane_crystal": {
      "textures": "textures/items/arcane_crystal"
    }
  }
}
```

##### 3. Manifesto Canônico de Material PBR (`RP/textures/blocks/obsidian_golem_block.texture_set.json`)
```json
{
  "format_version": "1.21.30",
  "minecraft:texture_set": {
    // Textura base de cor / albedo (RGBA)
    "color": "obsidian_golem_block",
    // Mapa de normais em espaço tangente no padrão DirectX (Y-down)
    "normal": "obsidian_golem_block_normal",
    // Camada unificada MER: R = Metalness, G = Emissive, B = Roughness
    "metalness_emissive_roughness": "obsidian_golem_block_mer"
  }
}
```

*Exemplo alternativo com Mapa de Altura (Heightmap) e valores numéricos uniformes de MER:*
```json
{
  "format_version": "1.21.30",
  "minecraft:texture_set": {
    "color": "carved_rune_stone",
    // Nota: 'heightmap' e 'normal' são mutuamente exclusivos!
    "heightmap": "carved_rune_stone_height",
    // Valores vetoriais: [Metalness=0.0 (dielétrico), Emissive=0.5 (brilho médio), Roughness=0.7 (semi-fosco)]
    "metalness_emissive_roughness": [0.0, 0.5, 0.7]
  }
}
```

---

### Fluxo 4: Efeitos Visuais, Partículas Snowstorm e Molang

#### 4.1 Anatomia e Modelo Canônico de Partícula Bedrock (`.particle.json`)
O motor de partículas do Minecraft Bedrock opera sob a especificação estável `format_version: "1.10.0"`. Ele organiza o comportamento em renderização básica, taxa de emissão, delimitador de tempo de vida, geometria do emissor, física de vetores dinâmicos, colisão e curvas de cor.

Abaixo encontra-se o exemplo canônico completo de uma rajada mágica arcana (`RP/particles/arcane_magic_burst.particle.json`), altamente comentado:

```json
{
  "format_version": "1.10.0",
  "particle_effect": {
    "description": {
      // Identificador exclusivo de namespace para invocação por script, animação ou comando
      "identifier": "custom:arcane_magic_burst",
      "basic_render_parameters": {
        // Material de mesclagem aditiva com canal alfa transparente
        "material": "particles_blend",
        // Caminho da textura no Resource Pack
        "texture": "textures/particle/particles"
      }
    },
    "components": {
      // Emissão instantânea: rajada de 45 partículas de uma só vez (impacto de feitiço)
      "minecraft:emitter_rate_instant": {
        "num_particles": 45
      },
      // Emissor de disparo único com tempo de vida de 0.1 segundo
      "minecraft:emitter_lifetime_once": {
        "active_time": 0.1
      },
      // Geometria esférica: partículas nascem na casca de uma esfera de raio 0.35 blocos
      "minecraft:emitter_shape_sphere": {
        "radius": 0.35,
        "surface_only": true,
        "direction": "outwards"
      },
      // Vida útil de cada partícula individual: variação estocástica entre 0.8 e 1.4 segundos
      "minecraft:particle_lifetime_expression": {
        "max_lifetime": "math.random(0.8, 1.4)"
      },
      // Velocidade inicial de ejeção radial
      "minecraft:particle_initial_speed": "math.random(3.0, 6.5)",
      // Dinâmica de movimento e física do ambiente
      "minecraft:particle_motion_dynamic": {
        // Aceleração linear: gravidade sutil puxando as partículas para baixo (eixo Y negativo)
        "linear_acceleration": [ 0.0, -2.5, 0.0 ],
        // Arrasto aerodinâmico linear de 1.8: desacelera a explosão progressivamente
        "linear_drag_coefficient": 1.8
      },
      // Detecção de colisão com o relevo sólido do mundo
      "minecraft:particle_motion_collision": {
        "collision_radius": 0.08,
        "collision_drag_coefficient": 2.0,
        // Desaparece instantaneamente ao colidir com blocos sólidos
        "expire_on_contact": true
      },
      // Rotação inicial aleatória
      "minecraft:particle_initial_spin": {
        "rotation": "math.random(0.0, 360.0)",
        "rotation_rate": "math.random(-90.0, 90.0)"
      },
      // Aparência e renderização de billboard
      "minecraft:particle_appearance_billboard": {
        // Encolhe suavemente conforme a partícula envelhece
        "size": [
          "0.18 * (1.0 - (context.particle_age / context.particle_lifetime))",
          "0.18 * (1.0 - (context.particle_age / context.particle_lifetime))"
        ],
        // Sempre orientada para a câmera do observador em todos os eixos
        "facing_camera_mode": "rotate_xyz",
        "uv": {
          "texture_width": 128,
          "texture_height": 128,
          "uv": [ 0, 0 ],
          "uv_size": [ 8, 8 ]
        }
      },
      // Tintura de cor dinâmica em Molang: transição suave de Cyan -> Magenta -> Alfa zero
      "minecraft:particle_appearance_tinting": {
        "color": [
          // Canal R (Vermelho): interpolação linear aumentando de 0.1 para 0.9
          "math.lerp(0.1, 0.9, context.particle_age / context.particle_lifetime)",
          // Canal G (Verde): decai de forma rápida cúbica
          "math.lerp(0.8, 0.05, math.pow(context.particle_age / context.particle_lifetime, 2))",
          // Canal B (Azul): intensidade azulada mantida
          "0.95",
          // Canal Alfa: opaco no início e decai gradativamente para transparência total
          "1.0 - (context.particle_age / context.particle_lifetime)"
        ]
      }
    }
  }
}
```

#### 4.2 Modelo Canônico de Animação Bedrock (`.animation.json`)
As animações operam na especificação `format_version: "1.8.0"`. Abaixo está o exemplo canônico de animação de conjuração mágica (`RP/animations/sorcerer.animation.json`), demonstrando interpolação Catmull-Rom, fórmulas contínuas em Molang e sincronização de eventos em `timeline`:

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.sorcerer.cast_spell": {
      // Duração total da linha do tempo da animação: 1.5 segundos
      "animation_length": 1.5,
      // Execução única: não entra em repetição cíclica
      "loop": false,
      // Suprime animações secundárias de menor prioridade durante a conjuração
      "override_previous_animation": true,
      "bones": {
        // Osso da cabeça: acompanha dinamicamente o olhar vertical e horizontal do alvo
        "head": {
          "rotation": [
            "query.target_x_rotation",
            "query.target_y_rotation",
            0.0
          ]
        },
        // Osso do braço direito: elevação para disparo com interpolação cúbica catmullrom
        "right_arm": {
          "rotation": {
            "0.0": [ 0.0, 0.0, 0.0 ],
            "0.4": {
              "pre": [ -60.0, 15.0, -10.0 ],
              "post": [ -60.0, 15.0, -10.0 ],
              "lerp_mode": "catmullrom"
            },
            "0.8": {
              // Vibração procedural na ponta do braço enquanto canaliza a energia mágica
              "pre": [
                "-110.0 + (math.sin(query.anim_time * 1800) * 4.0)",
                "20.0 + (math.cos(query.anim_time * 1800) * 3.0)",
                "-15.0"
              ],
              "post": [
                "-110.0 + (math.sin(query.anim_time * 1800) * 4.0)",
                "20.0 + (math.cos(query.anim_time * 1800) * 3.0)",
                "-15.0"
              ],
              "lerp_mode": "catmullrom"
            },
            "1.2": {
              "pre": [ -130.0, 5.0, -5.0 ],
              "post": [ -130.0, 5.0, -5.0 ],
              "lerp_mode": "catmullrom"
            },
            "1.5": [ 0.0, 0.0, 0.0 ]
          }
        },
        // Osso do tronco (body): recuo físico de impacto durante o disparo
        "body": {
          "position": {
            "0.0": [ 0.0, 0.0, 0.0 ],
            "0.8": [ 0.0, 0.2, -0.15 ],
            "1.2": [ 0.0, -0.1, 0.4 ],
            "1.5": [ 0.0, 0.0, 0.0 ]
          },
          "rotation": {
            "0.0": [ 0.0, 0.0, 0.0 ],
            "0.8": [ -5.0, 0.0, 0.0 ],
            "1.2": [ 8.0, 0.0, 0.0 ],
            "1.5": [ 0.0, 0.0, 0.0 ]
          }
        }
      },
      // Linha do tempo: sincronização exata de áudio 3D e partículas nos instantes certos
      "timeline": {
        "0.2": {
          "sound_effects": [
            { "effect": "spell.charge" }
          ]
        },
        "0.8": {
          "particle_effects": [
            {
              // Dispara a partícula mágica no locator da mão direita do modelo 3D
              "effect": "custom:arcane_magic_burst",
              "locator": "right_hand_locator"
            }
          ],
          "sound_effects": [
            { "effect": "spell.blast" }
          ]
        }
      }
    }
  }
}
```

#### 4.3 Tabela de Domínio da Linguagem Molang (MOJANG LANGuage)

Molang é a linguagem de avaliação de expressões de alta performance nativa em C++ da Mojang, executada frame a frame pelo Bedrock Engine sem a sobrecarga de um runtime interpretado:

##### 1. Namespaces e Escopos de Variáveis
| Namespace | Descrição e Ciclo de Vida | Exemplo Canônico de Aplicação |
| :--- | :--- | :--- |
| `query.*` | Telemetria somente-leitura do estado da entidade e do ambiente de jogo. | `query.ground_speed`, `query.anim_time`, `query.is_sneaking` |
| `variable.*` | Variáveis persistentes vinculadas à instância da entidade (mantidas entre frames). | `variable.spell_charge = 1.0;` |
| `temp.*` | Variáveis voláteis de curta duração, descartadas após o encerramento do bloco. | `temp.spd = query.ground_speed * 1.5; return temp.spd;` |
| `context.*` | Variáveis injetadas dinamicamente pelo renderizador em partículas e interfaces. | `context.particle_age`, `context.particle_lifetime` |
| `math.*` | Biblioteca completa de funções matemáticas, trigonométricas e estocásticas. | `math.sin()`, `math.clamp()`, `math.lerp()`, `math.random()` |

##### 2. Operadores e Estruturas de Expressão
- **Aritméticos:** Soma (`+`), Subtração (`-`), Multiplicação (`*`), Divisão (`/`).
- **Relacionais:** Igualdade (`==`), Diferença (`!=`), Menor (`<`), Menor/Igual (`<=`), Maior (`>`), Maior/Igual (`>=`).
- **Lógicos:** Conjunção (`&&`), Disjunção (`||`), Negação (`!`).
- **Ternário Condicional:** `condicao ? valor_se_verdadeiro : valor_se_falso`.
- **Coalescência Nula (`??`):** Retorna o primeiro operando se válido, ou o fallback (`variable.speed ?? 1.0`).
- **Separador e Retorno:** Ponto e vírgula (`;`) para encadear comandos imperativos, e `return` para entregar o valor numérico.

##### 3. Funções Matemáticas Nativas (`math.*`)
| Função | Argumentos | Comportamento Técnico |
| :--- | :--- | :--- |
| `math.sin(x)` | Ângulo em graus | Seno trigonométrico. |
| `math.cos(x)` | Ângulo em graus | Cosseno trigonométrico. |
| `math.abs(x)` | Valor numérico | Módulo absoluto do número. |
| `math.clamp(val, min, max)` | Valor, piso, teto | Restringe o valor estritamente ao intervalo `[min, max]`. |
| `math.lerp(a, b, t)` | Início, fim, interpolação (0 a 1) | Interpolação linear contínua. |
| `math.lerprotate(a, b, t)` | Ângulos em graus, fator | Interpolação angular que previne descontinuidades em 360°. |
| `math.pow(base, exp)` | Base e expoente | Potenciação. |
| `math.sqrt(x)` | Valor positivo | Raiz quadrada. |
| `math.random(min, max)` | Limites numéricos | Valor decimal pseudo-aleatório no intervalo. |
| `math.die_roll(n, faces)` | Quantidade e lados | Simula a rolagem de $N$ dados de $F$ faces. |
| `math.pi` | Constante | O valor matemático de Pi (~3.14159265). |

##### 4. Padrões de Expressões Molang para Geração por IA
- **Flutuação Mágica Orgânica:**
  `math.sin(query.anim_time * 120.0) * 0.15 + (math.cos(query.anim_time * 60.0) * 0.05)`
- **Balanço de Membros Proporcional à Velocidade:**
  `math.sin(query.anim_time * (180.0 + (query.ground_speed * 120.0))) * (25.0 + math.clamp(query.ground_speed * 15.0, 0.0, 45.0))`
- **Respiração Torácica Procedural com Atenuação ao Agachar:**
  `temp.rate = query.is_sneaking ? 45.0 : 90.0; temp.int = query.is_sneaking ? 0.02 : 0.06; return 1.0 + (math.sin(query.anim_time * temp.rate) * temp.int);`

---

### Fluxo 5: Desenvolvimento de Scripts em TypeScript (`@minecraft/server`)

#### 5.1 Runtime de Scripting e Arquitetura do Toolchain
O Minecraft Bedrock Edition executa código JavaScript em módulos ECMAScript (ESM) utilizando um motor embutido (QuickJS / V8 customizado) sincronizado com a thread de simulação principal do servidor (20 ticks por segundo, 50ms por tick). Loops síncronos longos ou bloqueios travam a física do servidor e causam encerramento automático por Watchdog Timeout.

Para manter um padrão corporativo à prova de falhas com suporte total a autocompletion e checagem de tipos estáticos, o desenvolvimento é conduzido em **TypeScript** com empacotamento em milissegundos via **esbuild**.

#### 5.2 Configuração do Projeto (`package.json`)
```json
{
  "name": "neverland-script-addon",
  "version": "1.0.0",
  "description": "Addon moderno para Minecraft Bedrock desenvolvido em TypeScript com esbuild",
  "main": "behavior_pack/scripts/main.js",
  "scripts": {
    "build": "node esbuild.config.js",
    "watch": "node esbuild.config.js --watch",
    "type-check": "tsc --noEmit"
  },
  "devDependencies": {
    "@types/node": "^20.11.0",
    "esbuild": "^0.20.2",
    "typescript": "^5.4.3"
  },
  "dependencies": {
    "@minecraft/server": "^2.11.0",
    "@minecraft/server-ui": "^2.3.0",
    "@minecraft/server-admin": "^1.0.0-beta",
    "@minecraft/server-net": "^1.0.0-beta"
  }
}
```

#### 5.3 Configuração Estrita do Compilador TypeScript (`tsconfig.json`)
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "node",
    "lib": ["ES2022"],
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true,
    "strictFunctionTypes": true,
    "strictPropertyInitialization": true,
    "noImplicitThis": true,
    "alwaysStrict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noImplicitReturns": true,
    "noFallthroughCasesInSwitch": true,
    "moduleDetection": "force",
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "behavior_pack/scripts"]
}
```

#### 5.4 Empacotamento Automatizado com Módulos Externos (`esbuild.config.js`)
```javascript
/**
 * esbuild.config.js — Bundler de Scripts Bedrock
 * Transpila TypeScript em JavaScript ESM puro preservando os módulos nativos do Bedrock como externos.
 */
const esbuild = require('esbuild');
const path = require('path');

const isWatch = process.argv.includes('--watch');

/** @type {esbuild.BuildOptions} */
const buildOptions = {
  // Ponto de entrada do código-fonte em TypeScript
  entryPoints: [path.join(__dirname, 'src', 'main.ts')],
  bundle: true,
  outfile: path.join(__dirname, 'behavior_pack', 'scripts', 'main.js'),
  // CRUCIAL: Módulos providos nativamente pela engine do Minecraft Bedrock.
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
      console.log('👀 [esbuild] Modo watch ativo em src/ para recompilação contínua...');
    } else {
      await esbuild.build(buildOptions);
      console.log('✅ [esbuild] Compilação do Script API concluída com sucesso!');
    }
  } catch (error) {
    console.error('❌ [esbuild] Falha fatal na compilação:', error);
    process.exit(1);
  }
}

run();
```

#### 5.5 Script Exemplo Canônico Completo em TypeScript (`src/main.ts`)
```typescript
/**
 * Arquivo: src/main.ts
 * Descrição: Exemplo canônico completo de manipulação de eventos (before/after),
 *            agendamento seguro via system.run e interfaces gráficas dinâmicas (server-ui).
 * Compatibilidade: Minecraft Bedrock 1.21+ / @minecraft/server ^2.11.0 / @minecraft/server-ui ^2.3.0
 */

import {
  world,
  system,
  Player,
  Vector3,
  ChatSendBeforeEvent,
  ItemUseAfterEvent
} from '@minecraft/server';

import {
  ActionFormData,
  ModalFormData,
  MessageFormData,
  FormCancelationReason
} from '@minecraft/server-ui';

// ============================================================================
// 1. INICIALIZAÇÃO DO MUNDO E BOAS-VINDAS
// ============================================================================

world.afterEvents.worldInitialize.subscribe(() => {
  console.warn('🚀 [Never Land BDS] Script API inicializada com sucesso no servidor dedicado!');
});

world.afterEvents.playerSpawn.subscribe((event) => {
  const { player, initialSpawn } = event;
  if (initialSpawn) {
    player.sendMessage(`§aBem-vindo ao servidor Never Land, §e${player.name}§a!`);
    player.sendMessage('§7Digite §b!menu §7no chat ou utilize uma bússola para abrir o painel operacional.');
  }
});

// ============================================================================
// 2. INTERCEPTAÇÃO SÍNCRONA COM BEFOREEVENTS (COMANDOS CUSTOMIZADOS)
// ============================================================================

world.beforeEvents.chatSend.subscribe((event: ChatSendBeforeEvent) => {
  const message = event.message.trim();
  const player = event.sender;

  if (message === '!menu') {
    // Cancela o envio público no chat para manter o histórico limpo
    event.cancel = true;

    // REGRA DE OURO ARQUITETURAL:
    // Nunca chame form.show() diretamente na mesma pilha de execução de um beforeEvent!
    // A engine do Minecraft proíbe aberturas de UI em eventos canceláveis.
    // O agendamento via system.run adia a execução para o tick imediatamente subsequente:
    system.run(() => {
      abrirMenuPrincipal(player);
    });
  }
});

// ============================================================================
// 3. ATIVAÇÃO DE MENU POR ITEM COM AFTEREVENTS
// ============================================================================

world.afterEvents.itemUse.subscribe((event: ItemUseAfterEvent) => {
  const player = event.source;
  const item = event.itemStack;

  // Se o jogador acionar o uso empunhando uma bússola
  if (item.typeId === 'minecraft:compass') {
    abrirMenuPrincipal(player);
  }
});

// ============================================================================
// 4. INTERFACE GRÁFICA DE BOTÕES E AÇÕES (ActionFormData)
// ============================================================================

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
    // Tratamento formal de cancelamento pelo usuário (ex: fechou menu ou estava em combate)
    if (response.canceled) {
      if (response.cancelationReason === FormCancelationReason.UserBusy) {
        player.sendMessage('§cVocê está com outra interface aberta ou em combate. Tente novamente.');
      }
      return;
    }

    switch (response.selection) {
      case 0: {
        const spawnPos: Vector3 = { x: 0, y: 64, z: 0 };
        player.teleport(spawnPos, { dimension: player.dimension });
        player.sendMessage('§aTeletransportado com sucesso para o Spawn!');
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
    console.error('Erro ao renderizar ActionFormData:', err);
  });
}

// ============================================================================
// 5. INTERFACE GRÁFICA DE FORMULÁRIO (ModalFormData)
// ============================================================================

/**
 * Renderiza um formulário com toggles, sliders, campos de texto e dropdowns.
 * @param player Jogador que preencherá o formulário
 */
function abrirModalConfiguracoes(player: Player): void {
  const modal = new ModalFormData()
    .title('§lAjustes e Preferências')
    .toggle('§eAtivar Modo Noturno Visual', false)
    .slider('§bRaio de Partículas (blocos)', 5, 50, 5, 20)
    .textField('§aStatus Pessoal no Servidor', 'Ex: Explorando as montanhas...', '')
    .dropdown('§6Canal de Chat Padrão', ['Global', 'Vizinhança', 'Mineração'], 0);

  modal.show(player).then((response) => {
    if (response.canceled || !response.formValues) return;

    const [noturno, distParticulas, statusTexto, canalIndex] = response.formValues;
    player.sendMessage('§aConfigurações salvas:');
    player.sendMessage(`§7- Noturno: §f${noturno}`);
    player.sendMessage(`§7- Raio Partículas: §f${distParticulas}`);
    player.sendMessage(`§7- Status: §f${statusTexto}`);
    player.sendMessage(`§7- Canal ID: §f${canalIndex}`);
  }).catch((err) => {
    console.error('Erro ao renderizar ModalFormData:', err);
  });
}

// ============================================================================
// 6. CAIXA DE CONFIRMAÇÃO DIALÓGICA (MessageFormData)
// ============================================================================

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
  }).catch((err) => {
    console.error('Erro ao renderizar MessageFormData:', err);
  });
}

// ============================================================================
// 7. MONITORAMENTO EM SEGUNDO PLANO VIA SYSTEM.RUNINTERVAL
// ============================================================================

// Monitoramento leve de atividade a cada 200 ticks (10 segundos)
system.runInterval(() => {
  const onlinePlayers = world.getAllPlayers();
  if (onlinePlayers.length === 0) return;
  // Telemetria leve em background
}, 200);
```

#### 5.6 Permissões Estritas no Bedrock Dedicated Server (`permissions.json`)
Quando módulos com permissões restritas (como `@minecraft/server-net` ou `@minecraft/server-admin`) são utilizados, o BDS exige declaração explícita no arquivo `config/<UUID_DO_MODULO_SCRIPT>/permissions.json`:

```json
{
  "allowed_modules": [
    "@minecraft/server",
    "@minecraft/server-ui",
    "@minecraft/server-admin",
    "@minecraft/server-net"
  ]
}
```
*(O UUID do diretório corresponde ao campo `uuid` declarado no módulo de script dentro do `behavior_pack/manifest.json`).*

---

### Fluxo 6: Build, Otimização e Empacotamento com Regolith

#### 6.1 Arquitetura de Pipeline Não-Destrutivo
O compilador oficial da comunidade **Regolith** (`Bedrock-OSS/regolith`) opera lendo as pastas de código-fonte puro (`BP/` para Behavior Pack, `RP/` para Resource Pack e `data/` para ativos brutos), criando um ambiente efêmero em `.regolith/tmp`, aplicando filtros em cascata e exportando o resultado final sem contaminar os arquivos de desenvolvimento:

```
[ Código-Fonte Puro ] (BP, RP, scripts TS, imagens brutas)
        │
        ▼  regolith run development / production
[ Ambiente Efêmero ] (.regolith/tmp)
        │
        ├──► Filtro 1: rolldown / esbuild (Empacota TypeScript em BP/scripts/main.js)
        ├──► Filtro 2: texture_list (Gera texture_list.json e item_texture.json)
        ├──► Filtro 3: name_ninja (Unifica chaves de tradução em pt_BR.lang)
        ├──► Filtro 4: optipng (Compressão sem perdas de texturas PNG)
        ├──► Filtro 5: json_cleaner (Remove comentários e minifica JSONs)
        └──► Filtro 6: mcaddon (Empacota em arquivo único de release)
        │
        ▼
[ Destino Final ] (development_behavior_packs / development_resource_packs OU builds/*.mcaddon)
```

#### 6.2 Configuração Canônica do Compilador (`config.json`)
O arquivo `config.json` reside na raiz do projeto e estabelece a esteira de filtros para cada perfil de build:

```json
{
  "format_version": "1.2.0",
  "name": "NeverLandAddon",
  "author": "Never Land Engineering Team",
  "packs": {
    "behaviorPack": "BP",
    "resourcePack": "RP",
    "dataPath": "data"
  },
  "profiles": {
    // ------------------------------------------------------------------------
    // Perfil de Desenvolvimento Local: Envia direto para pastas de teste do BDS/Minecraft
    // ------------------------------------------------------------------------
    "development": {
      "export": {
        "target": "development",
        "readOnly": false
      },
      "filters": [
        {
          // Transpilação rápida de TypeScript sem minificação e com sourcemaps
          "filter": "rolldown",
          "settings": {
            "entryPoints": [ "BP/scripts/main.ts" ],
            "sourceMap": true
          }
        },
        {
          // Varredura automática das texturas do Resource Pack
          "filter": "texture_list"
        },
        {
          // Unificação dos arquivos de idioma (.lang)
          "filter": "name_ninja",
          "settings": {
            "defaultLanguage": "pt_BR.lang"
          }
        }
      ]
    },
    // ------------------------------------------------------------------------
    // Perfil de Produção / Release: Otimização extrema, minificação e pacote .mcaddon
    // ------------------------------------------------------------------------
    "production": {
      "export": {
        "target": "exact",
        "build": "builds/production"
      },
      "filters": [
        {
          // Incrementa automaticamente a versão semântica (patch) no manifest.json
          "filter": "bump_manifest",
          "settings": {
            "type": "patch"
          }
        },
        {
          // Transpila e minifica o bundle JavaScript final
          "filter": "rolldown",
          "settings": {
            "entryPoints": [ "BP/scripts/main.ts" ],
            "minify": true,
            "sourceMap": false
          }
        },
        {
          // Compressão de imagens PNG sem perdas
          "filter": "optipng",
          "settings": {
            "optimizationLevel": 5
          }
        },
        {
          // Remove comentários e compacta todos os arquivos JSON
          "filter": "json_cleaner"
        },
        {
          // Compacta BP e RP em arquivo único .mcaddon para distribuição pública
          "filter": "mcaddon",
          "settings": {
            "outputName": "NeverLand_Addon_v1.0.0"
          }
        }
      ]
    }
  }
}
```

#### 6.3 Esteira de CI/CD para GitHub Actions (`.github/workflows/regolith.yml`)
Workflow que automatiza a checagem de tipos estáticos, compilação de release e geração do artefato `.mcaddon` a cada commit na branch principal:

```yaml
name: Regolith Addon CI/CD Pipeline

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]

jobs:
  build-and-package:
    name: Build & Package Addon (.mcaddon)
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Setup Node.js Environment
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - name: Install Dependencies & Type Check
        run: |
          npm ci
          npx tsc --noEmit

      - name: Compile Addon via Regolith
        uses: Bedrock-OSS/regolith-action@v1
        with:
          profile: production
          regolith-version: latest

      - name: Upload Compiled .mcaddon Artifact
        uses: actions/upload-artifact@v4
        with:
          name: NeverLand-Bedrock-Addon
          path: builds/production/*.mcaddon
          retention-days: 14
```

#### 6.4 Guia de Operação da CLI do Regolith
- `regolith init`: Inicializa as pastas de estrutura (`BP/`, `RP/`, `data/`) e cria o `config.json`.
- `regolith install-all`: Lê todos os perfis do `config.json` e baixa automaticamente todos os filtros necessários.
- `regolith run development`: Compila em modo rápido para testes imediatos.
- `regolith run production`: Compila, otimiza e gera o arquivo `.mcaddon` em `builds/production/`.
- `regolith watch development`: Executa um file-watcher contínuo; ao salvar qualquer arquivo de código ou textura, recompila instantaneamente.

---

### Fluxo 7: Schemas JSON, Manifestos Canônicos e Validação Estática

#### 7.1 Mapeamento de Schemas no VS Code (`.vscode/settings.json`)
A configuração abaixo associa os padrões glob de arquivos do projeto Bedrock aos schemas oficiais mantidos pelo **Blockception**. Ela fornece autocompletion, validação de tipos em tempo real e tooltips descritivas no editor de código:

```json
{
  "json.schemas": [
    {
      "description": "Validação de Manifestos de Pacotes Bedrock (Behavior e Resource Packs)",
      "fileMatch": [
        "BP/manifest.json",
        "RP/manifest.json",
        "behavior_packs/*/manifest.json",
        "resource_packs/*/manifest.json"
      ],
      "url": "https://raw.githubusercontent.com/Blockception/Minecraft-bedrock-json-schemas/main/general/manifest.json"
    },
    {
      "description": "Validação de Entidades de Behavior Pack",
      "fileMatch": [
        "BP/entities/*.json",
        "behavior_packs/*/entities/*.json"
      ],
      "url": "https://raw.githubusercontent.com/Blockception/Minecraft-bedrock-json-schemas/main/behavior/entities/entities.json"
    },
    {
      "description": "Validação de Blocos Customizados (Behavior Pack)",
      "fileMatch": [
        "BP/blocks/*.json",
        "behavior_packs/*/blocks/*.json"
      ],
      "url": "https://raw.githubusercontent.com/Blockception/Minecraft-bedrock-json-schemas/main/behavior/blocks/blocks.json"
    },
    {
      "description": "Validação de Itens Customizados (Behavior Pack)",
      "fileMatch": [
        "BP/items/*.json",
        "behavior_packs/*/items/*.json"
      ],
      "url": "https://raw.githubusercontent.com/Blockception/Minecraft-bedrock-json-schemas/main/behavior/items/items.json"
    },
    {
      "description": "Validação de Efeitos de Partículas Bedrock (Resource Pack)",
      "fileMatch": [
        "RP/particles/*.json",
        "resource_packs/*/particles/*.json"
      ],
      "url": "https://raw.githubusercontent.com/Blockception/Minecraft-bedrock-json-schemas/main/resource/particles/particles.json"
    },
    {
      "description": "Validação de Animações Bedrock (Resource Pack)",
      "fileMatch": [
        "RP/animations/*.json",
        "resource_packs/*/animations/*.json"
      ],
      "url": "https://raw.githubusercontent.com/Blockception/Minecraft-bedrock-json-schemas/main/resource/animations/animations.json"
    },
    {
      "description": "Validação de Controladores de Renderização (Resource Pack)",
      "fileMatch": [
        "RP/render_controllers/*.json",
        "resource_packs/*/render_controllers/*.json"
      ],
      "url": "https://raw.githubusercontent.com/Blockception/Minecraft-bedrock-json-schemas/main/resource/render_controllers/render_controllers.json"
    }
  ]
}
```

#### 7.2 Modelos Canônicos de Manifestos Mojang (`manifest.json`) e Pareamento BP/RP
Os pacotes de comportamento (BP) e recursos (RP) exigem UUIDs v4 exclusivos gerados para o cabeçalho (`header`) e para cada módulo (`modules`). Para que o jogo reconheça que ambos pertencem ao mesmo add-on, o Behavior Pack declara a dependência do UUID do Resource Pack:

##### `behavior_pack/manifest.json`
```json
{
  "format_version": 2,
  "header": {
    "name": "Never Land Custom Content BP",
    "description": "Behavior Pack de Itens, Blocos e Scripts para Never Land BDS",
    "uuid": "8b5f3a14-41d2-4e92-91f1-9457a82b9e01",
    "version": [1, 0, 0],
    "min_engine_version": [1, 21, 60]
  },
  "modules": [
    {
      "description": "Módulo de Dados e Comportamentos",
      "type": "data",
      "uuid": "3a7b9c12-88f4-4d2a-bc93-112233445566",
      "version": [1, 0, 0]
    },
    {
      "description": "Módulo de Script API TypeScript",
      "type": "script",
      "language": "javascript",
      "uuid": "7a942b10-e2b5-4a6c-9c7d-304918e745f2",
      "version": [1, 0, 0],
      "entry": "scripts/main.js"
    }
  ],
  "dependencies": [
    {
      // UUID correspondente ao cabeçalho (header.uuid) do Resource Pack abaixo
      "uuid": "9c12e847-5a33-4f19-a1b8-223344556677",
      "version": [1, 0, 0]
    },
    {
      "module_name": "@minecraft/server",
      "version": "2.11.0"
    },
    {
      "module_name": "@minecraft/server-ui",
      "version": "2.3.0"
    }
  ],
  "capabilities": [
    "script_eval"
  ]
}
```

##### `resource_pack/manifest.json`
```json
{
  "format_version": 2,
  "header": {
    "name": "Never Land Custom Content RP",
    "description": "Resource Pack de Modelos, Texturas e Sons para Never Land BDS",
    "uuid": "9c12e847-5a33-4f19-a1b8-223344556677",
    "version": [1, 0, 0],
    "min_engine_version": [1, 21, 60]
  },
  "modules": [
    {
      "description": "Módulo de Recursos Visuais e Áudio",
      "type": "resources",
      "uuid": "1d2e3f4a-5b6c-7d8e-9f0a-aabbccddeeff",
      "version": [1, 0, 0]
    }
  ]
}
```

#### 7.3 Amarração Completa por Namespaces Canônicos (Exemplo de Item e Bloco)

##### A. Cadeia de Amarração de Item Customizado (`custom:espada_rubi`)
1. **Comportamento (BP) — `BP/items/espada_rubi.json`:**
```json
{
  "format_version": "1.21.60",
  "minecraft:item": {
    "description": {
      "identifier": "custom:espada_rubi",
      "menu_category": {
        "category": "equipment",
        "group": "itemGroup.name.sword"
      }
    },
    "components": {
      "minecraft:icon": "custom_espada_rubi",
      "minecraft:max_stack_size": 1,
      "minecraft:damage": 9,
      "minecraft:durability": { "max_durability": 1561 },
      "minecraft:hand_equipped": true
    }
  }
}
```
2. **Registro de Textura (RP) — `RP/textures/item_texture.json`:**
```json
{
  "resource_pack_name": "neverland_rp",
  "texture_name": "atlas.items",
  "texture_data": {
    "custom_espada_rubi": {
      "textures": "textures/items/espada_rubi"
    }
  }
}
```
3. **Localização Amigável (RP) — `RP/texts/pt_BR.lang`:**
```properties
item.custom:espada_rubi=Espada de Rubi Ancestral
```

##### B. Cadeia de Amarração de Bloco Customizado (`custom:minerio_rubi`)
1. **Comportamento (BP) — `BP/blocks/minerio_rubi.json`:**
```json
{
  "format_version": "1.21.60",
  "minecraft:block": {
    "description": {
      "identifier": "custom:minerio_rubi",
      "menu_category": {
        "category": "nature"
      }
    },
    "components": {
      "minecraft:destructible_by_mining": { "seconds_to_destroy": 3.0 },
      "minecraft:friction": 0.6,
      "minecraft:light_emission": 2,
      "minecraft:material_instances": {
        "*": {
          "texture": "custom_minerio_rubi_tex",
          "render_method": "opaque"
        }
      }
    }
  }
}
```
2. **Registro de Textura de Terreno (RP) — `RP/textures/terrain_texture.json`:**
```json
{
  "resource_pack_name": "neverland_rp",
  "texture_name": "atlas.terrain",
  "padding": 8,
  "num_mip_levels": 4,
  "texture_data": {
    "custom_minerio_rubi_tex": {
      "textures": "textures/blocks/minerio_rubi"
    }
  }
}
```
3. **Localização Amigável (RP) — `RP/texts/pt_BR.lang`:**
```properties
tile.custom:minerio_rubi.name=Minério de Rubi Profundo
```

#### 7.4 Validação Estática Programática por IA via Ajv (`tools/validate_addon.js`)
Antes de rodar o servidor ou empacotar o add-on, a IA roda localmente o validador offline construído sobre o motor **Ajv** e os schemas oficiais do **Blockception**:

```javascript
/**
 * tools/validate_addon.js — Validação Estática de Addons Bedrock
 */
const fs = require('fs');
const path = require('path');
const Ajv = require('ajv');
const addFormats = require('ajv-formats');

const ajv = new Ajv({ allErrors: true, strict: false, verbose: true });
addFormats(ajv);

function validateJson(schemaPath, targetPath) {
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf-8'));
  const target = JSON.parse(fs.readFileSync(targetPath, 'utf-8'));
  const validate = ajv.compile(schema);
  const valid = validate(target);

  if (valid) {
    console.log(`✅ [VALIDAÇÃO SUCESSO] ${path.basename(targetPath)} está em conformidade estrita!`);
    return true;
  } else {
    console.error(`❌ [FALHA DE ESQUEMA] Erros encontrados em ${path.basename(targetPath)}:`);
    validate.errors.forEach((err, i) => {
      console.error(`  #${i + 1} Caminho: ${err.instancePath || '(raiz)'} | Mensagem: ${err.message}`);
    });
    return false;
  }
}

if (require.main === module) {
  const [schemaFile, targetFile] = process.argv.slice(2);
  const success = validateJson(schemaFile, targetFile);
  process.exit(success ? 0 : 1);
}
```

---

## 6. Arquitetura de Infraestrutura e Governança do Servidor Linux na Oracle Cloud

### 6.1 Configuração de Resiliência no Systemd

#### Arquivo `/etc/systemd/system/bedrock.socket` (Socket Activation FIFO)
```ini
[Unit]
Description=Console FIFO do Servidor Minecraft Bedrock Never Land
PartOf=bedrock.service

[Socket]
# Cria o FIFO na memória RAM (tmpfs)
ListenFIFO=/run/bedrock.console
SocketMode=0660
SocketUser=opc
SocketGroup=opc
RemoveOnStop=true

# Observação Arquitetural: esta unidade NÃO possui seção [Install] de propósito.
# Ela é instanciada e controlada automaticamente pelo bedrock.service (Requires=bedrock.socket).
```

#### Arquivo `/etc/systemd/system/bedrock.service` (Serviço de Produção Resiliente)
```ini
[Unit]
Description=Servidor Minecraft Bedrock Dedicated Server - Never Land
Wants=network-online.target
After=network-online.target swap.target local-fs.target
Requires=bedrock.socket
After=bedrock.socket
# Desativa limite de desistência: reinicia infinitamente em caso de crash
StartLimitIntervalSec=0

[Service]
Type=simple
User=opc
Group=opc
WorkingDirectory=/home/opc/minecraft/server
Environment="LD_LIBRARY_PATH=/home/opc/minecraft/server"
ExecStart=/home/opc/minecraft/server/bedrock_server

# Conexão de console via FIFO
Sockets=bedrock.socket
StandardInput=socket
StandardOutput=journal
StandardError=journal

# Encerramento gracioso prevenindo corrupção de LevelDB
ExecStop=/home/opc/minecraft/bedrock_stop.sh ${MAINPID}
KillSignal=SIGINT
TimeoutStopSec=60s

Restart=always
RestartSec=10s

LimitNOFILE=65535
Nice=10

# Cgroups v2: Limites estritos de memória para instâncias Micro
MemoryHigh=600M
MemoryMax=750M

[Install]
WantedBy=multi-user.target
```

#### Arquivo `/home/opc/minecraft/bedrock_stop.sh` (Script de Desligamento Gracioso)
```bash
#!/usr/bin/env bash
# ==============================================================================
# bedrock_stop.sh — Encerramento gracioso do Bedrock Dedicated Server via FIFO
# ==============================================================================
set -u

PID="${1:-}"
FIFO="/run/bedrock.console"

# 1. Se o processo já não existe mais, não envia nada para o FIFO
if [ -z "$PID" ] || ! kill -0 "$PID" 2>/dev/null; then
    exit 0
fi

# 2. Injeta o comando "stop" no FIFO
if [ -p "$FIFO" ]; then
    echo "stop" > "$FIFO"
fi

# 3. Aguarda por até 50 segundos até que o processo feche os arquivos LevelDB
for _ in $(seq 1 50); do
    if ! kill -0 "$PID" 2>/dev/null; then
        exit 0
    fi
    sleep 1
done

exit 0
```

---

### 6.2 Regras de Firewall no Linux e Security Lists da Nuvem OCI

O Minecraft Bedrock Dedicated Server v1.21+ opera sob arquitetura híbrida de rede: o tráfego de gameplay RakNet flui via portas UDP 19132 (IPv4) e 19133 (IPv6), enquanto a camada moderna de sinalização e handshake de conexões seguras (**NetherNet WebRTC Signaling / HTTP Handshake**) opera sobre a porta **TCP 19132**. Sem a abertura de TCP 19132, jogadores conectando via redes móveis (4G/5G), redes com CGNAT ou consoles podem enfrentar erros persistentes de timeout e falhas de conexão inicial:

1. **Firewalld no Oracle Linux 9:**
   ```bash
   # Libera tráfego RakNet UDP (IPv4 e IPv6)
   sudo firewall-cmd --zone=public --add-port=19132/udp --permanent
   sudo firewall-cmd --zone=public --add-port=19133/udp --permanent

   # Libera NetherNet WebRTC Signaling e Handshakes TCP
   sudo firewall-cmd --zone=public --add-port=19132/tcp --permanent

   # Recarrega as regras ativas sem derrubar conexões estabelecidas
   sudo firewall-cmd --reload
   sudo firewall-cmd --zone=public --list-ports
   # Saída esperada: 19132/udp 19133/udp 19132/tcp 22/tcp
   ```

2. **Security List / NSG na VCN da OCI (Borda de Nuvem):**
   - **Regra 1 (RakNet UDP):**
     - **Protocolo:** `UDP` (Protocolo 17)
     - **Source CIDR:** `0.0.0.0/0`
     - **Destination Port:** `19132, 19133`
     - **Tipo:** Stateful Ingress
   - **Regra 2 (NetherNet WebRTC TCP):**
     - **Protocolo:** `TCP` (Protocolo 6)
     - **Source CIDR:** `0.0.0.0/0`
     - **Destination Port:** `19132`
     - **Tipo:** Stateful Ingress

---

### 6.3 Rotina de Hot Backup Oficial da Mojang com Truncamento Atômico

#### Arquivo `/home/opc/minecraft/hot_backup.sh`
```bash
#!/usr/bin/env bash
# ==============================================================================
# hot_backup.sh — Protocolo Oficial Mojang de Hot Backup Atômico para LevelDB
# Servidor: Never Land BDS (Oracle Cloud Infrastructure)
# ==============================================================================
set -euo pipefail

BASE_DIR="/home/opc/minecraft"
SERVER_DIR="${BASE_DIR}/server"
BACKUP_DIR="${BASE_DIR}/backups"
TMP_DIR="/tmp/mc_hot_backup_$$"
FIFO="/run/bedrock.console"
LOG_FILE="${BASE_DIR}/hot_backup.log"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_ARCHIVE="${BACKUP_DIR}/backup_never_land_${TIMESTAMP}.tar.gz"

log() {
    local nivel="$1"
    local msg="$2"
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [${nivel}] ${msg}" | tee -a "${LOG_FILE}"
}

# Trap de limpeza garantindo que 'save resume' seja sempre disparado em caso de erro ou término
cleanup() {
    local exit_code=$?
    log "INFO" "Executando rotina de limpeza e garantindo retomada do servidor (save resume)..."
    if [ -p "${FIFO}" ]; then
        echo "save resume" > "${FIFO}" || true
    fi
    rm -rf "${TMP_DIR}"
    if [ ${exit_code} -ne 0 ]; then
        log "ERRO" "O script de hot backup falhou com código ${exit_code}. Nenhum backup corrompido foi gerado."
    fi
}
trap cleanup EXIT INT TERM

log "INFO" "=== INICIANDO HOT BACKUP ATÔMICO DO LEVELDB ==="
mkdir -p "${BACKUP_DIR}" "${TMP_DIR}"

if ! systemctl is-active --quiet bedrock.service; then
    log "AVISO" "bedrock.service não está em execução. Realizando backup a frio tradicional..."
    tar -czf "${BACKUP_ARCHIVE}" -C "${SERVER_DIR}" worlds server.properties permissions.json
    log "SUCESSO" "Backup a frio gerado com sucesso: ${BACKUP_ARCHIVE}"
    exit 0
fi

# Passo 1 do Protocolo: Congelar escrita no LevelDB (save hold)
log "INFO" "Passo 1/4: Enviando 'save hold' para congelar compaction no LevelDB..."
echo "save hold" > "${FIFO}"

# Passo 2 do Protocolo: Polling com 'save query' até o manifesto de arquivos estar pronto
READY=0
FILE_MANIFEST=""

for i in $(seq 1 30); do
    sleep 2
    echo "save query" > "${FIFO}"
    sleep 1

    # Usamos '-o cat' para eliminar o prefixo de syslog do journalctl que possui dois-pontos
    # e quebra a separação de caminho de arquivo e tamanho em bytes!
    LOG_OUTPUT=$(journalctl -u bedrock.service -o cat -n 25 --no-pager)

    if echo "${LOG_OUTPUT}" | grep -q "Data saved. Files are now ready to be copied"; then
        # Captura todas as linhas a partir de "Data saved" até o fim do manifesto
        # e concatena em uma única string contínua, suportando manifestos multi-linha
        RAW_MANIFEST=$(echo "${LOG_OUTPUT}" | sed -n '/Data saved\. Files are now ready to be copied\./,$p' | sed 's/Data saved\. Files are now ready to be copied\.//' | tr '\n' ' ')
        
        if echo "${RAW_MANIFEST}" | grep -q ":"; then
            FILE_MANIFEST="${RAW_MANIFEST}"
            READY=1
            log "INFO" "Manifesto LevelDB confirmado com sucesso na tentativa ${i}."
            break
        fi
    fi
done

if [[ ${READY} -ne 1 || -z "${FILE_MANIFEST}" ]]; then
    log "ERRO" "Timeout ao obter confirmação de 'save query' do console BDS!"
    exit 1
fi

# Passo 3 do Protocolo: Copiar e truncar os arquivos SSTables (.ldb) no tamanho exato
log "INFO" "Passo 2/4: Copiando arquivos do snapshot LevelDB com truncamento preciso..."

# Salva arquivos de configuração auxiliares
cp "${SERVER_DIR}/server.properties" "${TMP_DIR}/" || true
cp "${SERVER_DIR}/permissions.json" "${TMP_DIR}/" || true

# Processa cada entrada do manifesto separada por vírgula
IFS=',' read -ra ENTRIES <<< "${FILE_MANIFEST}"
COPIED_COUNT=0

for entry in "${ENTRIES[@]}"; do
    trimmed=$(echo "${entry}" | xargs)
    if [[ -z "${trimmed}" ]]; then continue; fi

    f_path=$(echo "${trimmed}" | cut -d':' -f1 | xargs)
    f_bytes=$(echo "${trimmed}" | cut -d':' -f2 | xargs)

    if [[ -z "${f_path}" || -z "${f_bytes}" ]]; then
        log "ERRO" "Entrada de manifesto malformada: '${entry}'"
        exit 1
    fi

    src="${SERVER_DIR}/${f_path}"
    dest="${TMP_DIR}/${f_path}"

    if [[ -f "${src}" ]]; then
        mkdir -p "$(dirname "${dest}")"
        cp "${src}" "${dest}"
        # Aplica o truncamento exato ao número de bytes válido no instante do snapshot
        truncate -s "${f_bytes}" "${dest}"
        COPIED_COUNT=$((COPIED_COUNT + 1))
    else
        log "ERRO" "Arquivo vital do LevelDB listado no manifesto não foi encontrado: ${src}"
        log "ERRO" "Abortando backup imediatamente para evitar snapshot inconsistente!"
        exit 1
    fi
done

log "INFO" "Total de ${COPIED_COUNT} arquivos do LevelDB copiados e truncados com sucesso."

# Passo 4 do Protocolo: Retomar operações do servidor imediatamente (save resume)
log "INFO" "Passo 3/4: Liberando o servidor Bedrock (save resume)..."
echo "save resume" > "${FIFO}"

# Passo 5: Compactação e auditoria rigorosa de integridade
log "INFO" "Passo 4/4: Compactando snapshot para ${BACKUP_ARCHIVE}..."
tar -czf "${BACKUP_ARCHIVE}" -C "${TMP_DIR}" .

log "INFO" "Auditando integridade do arquivo compactado via gzip -t..."
if gzip -t "${BACKUP_ARCHIVE}"; then
    TAMANHO=$(du -h "${BACKUP_ARCHIVE}" | cut -f1)
    log "SUCESSO" "Integridade auditada e validada com sucesso! Tamanho final: ${TAMANHO}"
else
    log "ERRO" "Falha crítica: o arquivo compactado está corrompido!"
    rm -f "${BACKUP_ARCHIVE}"
    exit 1
fi

# Política de retenção: remove backups com mais de 7 dias SOMENTE após validação com sucesso
log "INFO" "Aplicando política de retenção (removendo backups locais com mais de 7 dias)..."
find "${BACKUP_DIR}" -type f -name "backup_never_land_*.tar.gz" -mtime +7 -delete

# Sincronização secundária com OCI Object Storage
OCI_BUCKET_NAME="minecraft-backups-neverland"
if command -v oci &> /dev/null; then
    log "INFO" "Sincronizando arquivo de backup com bucket OCI Object Storage..."
    oci os object put \
        --bucket-name "${OCI_BUCKET_NAME}" \
        --file "${BACKUP_ARCHIVE}" \
        --name "$(basename "${BACKUP_ARCHIVE}")" \
        --no-overwrite false || log "AVISO" "Não foi possível sincronizar com bucket OCI."
fi

log "INFO" "=== HOT BACKUP ATÔMICO FINALIZADO COM SUCESSO ==="
```

#### Unidades Systemd de Hot Backup Agendado

##### Arquivo `/etc/systemd/system/bedrock-backup.service`
```ini
[Unit]
Description=Rotina de Hot Backup Atômico do Servidor Bedrock Never Land
After=bedrock.service

[Service]
Type=oneshot
User=opc
Group=opc
ExecStart=/home/opc/minecraft/hot_backup.sh
```

##### Arquivo `/etc/systemd/system/bedrock-backup.timer`
```ini
[Unit]
Description=Temporizador Diário de Backup do Servidor Minecraft Bedrock (04:00)
Requires=bedrock-backup.service

[Timer]
# Executa todos os dias pontualmente às 04:00 da madrugada
OnCalendar=*-*-* 04:00:00
# Caso a VM esteja temporariamente desligada no horário, executa imediatamente ao religar
Persistent=true

[Install]
WantedBy=timers.target
```

##### Ativação do Temporizador no Linux:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now bedrock-backup.timer
sudo systemctl list-timers --all | grep bedrock
```

---

### 6.4 Opção de Deploy Conteinerizado com Docker Compose

Caso o usuário opte por rodar o servidor conteinerizado através da imagem mantida por Geoff Bourne (`itzg/minecraft-bedrock-server`):

```yaml
# /home/opc/minecraft/docker-compose.yml
version: '3.8'

services:
  bedrock-never-land:
    image: itzg/minecraft-bedrock-server:latest
    container_name: bedrock-never-land
    restart: unless-stopped
    stdin_open: true
    tty: true
    ports:
      # Protocolo de jogo RakNet (IPv4 e IPv6)
      - "19132:19132/udp"
      - "19133:19133/udp"
      # Protocolo de sinalização NetherNet WebRTC
      - "19132:19132/tcp"
    volumes:
      - ./data:/data
    environment:
      EULA: "TRUE"
      VERSION: "LATEST"
      TZ: "America/Sao_Paulo"
      SERVER_NAME: "Never Land BDS"
      GAMEMODE: "survival"
      DIFFICULTY: "normal"
      LEVEL_NAME: "Never Land"
      MAX_PLAYERS: "10"
      ONLINE_MODE: "true"
      ALLOW_CHEATS: "false"
      VIEW_DISTANCE: "12"
      TICK_DISTANCE: "4"
      MAX_THREADS: "2"
      UID: "1000"
      GID: "1000"
    healthcheck:
      test: ["CMD-SHELL", "pgrep bedrock_server || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    deploy:
      resources:
        limits:
          # NOTA CRÍTICA DE DIMENSIONAMENTO DE MEMÓRIA:
          # Para instância ARM64 Ampere (VM.Standard.A1.Flex com 24 GB RAM): memory: 1500M
          # Para instância AMD Micro (VM.Standard.E2.1.Micro com 1 GB RAM): memory: 750M
          memory: 1500M
        reservations:
          memory: 512M
```

---

### 6.5 Interface Interativa de Controle do Servidor via SSH (`mc.py`)

Em estrita consonância com o Requisito **R2** do projeto, a administração operacional do servidor Never Land é realizada pelo utilitário nativo em Python `mc.py` (`Server/systemd/mc.py`), desenvolvido com mais de 1100 linhas de lógica pura sem dependências externas pesadas:

#### Destaques Arquiteturais do `mc.py`:
1. **Comunicação Não-Bloqueante via FIFO:**
   - Abre o canal `/run/bedrock.console` utilizando as flags `os.O_NONBLOCK | os.O_WRONLY`, garantindo que o envio de comandos nunca congele o terminal do operador mesmo se o processo estiver sob carga pesada.
2. **Captura Precisa de Saídas via Journald Cursor:**
   - Para comandos de consulta (`list`, `status`), o `mc.py` armazena o cursor de leitura do journald (`journalctl --cursor`) antes de emitir o comando no FIFO e lê estritamente as mensagens emitidas após aquele cursor, eliminando a interferência de logs antigos ou chat de jogadores.
3. **Monitoramento Integrado de Cgroups v2:**
   - Inspeciona diretamente `/sys/fs/cgroup/system.slice/bedrock.service/` para relatar consumo de RAM física em tempo real, pico de uso (`memory.peak`), limiares de pressão (`memory.high`) e limites de contenção (`memory.max`).

#### Comandos Operacionais da CLI e Modo REPL:
- **Modo Interativo (REPL):**
  ```bash
  python3 /home/opc/minecraft/mc.py
  # Abre terminal interativo com autocompletion, histórico de comandos e prompt estilizado
  ```
- **Comandos Diretos de Terminal:**
  - `python3 mc.py status`: Exibe status do serviço systemd, PID ativo, uptime, portas de escuta e métricas de Cgroups v2.
  - `python3 mc.py list`: Consulta em tempo real os jogadores online conectados à sessão.
  - `python3 mc.py say "Mensagem"`: Transmite aviso oficial aos jogadores no chat global do servidor.
  - `python3 mc.py kick <jogador> "Motivo"`: Desconecta um jogador específico com notificação formatada.
  - `python3 mc.py backup`: Dispara a rotina de hot backup atômico e exibe o progresso em tempo real.
  - `python3 mc.py stop`: Encerra o servidor de forma graciosa garantindo a gravação de chunks LevelDB.

---

### 6.6 Modding Avançado de Servidor BDS (LeviLamina e Crossplay)

#### 1. BDS Vanilla vs Ecossistema LeviLamina
Para servidores que necessitam estender o Minecraft além das capacidades da Script API padrão:
- **BDS Vanilla (Adotado no Never Land):** Utiliza a Script API oficial da Mojang em TypeScript (`@minecraft/server`). Máxima estabilidade, compatibilidade direta com todas as atualizações de versão estável da Mojang e zero risco de quebra de hooks binários.
- **LiteLDev/LeviLamina (Sucessor do LiteLoaderBDS):** Carregador de modificações nativas em C++ que intercepta funções do binário compilado (`bedrock_server`) via MinHook e detours em memória com assinatura de símbolos `libhat`. Permite plugins nativos em C++, Lua e JavaScript com acesso a sockets TCP brutos, bancos de dados SQLite/MySQL e RPCs de alta velocidade, gerenciados pelo utilitário `Lip`.

#### 2. Crossplay com GeyserMC / Floodgate
- O **GeyserMC** atua como uma ponte de tradução de pacotes em tempo real, convertendo o protocolo UDP RakNet do Bedrock no protocolo TCP de servidores Java Edition.
- **Limitação Técnica Fundamental:** O GeyserMC permite que jogadores Bedrock acessem servidores Java. Contudo, ele **NÃO** executa addons e blocos nativos Bedrock em servidores Java sem pacotes de mapeamento específicos (*Geyser Extensions*). Para o servidor Never Land, que é um Bedrock Dedicated Server puro com Addons nativos, a arquitetura direta via BDS é a única que preserva 100% da fidelidade das entidades, scripts e partículas Bedrock.

---

## 7. Roteiro de Implementação e Checklist de Validação

Para a execução sequencial do projeto, a equipe deve seguir a lista de verificação abaixo:

### Fase 1: Preparação do Ambiente Local
- [ ] Instalar Node.js 20 LTS, Python 3.11+, Git e Regolith CLI no Windows.
- [ ] Preparar a pasta `tools/` com os clones e builds de `blockbench-mcp`, `pixel-mcp`, `aseprite-mcp-studio`, `minecraft-structure-mcp` e `minecraft-bedrock-mcp`.
- [ ] Instalar dependências Python via pip (`pillow`, `numpy`, `mcrcon`, `oci`, `mcp-ssh-session`, `mcp-server-oci`).
- [ ] Configurar os servidores MCP no arquivo de configuração do cliente de IA (`mcp_config.json` ou `claude_desktop_config.json`).
- [ ] Instalar o plugin `blockbench_mcp.js` no Blockbench Desktop e validar o endpoint `http://127.0.0.1:8787/ping`.
- [ ] Iniciar o visualizador web do `aseprite-mcp-studio` na porta 7790 e validar testes de pixelização.

### Fase 2: Pipelines de Add-on e Validação Estática
- [ ] Configurar o `config.json` do Regolith com perfis `development` e `production`.
- [ ] Configurar o workflow de CI/CD `.github/workflows/regolith.yml`.
- [ ] Configurar o arquivo `.vscode/settings.json` com os schemas do Blockception.
- [ ] Configurar os manifestos `manifest.json` com dependência cruzada entre Behavior Pack e Resource Pack.
- [ ] Validar a amarração de texturas com `textures/terrain_texture.json` e `textures/item_texture.json`.
- [ ] Executar o script `tools/validate_addon.js` com o Ajv sobre todos os manifestos e arquivos de entidades.
- [ ] Testar a compilação de scripts TypeScript com `esbuild.config.js`.

### Fase 3: Infraestrutura e Resiliência na Oracle Cloud
- [ ] Configurar as regras de Ingress para **UDP 19132/19133** e **TCP 19132 (NetherNet Signaling)** na Security List da VCN na OCI.
- [ ] Liberar as portas **19132/udp**, **19133/udp** e **19132/tcp** no `firewalld` da VM Oracle Linux 9.
- [ ] Habilitar o serviço de sincronização de rede: `sudo systemctl enable NetworkManager-wait-online.service`.
- [ ] Criar o arquivo de Swap de 5 GB (`/swapfile`) e configurar `vm.swappiness=30`.
- [ ] Implantar `bedrock.socket` e `bedrock.service` no diretório de unidades `/etc/systemd/system/`.
- [ ] Implantar o executável de encerramento em `/home/opc/minecraft/bedrock_stop.sh` com permissões de execução: `chmod +x /home/opc/minecraft/bedrock_stop.sh`.
- [ ] Habilitar e iniciar o serviço: `sudo systemctl daemon-reload && sudo systemctl enable --now bedrock.service`.
- [ ] Realizar teste real de reboot na VM (`sudo reboot`) e verificar se o processo sobe ouvindo nas portas UDP/TCP 19132 após a rede estar online.

### Fase 4: Governança de Backups, Ferramenta de Controle SSH e Documentação
- [ ] Implantar o utilitário nativo de controle interativo em `/home/opc/minecraft/mc.py` com `chmod +x /home/opc/minecraft/mc.py`.
- [ ] Validar comandos administrativos (`mc.py status`, `mc.py list`, `mc.py say`, `mc.py stop`) via sessão SSH interativa atendendo a R2.
- [ ] Implantar o script `/home/opc/minecraft/hot_backup.sh` com permissões `chmod 0750`.
- [ ] Implantar as unidades `/etc/systemd/system/bedrock-backup.service` e `/etc/systemd/system/bedrock-backup.timer`.
- [ ] Ativar o agendamento de backup diário: `sudo systemctl enable --now bedrock-backup.timer`.
- [ ] Executar um teste manual de hot backup (`sudo /home/opc/minecraft/hot_backup.sh`) e verificar a integridade do `.tar.gz` com `gzip -t`.
- [ ] Manter e expandir o arquivo `DOCUMENTACAO.md` no repositório com o guia operacional completo via SSH atendendo a R3.

---
*Plano Mestre consolidado e homologado para execução da infraestrutura e desenvolvimento do servidor Never Land.*
