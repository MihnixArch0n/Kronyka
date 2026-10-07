FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S kronyka && adduser -S kronyka -G kronyka
USER kronyka

COPY --from=builder --chown=kronyka:kronyka /build/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
