# ---- 1. Build stage ----
FROM eclipse-temurin:24-jdk AS build

# Cài đặt Maven
RUN apt-get update && apt-get install -y maven

WORKDIR /app

# Copy toàn bộ project
COPY . .

# Build chỉ module friend-service
RUN mvn -B clean package -Dmaven.test.skip=true -pl friend-service -am

# ---- 2. Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Copy jar của module friend-service
COPY --from=build /app/friend-service/target/friend-service-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
