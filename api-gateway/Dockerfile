# ---- 1. Build stage ----
FROM eclipse-temurin:24-jdk AS build

RUN apt-get update && apt-get install -y maven

WORKDIR /app

COPY . .

# Build chỉ module api-gateway
RUN mvn -B clean package -Dmaven.test.skip=true -pl api-gateway -am

# ---- 2. Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

COPY --from=build /app/api-gateway/target/api-gateway-*.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]