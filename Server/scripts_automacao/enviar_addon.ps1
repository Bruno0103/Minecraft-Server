# ==============================================================================
# SCRIPT DE ENVIO E INSTALAÇÃO AUTOMATIZADA DE ADDONS (WINDOWS -> SERVIDOR OCI)
# ==============================================================================
# Autor: Equipe de Engenharia / Antigravity
# Finalidade: Enviar arquivos de modificação (.mcaddon, .mcpack ou .zip) a partir
#             do ambiente local Windows diretamente para a pasta de recebimento do
#             servidor Minecraft Bedrock na nuvem Oracle Cloud Infrastructure (OCI)
#             via SCP/SSH seguro, disparando a rotina de descompactação e injeção
#             automática nos mundos do Bedrock Dedicated Server (BDS).
#
# Parâmetros:
#   - CaminhoArquivo (opcional): Caminho completo ou relativo para o arquivo de addon.
#                                Caso omitido, o script solicitará interativamente
#                                que o usuário arraste e solte o arquivo no console.
#
# Uso via Terminal PowerShell:
#   .\enviar_addon.ps1 -CaminhoArquivo "C:\Downloads\meu_addon.mcaddon"
#
# Uso Facilitado no Windows Explorer:
#   Arraste e solte o arquivo .mcaddon ou .mcpack sobre o arquivo 'enviar_addon.bat'.
# ==============================================================================

[CmdletBinding()]
param (
    # Parâmetro posicional 0: Caminho para o pacote do addon (.mcaddon, .mcpack ou .zip)
    [Parameter(Mandatory = $false, Position = 0, HelpMessage = "Caminho do arquivo de addon a ser enviado")]
    [string]$CaminhoArquivo
)

# ------------------------------------------------------------------------------
# 1. Configurações de Conexão com o Servidor Oracle Cloud Infrastructure (OCI)
# ------------------------------------------------------------------------------
# Endereço IP público dedicado da máquina virtual do servidor Minecraft (VM Always Free)
$HostRemoto = "157.151.2.91"

# Usuário padrão com privilégios de sudo sem senha no Oracle Linux Server
$Usuario = "opc"

# Caminho absoluto da chave privada RSA 2048-bit utilizada para autenticação SSH/SCP
$PossiveisCaminhosChave = @(
    "$PSScriptRoot\..\vm_credentials\SSH keys\ssh-key-2026-11-03.key",
    "$PSScriptRoot\VM\SSH keys\ssh-key-2026-11-03.key",
    "$PSScriptRoot\..\..\Server\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
)

$ChaveSSH = $PossiveisCaminhosChave | Where-Object { Test-Path -Path $_ } | Select-Object -First 1
if (-not $ChaveSSH) {
    $ChaveSSH = "$PSScriptRoot\..\vm_credentials\SSH keys\ssh-key-2026-11-03.key"
}

# Diretório remoto no servidor Linux onde os pacotes brutos são recebidos para processamento
$PastaDestinoRemota = "/home/opc/minecraft/addons_inbox/"

# ------------------------------------------------------------------------------
# 2. Validação da Chave SSH Privada
# ------------------------------------------------------------------------------
# Verifica se a chave privada existe no caminho esperado antes de tentar qualquer conexão de rede
if (-not (Test-Path -Path $ChaveSSH)) {
    Write-Host "[ERRO CRÍTICO] Arquivo de chave privada SSH não encontrado em nenhum dos locais esperados." -ForegroundColor Red
    Write-Host "[DICA] Certifique-se de que o arquivo da chave SSH está presente em 'Server\vm_credentials\SSH keys\'." -ForegroundColor Yellow
    pause
    exit 1
}

# ------------------------------------------------------------------------------
# 3. Tratamento Interativo do Caminho do Arquivo (Drag-and-Drop)
# ------------------------------------------------------------------------------
# Caso o script seja chamado sem parâmetros, exibe um menu explicativo para o usuário
if ([string]::IsNullOrWhiteSpace($CaminhoArquivo)) {
    Write-Host "====================================================================" -ForegroundColor Cyan
    Write-Host "         ENVIO FACILITADO DE ADDONS - MINECRAFT BEDROCK             " -ForegroundColor Cyan
    Write-Host "         Servidor Alvo: Never Land ($HostRemoto)                    " -ForegroundColor Cyan
    Write-Host "====================================================================" -ForegroundColor Cyan
    $CaminhoArquivo = Read-Host "Arraste o arquivo do Addon (.mcaddon, .mcpack ou .zip) para cá e pressione Enter"
    
    # Remove eventuais aspas duplas ou simples adicionadas pelo Explorer ao arrastar o arquivo
    $CaminhoArquivo = $CaminhoArquivo.Trim('"').Trim("'")
}

