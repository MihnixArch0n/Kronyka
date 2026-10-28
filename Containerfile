# Stage 1: Build & Layer Extraction
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test
RUN cp build/libs/*.jar application.jar && \
    java -Djarmode=tools -jar application.jar extract --layers --application-filename application.jar --destination extracted

# Stage 2: Runtime Container with Java 25 AOT Cache
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

RUN apk add --no-cache curl \
    && addgroup -S kronyka && adduser -S kronyka -G kronyka

COPY --from=builder --chown=kronyka:kronyka /build/extracted/dependencies/ ./
COPY --from=builder --chown=kronyka:kronyka /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=kronyka:kronyka /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=kronyka:kronyka /build/extracted/application/ ./

# Execute AOT Cache training run in the runtime container environment
RUN java -XX:AOTCacheOutput=app.aot \
    -Dspring.context.exit=onRefresh \
    -Dspring.datasource.url=jdbc:h2:mem:aotdb \
    -Dspring.datasource.driver-class-name=org.h2.Driver \
    -jar application.jar \
    && chown kronyka:kronyka app.aot

USER kronyka

ENV SERVER_PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "application.jar"]
