@echo off
chcp 65001 > nul
call "%~dp0dist\Start-Server.cmd" %*
