#!/bin/bash
root=/Users/huson/IdeaProjects/community/splitstree6/target

jars=$(ls $root/dependency/* | tr '\n' ':')

jars=$jars:$root/SplitsTree-1.0.0-SNAPSHOT.jar

echo java -cp $jars splitstree6.tools.SampleTrees $*
java -cp $jars splitstree6.tools.SampleTrees $*
