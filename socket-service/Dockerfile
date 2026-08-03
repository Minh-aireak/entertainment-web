# ---- 1. Build stage ----
FROM eclipse-temurin:24-jdk AS build

RUN apt-get update && apt-get install -y maven

WORKDIR /app

COPY . .

# Build chỉ module socket-service
RUN mvn -B clean package -Dmaven.test.skip=true -pl socket-service -am

# ---- 2. Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

COPY --from=build /app/socket-service/target/socket-service-*.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]