#!/bin/sh
#JAVA_HOME=/your/java/home
case "$0" in
/*) BIN_DIR=`dirname "$0"` ;;
*) BIN_DIR=`dirname "$PWD/$0"` ;;
esac

QUEUELET_HOME=${BIN_DIR}/../pack/phantom
PH_HOME=${QUEUELET_HOME}/ph
#QUEUELET_BOOT_JAR=${QUEUELET_HOME}/bin/queuelet-boot-1.2.0.jar
QUEUELET_BOOT_JAR=${QUEUELET_HOME}/bin/queuelet-boot.jar


DEBUG_FLAG=
#DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="1234"
${JAVA_HOME}/bin/java ${DEBUG_FLAG} -Xmx16m -DPH_HOME=${PH_HOME} -DQUEUELET_HOME=${QUEUELET_HOME} -jar ${QUEUELET_BOOT_JAR} phantom.xml $1 $2 $3 $4

