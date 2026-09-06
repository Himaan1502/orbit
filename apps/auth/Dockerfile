# Multi-stage build for the ORBIT auth server (self-hosted OIDC — see ADR-004)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src src
COPY checkstyle checkstyle
COPY spotbugs spotbugs
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S orbit && adduser -S orbit -G orbit
COPY --from=build /app/build/libs/*.jar app.jar
USER orbit
EXPOSE 9000
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:9000/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
