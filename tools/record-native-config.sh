#!/bin/sh
# Records the reachability metadata (reflection, JNI, resources) the native image of fsh-ide needs,
# by running the real IDE on a JVM under GraalVM's tracing agent. Run it on each platform the native
# image is built for (the AWT/Swing metadata is platform specific: the Windows entries are already in
# the file; Linux and macOS add theirs), then commit the updated file.
#
#   tools/record-native-config.sh
#
# Needs a GraalVM JDK (java -version says GraalVM) as the JAVA_HOME or on PATH, and Maven.
# The agent merges into the existing file, so it only ever adds. Use the IDE the way you would:
# open and save a script, autocomplete, run and debug a script, templates, the syntax-tree view, the
# file chooser, the dialogs. Whatever is not used is not recorded, and fails in the native image
# (reflection/JNI "not found"). Close the IDE normally when done: the file is written on exit.

set -e
project=$(cd "$(dirname "$0")/.." && pwd)
cd "$project"

out=$project/resources/META-INF/native-image/us.bringardner/fsh-ide
mkdir -p "$out"

mvn -B -q compile
mvn -B -q dependency:build-classpath -Dmdep.outputFile=target/record-classpath.txt
cp=$(cat target/record-classpath.txt)
case $(uname -s) in
MINGW*|MSYS*|CYGWIN*) sep=';' ;;
*) sep=':' ;;
esac

java -agentlib:native-image-agent=config-merge-dir="$out" \
	-cp "target/classes$sep$cp" us.bringardner.fsh.ide.FshIDE

echo "Recorded into $out"
