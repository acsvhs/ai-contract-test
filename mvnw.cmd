@echo off
setlocal
set "PROJECT_DIR=%~dp0"
set "MAVEN_VERSION=3.9.11"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\ai-contract-test\apache-maven-%MAVEN_VERSION%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $cache=Join-Path $env:USERPROFILE '.m2\wrapper\dists\ai-contract-test'; [void](New-Item -ItemType Directory -Force -Path $cache); $zip=Join-Path $cache 'apache-maven-3.9.11-bin.zip'; Invoke-WebRequest -UseBasicParsing -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath $cache -Force; Remove-Item -LiteralPath $zip"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
