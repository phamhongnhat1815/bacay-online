@echo off
setlocal
cd /d "%~dp0"
java "-Dfile.encoding=UTF-8" -Xmx512m -cp "client.jar;shared.jar" com.game3cay.RunClient "%~1" "%~2"
if errorlevel 1 pause
endlocal