# Verifica se o arquivo informado pelo usuário realmente existe no disco local
if (-not (Test-Path -Path $CaminhoArquivo)) {
    Write-Host "[ERRO] O arquivo de addon especificado não existe: $CaminhoArquivo" -ForegroundColor Red
    pause
    exit 1
}

# Extrai o nome do arquivo para exibição no log
$NomeArquivo = Split-Path -Path $CaminhoArquivo -Leaf
Write-Host "`n[1/3] Preparando envio do addon: $NomeArquivo" -ForegroundColor Yellow

# ------------------------------------------------------------------------------
# 4. Envio Seguro do Pacote via SCP (OpenSSH Secure Copy Protocol)
# ------------------------------------------------------------------------------
# Parâmetros utilizados:
#   -i: Especifica a chave privada de autenticação
#   -o StrictHostKeyChecking=no: Evita interrupção solicitando confirmação de host fingerprint
#   -o ConnectTimeout=15: Limite de 15 segundos para estabelecer o handshake TCP
Write-Host "[2/3] Transferindo arquivo para a caixa de entrada do servidor via SCP..." -ForegroundColor Yellow
$scpArgs = @(
    "-i", $ChaveSSH,
    "-o", "StrictHostKeyChecking=no",
    "-o", "ConnectTimeout=15",
    $CaminhoArquivo,
    "${Usuario}@${HostRemoto}:${PastaDestinoRemota}"
)

& scp @scpArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[ERRO] Falha ao enviar o arquivo via SCP (Código de saída: $LASTEXITCODE)." -ForegroundColor Red
    Write-Host "[DICA] Verifique se a VM na Oracle Cloud está ligada, se o IP $HostRemoto está acessível e se a porta 22 está liberada." -ForegroundColor Yellow
    pause
    exit 1
}
Write-Host "[OK] Arquivo transferido com sucesso para: $PastaDestinoRemota" -ForegroundColor Green

# ------------------------------------------------------------------------------
# 5. Execução do Instalador Inteligente no Servidor Remoto via SSH
# ------------------------------------------------------------------------------
# Executa uma cadeia de comandos no servidor remoto:
#   1. chmod +x /home/opc/minecraft/install_addon.sh (Garante permissão de execução)
#   2. /home/opc/minecraft/install_addon.sh (Processa pacotes, descompacta BP/RP e edita JSONs do mundo)
#   3. sudo systemctl restart bedrock.service (Reinicia o BDS de forma graciosa para carregar os novos pacotes)
Write-Host "`n[3/3] Disparando o instalador inteligente e reiniciando o serviço Bedrock..." -ForegroundColor Yellow
$ComandoRemoto = "chmod +x /home/opc/minecraft/install_addon.sh && /home/opc/minecraft/install_addon.sh && sudo systemctl restart bedrock.service"

$sshArgs = @(
    "-i", $ChaveSSH,
    "-o", "StrictHostKeyChecking=no",
    "-o", "ConnectTimeout=15",
    "${Usuario}@${HostRemoto}",
    $ComandoRemoto
)

& ssh @sshArgs
if ($LASTEXITCODE -eq 0) {
    Write-Host "`n====================================================================" -ForegroundColor Green
    Write-Host "  ADDON INSTALADO COM SUCESSO E SERVIDOR REINICIADO!" -ForegroundColor Green
    Write-Host "  IP do Servidor: $HostRemoto | Porta: 19132" -ForegroundColor Green
    Write-Host "====================================================================" -ForegroundColor Green
} else {
    Write-Host "`n[AVISO] O arquivo foi enviado com sucesso, mas a execução remota reportou o código de retorno $LASTEXITCODE." -ForegroundColor Yellow
    Write-Host "[DICA] Conecte via SSH para inspecionar os logs: journalctl -u bedrock.service -n 50" -ForegroundColor Yellow
}

Write-Host "`nPressione qualquer tecla para fechar..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
