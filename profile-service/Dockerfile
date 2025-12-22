# ---- 1. Build stage ----
FROM maven:4.0.0-rc-5-amazoncorretto-25-al2023 AS build

WORKDIR /app

COPY . .

# Build toàn bộ project
RUN mvn -B clean package -DskipTests

# ---- 2. Runtime stage ----
FROM amazoncorretto:24.0.1-alpine3.21

WORKDIR /app

COPY --from=build /app/profile-service/target/profile-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]