@echo off
setlocal
cd /d "%~dp0"

set "PY_EXE=..\..\.venv_build\Scripts\python.exe"
if not exist "%PY_EXE%" set "PY_EXE=..\.venv_build\Scripts\python.exe"
if not exist "%PY_EXE%" set "PY_EXE=python"

if not exist "grass_block.ico" "%PY_EXE%" make_grass_icon.py

set "PYI_EXE=..\..\.venv_build\Scripts\pyinstaller.exe"
if not exist "%PYI_EXE%" set "PYI_EXE=..\.venv_build\Scripts\pyinstaller.exe"
if not exist "%PYI_EXE%" set "PYI_EXE=pyinstaller"

"%PYI_EXE%" --noconfirm --clean --onefile --windowed ^
    --name MinecraftServerControl --icon grass_block.ico mc_gui.py
if errorlevel 1 (
    echo Erro ao compilar com PyInstaller.
    exit /b %errorlevel%
)

echo.
echo EXE criado em: "%~dp0dist\MinecraftServerControl.exe"
endlocal
