# Imagen de la demo pública (perfil "demo": PostgreSQL y datos ficticios que se reinician cada hora).

# 1) Interfaz web
FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) Backend + interfaz en un jar ejecutable
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY core/ core/
COPY app/ app/
COPY desktop/build.gradle.kts desktop/
COPY --from=frontend /src/frontend/dist frontend/dist
RUN ./gradlew :app:bootJar -PfrontendCompilado --no-daemon --console=plain -x test

# 3) Imagen final mínima, usuario sin privilegios
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=backend /src/app/build/libs/tratohecho-servidor.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=demo JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
