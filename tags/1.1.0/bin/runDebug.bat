echo off
set JAVA_HOME=C:\jdk1.6.0_22
set QUEUELET_HOME=E:\svn\coco\queuelet\home
set PH_HOME=E:\svn\coco\aweb\ph
rem set DEBUG_FLAG=
set DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="1234"
"%JAVA_HOME%\bin\java" %DEBUG_FLAG% -Xms128m -Xmx128m -DQUEUELET_HOME=%QUEUELET_HOME%  -DPH_HOME=%PH_HOME% -jar %QUEUELET_HOME%\bin\queuelet-boot-1.1.0.jar %PH_HOME%\phantom.xml %1 %2 %3 %4

