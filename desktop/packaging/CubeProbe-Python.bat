@echo off
setlocal
cd /d "%~dp0"
where pythonw >nul 2>nul
if %errorlevel%==0 (
    start "" pythonw "CubeProbe.pyz"
    exit /b 0
)
where pyw >nul 2>nul
if %errorlevel%==0 (
    start "" pyw -3 "CubeProbe.pyz"
    exit /b 0
)
echo Python 3 with Tk was not found.
echo Install Python from https://www.python.org/ and enable tcl/tk.
pause
