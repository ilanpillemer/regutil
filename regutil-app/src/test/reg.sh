#!/bin/bash

echo EasyReg to Game On!

java -cp ../../build/libs/regutil-app.jar org.gameontext.util.reg.RegistrationUtility $@

echo "System exit code : $?"
