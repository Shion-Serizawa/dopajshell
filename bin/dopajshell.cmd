@echo off
rem dopajshell launcher (Windows). Uses JAVA_HOME (set by mise in this repo) or java on PATH.
setlocal
set "JAR=%~dp0..\build\libs\dopajshell-all.jar"
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" --enable-native-access=ALL-UNNAMED -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%JAR%" %*
