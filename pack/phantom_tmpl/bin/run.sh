#!/bin/sh
JAVA_HOME=/your/java/home
QUEUELET_HOME=/your/install/phantom
PH_HOME=${QUEUELET_HOME}/ph
DEBUG_FLAG=
#DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=1234
${JAVA_HOME}/bin/java ${DEBUG_FLAG} -DPH_HOME=${PH_HOME} -DQUEUELET_HOME=${QUEUELET_HOME} -jar ${QUEUELET_HOME}/bin/queuelet-boot-1.2.0.jar phantomd.xml $1 $2 $3 $4
