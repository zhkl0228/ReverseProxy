@echo off
set APP_HOME=%cd%

set "_cd=%cd%"

:loop
set "_cd=%_cd:*\=%"
set "cd_tmp=%_cd:\=%"
if not "%cd_tmp%" == "%_cd%" goto loop

%APP_HOME%\prunsrv //DS//%_cd%
