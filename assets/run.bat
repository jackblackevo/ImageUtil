@echo off
set dependencies=".;ImageUtil-1.0-SNAPSHOT.jar"

@echo on
java -version

@echo off
java -classpath %dependencies% idv.jackblackevo.App

pause
