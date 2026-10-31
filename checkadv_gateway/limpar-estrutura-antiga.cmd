@echo off
setlocal
chcp 65001 >nul

rem ==================================================================
rem  Remove da RAIZ os restos do projeto de módulo único (Initializr):
rem    src\   HELP.md   target\
rem  Não toca em gateway-contrato, gateway-app nem resolvedor-app.
rem  Depois, compila tudo para confirmar.
rem ==================================================================

cd /d "%~dp0"

rem Segurança: só roda na raiz do projeto multimódulo
if not exist "pom.xml" goto :fora
if not exist "gateway-app\pom.xml" goto :fora
if not exist "gateway-contrato\pom.xml" goto :fora
if not exist "resolvedor-app\pom.xml" goto :fora

echo Pasta: %CD%
echo.
echo Serão apagados (somente na raiz):
if exist "src\"     echo   - src\
if exist "HELP.md"  echo   - HELP.md
if exist "target\"  echo   - target\
echo.
choice /c SN /m "Confirmar"
if errorlevel 2 (
    echo Cancelado.
    goto :fim
)

if exist "src\"    rmdir /s /q "src"
if exist "HELP.md" del /q "HELP.md"
if exist "target\" rmdir /s /q "target"
echo Limpeza concluída.
echo.

choice /c SN /m "Compilar e rodar os testes agora (mvnw verify)"
if errorlevel 2 goto :eclipse

call mvnw.cmd -B verify
if errorlevel 1 (
    echo.
    echo *** O build falhou. Copie a saída acima e envie. ***
    goto :fim
)

:eclipse
echo.
echo Falta um passo no Eclipse:
echo   selecione o projeto checkadv_gateway e tecle Alt+F5 ^(Maven ^> Update Project^) ^> OK
goto :fim

:fora
echo Este script precisa estar na raiz do checkadv_gateway ^(junto do pom.xml pai^).

:fim
echo.
pause
endlocal
