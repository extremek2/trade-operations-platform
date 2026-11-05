FROM gradle:9.3.1-jdk17 AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN ./gradlew bootJar --no-daemon


FROM amazoncorretto:17

WORKDIR /app

COPY --from=builder /workspace/build/libs/api-server-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
