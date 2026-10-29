FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./mvnw && ./mvnw --batch-mode --no-transfer-progress -pl backend/api -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system shardvault \
    && useradd --system --gid shardvault --home-dir /app --create-home shardvault
WORKDIR /app
COPY --from=build --chown=shardvault:shardvault /workspace/backend/api/target/api-0.0.1-SNAPSHOT.jar /app/app.jar
USER shardvault
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
