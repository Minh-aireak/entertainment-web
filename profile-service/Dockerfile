# ---- 1. Build stage ----
FROM eclipse-temurin:24-jdk AS build

RUN apt-get update && apt-get install -y maven

WORKDIR /app

COPY . .

# Build chỉ module profile-service
RUN mvn -B clean package -Dmaven.test.skip=true -pl profile-service -am

# ---- 2. Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

COPY --from=build /app/profile-service/target/profile-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
