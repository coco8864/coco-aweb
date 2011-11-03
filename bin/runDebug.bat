echo on
rem set JAVA_HOME=${yourJavaHome}
set QUEUELET_HOME=%~dp0..\pack\phantom
set PH_HOME="%QUEUELET_HOME%\ph"
set QUEUELET_BOOT_JAR=%QUEUELET_HOME%\bin\queuelet-boot-1.2.0.jar
rem set QUEUELET_BOOT_JAR=%QUEUELET_HOME%\bin\queuelet-boot.jar
set VMOPTION_LENGTH=1
IF NOT "%1"=="debug" goto NEXT
rem VMOPTIONを3つにすると、phantom起動時に-Xdebugオプションをつける
set VMOPTION_LENGTH=3
shift
:NEXT
set DEBUG_FLAG=
rem set DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="1234"
"%JAVA_HOME%\bin\java" %DEBUG_FLAG% -Xmx16m -DQUEUELET_HOME=%QUEUELET_HOME% -DVMOPTION_LENGTH=%VMOPTION_LENGTH% -DPH_HOME=%PH_HOME% -jar %QUEUELET_BOOT_JAR% phantomd.xml %1 %2 %3 %4

