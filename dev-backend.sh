#!/usr/bin/env bash
# Démarre le backend en développement (arrête d'abord une instance déjà lancée sur le port 8080).
cd "$(dirname "$0")/backend"
# Local address en 2e colonne : le libellé LISTENING dépend de la langue de Windows
PID=$(netstat -ano 2>/dev/null | awk '$2 ~ /:8080$/ && $3 ~ /:0$/ {print $NF}' | head -1)
[ -n "$PID" ] && taskkill //PID "$PID" //F >/dev/null 2>&1
export JAVA_HOME="${JAVA_HOME_OVERRIDE:-/c/Program Files/Java/jdk-17}"
exec ./mvnw -q spring-boot:run
