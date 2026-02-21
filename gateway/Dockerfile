# --- Сборка --
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)


# --- Запуск ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG DEP=/app/target/dependency
COPY --from=build ${DEP}/BOOT-INF/lib /app/lib
COPY --from=build ${DEP}/META-INF /app/META-INF
COPY --from=build ${DEP}/BOOT-INF/classes /app

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-cp", "/app:/app/lib/*", \
            "ru.petrov.gateway.GatewayApplication"]
