@echo off
setlocal
cd /d "%~dp0"
if not exist "grass_block.ico" "..\.venv_build\Scripts\python.exe" make_grass_icon.py

if not exist "..\.venv_build\Scripts\pyinstaller.exe" (
    echo PyInstaller nao encontrado em ..\.venv_build
    echo Execute: py -m venv ..\.venv_build ^&^& ..\.venv_build\Scripts\python.exe -m pip install pyinstaller
    exit /b 1
)

"..\.venv_build\Scripts\pyinstaller.exe" --noconfirm --clean --onefile --windowed ^
    --name MinecraftServerControl --icon grass_block.ico mc_gui.py
if errorlevel 1 exit /b %errorlevel%

echo.
echo EXE criado em: "%~dp0dist\MinecraftServerControl.exe"
endlocal
