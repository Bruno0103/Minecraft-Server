@echo off
rem ==============================================================================
rem FACILITADOR DE ENVIO DE ADDONS (ARRASTE E SOLTE O ARQUIVO AQUI)
rem ==============================================================================
rem Arraste qualquer arquivo .mcaddon, .mcpack ou .zip para cima deste arquivo .bat
rem para enviar e instalar automaticamente no servidor de Minecraft na nuvem.
rem ==============================================================================

chcp 65001 >nul
title Enviar Addon para o Servidor Minecraft

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0enviar_addon.ps1" "%~1"
pause
