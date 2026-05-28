# ---- 1. Build stage ----
FROM eclipse-temurin:24-jdk AS build

RUN apt-get update && apt-get install -y maven

WORKDIR /app

# Copy toàn bộ project
COPY . .

# Chỉ build module debezium-manager và các dependency (như common)
RUN mvn -B clean package -Dmaven.test.skip=true -pl debezium-manager -am

# ---- 2. Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Copy jar của module debezium-manager
COPY --from=build /app/debezium-manager/target/debezium-manager-*.jar app.jar

EXPOSE 9000

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
