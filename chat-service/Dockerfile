# ---- 1. Build stage ----
FROM eclipse-temurin:24-jdk AS build

RUN apt-get update && apt-get install -y maven

WORKDIR /app

COPY . .

# Build chỉ module chat-service
RUN mvn -B clean package -Dmaven.test.skip=true -pl chat-service -am

# ---- 2. Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

COPY --from=build /app/chat-service/target/chat-service-*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]