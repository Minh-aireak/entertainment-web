# ---- 1. Build stage ----
FROM maven:4.0.0-rc-5-amazoncorretto-25-al2023 AS build

WORKDIR /app
#
#COPY pom.xml ./
#RUN mvn dependency:go-offline -B

COPY . .

# Build toàn bộ project
RUN mvn -B clean package -DskipTests

# ---- 2. Runtime stage ----
FROM amazoncorretto:24.0.1-alpine3.21

WORKDIR /app

COPY --from=build /app/notification-service/target/notification-service-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]