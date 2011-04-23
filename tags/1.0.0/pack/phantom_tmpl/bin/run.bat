echo off
set JAVA_HOME=X:/your/java/home
set QUEUELET_HOME=X:/your/install/phantom
set PH_HOME=%QUEUELET_HOME%/ph
set DEBUG_FLAG=
rem set DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="1234"
"%JAVA_HOME%\bin\java" %DEBUG_FLAG% -Xms128m -Xmx128m -DQUEUELET_HOME=%QUEUELET_HOME%  -DPH_HOME=%PH_HOME% -jar %QUEUELET_HOME%\bin\queuelet-boot-1.1.0.jar phantom.xml %1 %2 %3 %4

