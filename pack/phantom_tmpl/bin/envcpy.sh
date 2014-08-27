#!/bin/sh
cp -f $1/bin/run.sh ../bin/run.sh
chmod +x ../bin/run.sh
cp -f $1/ph/setting/ph.env.properties ../ph/setting/ph.env.properties
cp -rf $1/ph/security/* ../ph/security/
cp -rf $1/ph/store/* ../ph/store/
cp -rf $1/ph/db/* ../ph/db/

