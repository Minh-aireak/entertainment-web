# ---- 1. Build stage ----
FROM eclipse-temurin:24-jdk AS build

# Cài đặt Maven
RUN apt-get update && apt-get install -y maven

WORKDIR /app

# Copy toàn bộ project
COPY . .

# Build chỉ module film_service
RUN mvn -B clean package -Dmaven.test.skip=true -pl film_service -am

# ---- 2. Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Copy jar của module film_service
COPY --from=build /app/film_service/target/film_service-*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
