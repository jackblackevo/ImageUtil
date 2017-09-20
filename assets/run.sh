#!/bin/bash
declare dependencies=".:ImageUtil-1.0-SNAPSHOT.jar"

eval "java -version"

eval "java -classpath $dependencies idv.jackblackevo.App"
