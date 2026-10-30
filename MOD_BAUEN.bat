@echo off
chcp 65001 >nul
title Testohd Styles bauen
cd /d "%~dp0"
echo ==========================================
echo    Testohd Styles - Mod bauen (Made by Testohd23)
echo ==========================================
echo.

rem ---- 1) Java 25 suchen (das Fabric-Bauwerkzeug braucht Java 25) ----
call :findjdk
if defined JDK25 goto havejava

echo [!] Java 25 wird zum Bauen gebraucht und ist noch nicht installiert.
echo     Ich installiere Java 25 (Temurin) mit winget...
echo     (Falls Windows fragt, bitte mit "Ja" bestaetigen.)
winget install --id EclipseAdoptium.Temurin.25.JDK -e --accept-package-agreements --accept-source-agreements
call :findjdk
if defined JDK25 goto havejava
echo.
echo [FEHLER] Java 25 wurde nicht gefunden. Bitte den PC neu starten und MOD_BAUEN.bat nochmal starten.
pause
exit /b 1

:havejava
set "JAVA_HOME=%JDK25%"
set "PATH=%JDK25%\bin;%PATH%"
echo [OK] Java 25 gefunden: %JDK25%

rem ---- 2) Bauen ----
echo.
echo [..] Baue die Mod. Beim ersten Mal laedt Gradle Minecraft + Fabric herunter (ein paar Minuten)...
echo      Bitte warten und das Fenster nicht schliessen.
echo.
call gradlew.bat build --no-configuration-cache > build-log.txt 2>&1
if errorlevel 1 (
  echo [FEHLER] Der Build ist fehlgeschlagen.
  echo Schick die Datei build-log.txt an Claude, dann wird der Fehler behoben.
  start "" notepad build-log.txt
  pause
  exit /b 1
)

set JAR=
for %%f in (build\libs\testohd-styles-*.jar) do (
  echo %%~nxf | findstr /i "sources" >nul || set JAR=%%f
)
echo [OK] Fertig: %JAR%
echo.

rem ---- 3) In den mods-Ordner kopieren ----
set MODS=%APPDATA%\.minecraft\mods
if exist "%APPDATA%\.minecraft" (
  if not exist "%MODS%" mkdir "%MODS%"
  copy /y "%JAR%" "%MODS%\" >nul
  echo [OK] In deinen mods-Ordner kopiert: %MODS%
  dir /b "%MODS%" | findstr /i "fabric-api" >nul
  if errorlevel 1 (
    echo [!] Fabric API fehlt noch im mods-Ordner!
    echo     Lade "Fabric API" fuer 1.21.11 von modrinth.com/mod/fabric-api herunter
    echo     und lege sie auch in den mods-Ordner.
  ) else (
    echo [OK] Fabric API ist schon da.
  )
) else (
  echo [i] Kein .minecraft-Ordner gefunden ^(z.B. CurseForge^).
  echo     Kopiere die .jar selbst in den mods-Ordner deiner Instanz.
)
echo.
rem ---- 4) Auto-Update: ueberall ersetzen, wo die Mod schon installiert ist (NoRisk, andere Launcher) ----
for %%f in ("%JAR%") do set "JARNAME=%%~nxf"
set UPDATED=0
for %%R in ("%APPDATA%" "%LOCALAPPDATA%") do (
  for /f "delims=" %%F in ('dir /s /b "%%~R\testohd-styles-*.jar" 2^>nul ^| findstr /v /i "sources"') do (
    if /i not "%%~dpF"=="%MODS%\" (
      copy /y "%JAR%" "%%~dpF" >nul && (
        if /i not "%%~nxF"=="%JARNAME%" del /q "%%F"
        echo [OK] Auch aktualisiert: %%~dpF
        set UPDATED=1
      )
    )
  )
)
if "%UPDATED%"=="0" echo [i] Keine weitere Installation gefunden ^(z.B. NoRisk^). Leg die .jar dort einmal selbst rein - danach wird sie automatisch mit aktualisiert.
echo.
explorer "build\libs"
echo Starte Minecraft 1.21.11 mit Fabric Loader und druecke im Spiel K.
pause
exit /b 0

rem ---- sucht einen installierten JDK 25 (Temurin / Microsoft / Oracle) ----
:findjdk
set JDK25=
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do set "JDK25=%%~fd"
if defined JDK25 exit /b 0
for /d %%d in ("%ProgramFiles%\Microsoft\jdk-25*") do set "JDK25=%%~fd"
if defined JDK25 exit /b 0
for /d %%d in ("%ProgramFiles%\Java\jdk-25*") do set "JDK25=%%~fd"
exit /b 0
