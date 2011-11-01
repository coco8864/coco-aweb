echo off

rem set JAVA_HOME=X:/your/java/home

set QUEUELET_HOME=%~dp0..
set PH_HOME=%~dp0..\ph
set QUEUELET_BOOT_JAR=%QUEUELET_HOME%\bin\queuelet-boot-1.2.0.jar
set PH_HEAPSIZE=128
set CONF_XML=phantomd.xml
set PH_CONF_XML=phantom.xml
set PH_XDEBUG=
set PH_XRUNJDWP=

IF NOT "%1"=="debug" goto NEXT
set PH_XDEBUG=-Xdebug
set PH_XRUNJDWP=-Xrunjdwp:transport=dt_socket,server=y,address="1234"
shift

:NEXT
set DEBUG_FLAG=
rem set DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="1234"

"%JAVA_HOME%\bin\java" %DEBUG_FLAG% -Xmx16m -DPH_HEAPSIZE=%PH_HEAPSIZE% -DPH_XDEBUG=%PH_XDEBUG% -DPH_XRUNJDWP=%PH_XRUNJDWP% -DQUEUELET_HOME=%QUEUELET_HOME% -DPH_CONF_XML=%PH_CONF_XML% -DPH_HOME=%PH_HOME% -jar %QUEUELET_BOOT_JAR% %CONF_XML% %1 %2 %3 %4

