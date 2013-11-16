echo on
rem set JAVA_HOME=${yourJavaHome}

set PROJ=%~dp0..

set QUEUELET_HOME=%PROJ%\pack\phantom
set PH_HOME=%PROJ%\ph
set PACK_PH_HOME=%QUEUELET_HOME%\ph
set QUEUELET_BOOT_JAR=%QUEUELET_HOME%\bin\queuelet-boot-1.2.0.jar
set CONF_XML=%PROJ%\conf\phdebugd.xml
set PH_CONF_XML=%PROJ%\conf\phdebug.xml
set PH_DEBUG=
set PH_HEAPSIZE=128
set PH_VM_OPTIONS=
set AWEB_CLASSES=%PROJ%\target\classes
set PH_LIB=%PROJ%\pack\phantom\ph\lib
set ASYNC_CLASSES=%PROJ%\..\async\target\classes

IF NOT "%1"=="debug" goto NEXT1
set PH_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=1234"
shift

:NEXT1
IF NOT "%1"=="debugSuspend" goto NEXT2
set PH_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=1234"
shift

:NEXT2
set DEBUG_FLAG=
rem set DEBUG_FLAG="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=1234"
"%JAVA_HOME%\bin\java" %DEBUG_FLAG% -Xmx16m -DASYNC_CLASSES=%ASYNC_CLASSES% -DPH_LIB=%PH_LIB% -DAWEB_CLASSES=%AWEB_CLASSES% -DPH_HEAPSIZE=%PH_HEAPSIZE% -DPH_DEBUG=%PH_DEBUG% -DPH_VM_OPTIONS=%PH_VM_OPTIONS% -DQUEUELET_HOME=%QUEUELET_HOME% -DPH_CONF_XML=%PH_CONF_XML% -DPH_HOME=%PH_HOME% -jar %QUEUELET_BOOT_JAR% %CONF_XML% %1 %2 %3 %4

