#!/bin/sh
JAVA_HOME=/your/java/home
QUEUELET_HOME=/your/install/phantom
PH_HOME=${QUEUELET_HOME}/ph
DEBUG_FLAG=
#DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="1234"
${JAVA_HOME}/bin/java ${DEBUG_FLAG} -Xms128m -Xmx128m -DPH_HOME=${PH_HOME} -DQUEUELET_HOME=${QUEUELET_HOME} -jar ${QUEUELET_HOME}/bin/queuelet-boot-1.1.0.jar phantom.xml $1 $2 $3 $4
