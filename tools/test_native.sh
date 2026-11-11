#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK with JNI headers}"
mkdir -p analysis/tests
${CXX:-g++} -std=c++17 -Wall -Wextra -Werror -pedantic \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -Irecovered/native \
  recovered/native/jvm_attachment.cpp tests/test_jvm_attachment.cpp \
  -o analysis/tests/test_jvm_attachment
analysis/tests/test_jvm_attachment
printf 'JNI attachment reconstruction: tests pass (mock JVM only).\n'
