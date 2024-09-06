@echo off
set APP_HOME=%cd%

set "_cd=%cd%"

:loop
set "_cd=%_cd:*\=%"
set "cd_tmp=%_cd:\=%"
if not "%cd_tmp%" == "%_cd%" goto loop

%APP_HOME%\prunsrv //IS//%_cd% ^
--DisplayName="ReverseProxy" ^
--Install=%APP_HOME%\\prunsrv.exe ^
--Jvm=auto ^
--JvmOptions=-Djava.awt.headless=true ^
--Classpath=%APP_HOME%\\lib\*.jar ^
--PidFile=%APP_HOME%\\logs\rp_run ^
--StdOutput=%APP_HOME%\\logs\rp.out ^
--StdError=%APP_HOME%\\logs\rp.err ^
--StartMode=jvm ^
--StopMode=jvm ^
--StartClass=cn.banny.rp.client.ReverseProxyProcrun ^
--StartParams=start ^
--StopClass=cn.banny.rp.client.ReverseProxyProcrun ^
--StopParams=stop
