# LTStation : site Center + logiciel Station (navigateur) dans une seule image.
# Les secrets (.env) et les réglages de la station (backend/station) ne sont jamais copiés dans l'image :
# docker-compose.yml les fournit au démarrage.

# 1. Interface (React) : construite dans backend/src/main/resources/static
FROM node:20-alpine AS front
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN mkdir -p ../backend/src/main/resources && npm run build

# 2. Serveur (Spring Boot)
FROM maven:3.9-eclipse-temurin-17 AS back
WORKDIR /src/backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=front /src/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -q -B package -Dmaven.test.skip=true

# 3. Image d'exécution
FROM eclipse-temurin:17-jre
# Dossier de travail : station/ (réglages, modèles RDLC, impressions, exports) et logs/
WORKDIR /app/data
COPY --from=back /src/backend/target/center-1.0.0.jar /app/center.jar
ENV SERVER_PORT=8080
EXPOSE 8080
VOLUME /app/data
ENTRYPOINT ["sh", "-c", "exec java -Dfile.encoding=UTF-8 -Duser.timezone=${TZ:-UTC} -jar /app/center.jar"]
