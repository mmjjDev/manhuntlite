#!/bin/bash
set -e

export JAVA_HOME=/home/user/tools/jdk-21.0.10+7
export PATH=$JAVA_HOME/bin:$PATH

echo "Java version:"
java -version 2>&1

echo "=== Download Gradle 9.0 ==="
cd /tmp
wget -q "https://services.gradle.org/distributions/gradle-9.0-bin.zip" -O gradle-9.0-bin.zip
unzip -qo gradle-9.0-bin.zip
export GRADLE_HOME=/tmp/gradle-9.0
export PATH=$GRADLE_HOME/bin:$PATH

echo "Gradle version:"
gradle --version 2>&1 | head -5

echo "=== Generate wrapper ==="
cd /home/user/files/code/manhuntlite
gradle wrapper --gradle-version 9.0 2>&1
chmod +x gradlew
ls -la gradlew

echo "=== Running build ==="
./gradlew build --no-daemon --stacktrace 2>&1

echo "=== Build output ==="
ls -la build/libs/ 2>/dev/null || echo "No build/libs"

echo "=== DONE ==="
