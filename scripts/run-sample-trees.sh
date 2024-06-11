#!/bin/bash

bin_dir=$(dirname "$0")         # may be relative path
bin_dir=$(cd "$bin_dir" && pwd)

root=$bin_dir/../target

jars=$(ls $root/dependency/* | tr '\n' ':')

jars=$jars:$root/SplitsTree-1.0.0-SNAPSHOT.jar

#echo java -cp $jars splitstree6.tools.SampleTrees $*
java -cp $jars splitstree6.tools.SampleTrees $*
