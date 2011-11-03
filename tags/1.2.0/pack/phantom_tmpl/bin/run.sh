#!/bin/sh
#JAVA_HOME=/your/java/home
case "$0" in
/*) BIN_DIR=`dirname "$0"` ;;
*) BIN_DIR=`dirname "$PWD/$0"` ;;
esac

QUEUELET_HOME=${BIN_DIR}/..
PH_HOME=${QUEUELET_HOME}/ph

QUEUELET_BOOT_JAR=${QUEUELET_HOME}/bin/queuelet-boot-1.2.0.jar
PH_DEBUG=
PH_HEAPSIZE=256
PH_VM_OPTIONS=
PH_CONF_XML=phantom.xml
CONF_XML=phantomd.xml

if [ "$1" = "debug" ]
then
PH_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=1234"
fi

DEBUG_FLAG=
#DEBUG_FLAG=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=1234
${JAVA_HOME}/bin/java ${DEBUG_FLAG} -DPH_DEBUG="${PH_DEBUG}" -DPH_CONF_XML=${PH_CONF_XML}
-DPH_VM_OPTIONS="${PH_VM_OPTIONS}" -DPH_HEAPSIZE=${PH_HEAPSIZE} -DPH_HOME=${PH_HOME} -DQUE
UELET_HOME=${QUEUELET_HOME} -jar ${QUEUELET_BOOT_JAR} ${CONF_XML} $1 $2 $3 $4
