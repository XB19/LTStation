#!/usr/bin/env bash
# Démarre le backend en développement (arrête d'abord une instance déjà lancée sur le port 8080).
cd "$(dirname "$0")/backend"
PID=$(netstat -ano 2>/dev/null | grep ":8080 .*LISTEN" | awk '{print $NF}' | head -1)
[ -n "$PID" ] && taskkill //PID "$PID" //F >/dev/null 2>&1
export JAVA_HOME="${JAVA_HOME_OVERRIDE:-/c/Program Files/Java/jdk-17}"
exec ./mvnw -q spring-boot:run
