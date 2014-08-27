echo off

rem set JAVA_HOME=X:/your/java/home

set QUEUELET_HOME=%~dp0..
set PH_HOME=%~dp0..\ph
set QUEUELET_BOOT_JAR=%QUEUELET_HOME%\bin\queuelet-boot-1.2.0.jar
set PH_DEBUG=
set PH_HEAPSIZE=128
set PH_VM_OPTIONS=
set CONF_XML=phantomd.xml
set PH_CONF_XML=phantom.xml

IF NOT "%1"=="debug" goto NEXT1
set PH_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=1234"
shift

:NEXT1
IF NOT "%1"=="debugSuspend" goto NEXT2
set PH_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=1234"
shift

:NEXT2
set DEBUG_FLAG=
rem set DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="1234"

"%JAVA_HOME%\bin\java" %DEBUG_FLAG% -Xmx16m -DPH_HEAPSIZE=%PH_HEAPSIZE% -DPH_DEBUG=%PH_DEBUG% -DPH_VM_OPTIONS=%PH_VM_OPTIONS% -DQUEUELET_HOME=%QUEUELET_HOME% -DPH_CONF_XML=%PH_CONF_XML% -DPH_HOME=%PH_HOME% -jar %QUEUELET_BOOT_JAR% %CONF_XML% %1 %2 %3 %4

