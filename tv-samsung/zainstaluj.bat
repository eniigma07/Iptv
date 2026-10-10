@echo off
rem MojaTV - instalacja na telewizorze Samsung przez Tizen Studio (sposób zapasowy).
rem Wymaga: Tizen Studio w C:\tizen-studio, certyfikatu Samsung (Certificate Manager)
rem i telewizora w trybie programisty. Szczegóły w README.md.
chcp 65001 >nul
setlocal

set "TS=C:\tizen-studio"
set "TIZEN=%TS%\tools\ide\bin\tizen.bat"
set "SDB=%TS%\tools\sdb.exe"
set "APP=%~dp0app"

if not exist "%TIZEN%" (
  echo Nie znaleziono Tizen Studio w %TS%.
  echo Zainstaluj je albo zmien sciezke TS na poczatku tego pliku.
  pause & exit /b 1
)

set /p TVIP=Podaj adres IP telewizora (np. 192.168.1.50):
"%SDB%" connect %TVIP%
"%SDB%" devices

set "DEV="
for /f "skip=1 tokens=1,3" %%a in ('"%SDB%" devices') do (
  echo %%a | findstr /b /c:"%TVIP%" >nul && set "DEV=%%b"
)
if "%DEV%"=="" (
  echo Nie udalo sie polaczyc z telewizorem %TVIP%.
  echo Sprawdz tryb programisty i adres IP komputera wpisany w telewizorze.
  pause & exit /b 1
)
echo Telewizor: %DEV%

pushd "%APP%"
if exist .buildResult rmdir /s /q .buildResult
call "%TIZEN%" build-web -e ".*"
if errorlevel 1 goto blad
call "%TIZEN%" package -t wgt -o "%~dp0" -- .buildResult
if errorlevel 1 goto blad
popd

call "%TIZEN%" install -n MojaTV.wgt -t %DEV% -- "%~dp0"
if errorlevel 1 goto blad

echo.
echo Gotowe! MojaTV jest na telewizorze (Aplikacje).
pause
exit /b 0

:blad
popd 2>nul
echo.
echo Cos poszlo nie tak - przewin w gore i sprawdz komunikat bledu.
echo Najczestsza przyczyna: brak aktywnego certyfikatu Samsung w Certificate Manager
echo albo brak "Permit to install applications" w Device Manager.
pause
exit /b 1
