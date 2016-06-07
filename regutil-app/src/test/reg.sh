#!/bin/bash

echo EasyReg to Game On!

java -cp ../../build/libs/regutil-app.jar net.wasdev.gameon.util.RegistrationUtility $@

echo "System exit code : $?"

