FROM node:22-alpine AS frontend
WORKDIR /fe
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run generate

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /be
COPY backend/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /fe/.output/public ./src/main/resources/webroot
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /be/target/dvlogger.jar ./dvlogger.jar
EXPOSE 8080 11222/tcp 11222/udp
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/dvlogger.jar"]
