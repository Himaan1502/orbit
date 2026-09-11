# Multi-stage build for the ORBIT auth server (self-hosted OIDC — see ADR-004).
#
# The `build` stage is pinned to the *build* platform (the CI runner, amd64) —
# Java bytecode is architecture-independent, so `bootJar` runs at native speed
# and never under QEMU emulation. The runtime stage resolves to the *target*
# platform (linux/arm64 for the OCI Ampere A1 host) and only copies the jar in.
# See ADR-035 §3.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src src
COPY checkstyle checkstyle
COPY spotbugs spotbugs
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.source="https://github.com/Himaan1502/orbit"
WORKDIR /app
RUN addgroup -S orbit && adduser -S orbit -G orbit
COPY --from=build /app/build/libs/*.jar app.jar
USER orbit
EXPOSE 9000
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:9000/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
