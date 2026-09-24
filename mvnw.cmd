@REM ----------------------------------------------------------------------------
@REM Maven wrapper — dùng Maven đã tải tại %USERPROFILE%\.m2\maven-3.9.9
@REM Nếu chưa có, xem README.md để tải thủ công hoặc cài Maven vào PATH.
@REM ----------------------------------------------------------------------------
@echo off
setlocal

@REM Ưu tiên JAVA_HOME env var; fallback về JDK 21 đã cài
if not defined JAVA_HOME (
    set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
)
set JAVACMD=%JAVA_HOME%\bin\java.exe

if not exist "%JAVACMD%" (
    echo ERROR: Khong tim thay java.exe. Dat JAVA_HOME hoac them java vao PATH.
    exit /B 1
)

@REM Tìm Maven
set MVN_CMD=%USERPROFILE%\.m2\maven-3.9.9\bin\mvn.cmd

if not exist "%MVN_CMD%" (
    echo ERROR: Khong tim thay Maven tai %MVN_CMD%
    echo Chay lenh nay de tai Maven:
    echo   powershell -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile '$env:TEMP\maven.zip'; Expand-Archive '$env:TEMP\maven.zip' '$env:USERPROFILE\.m2' -Force"
    exit /B 1
)

call "%MVN_CMD%" %*
endlocal
