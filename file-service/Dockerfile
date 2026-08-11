# ---- 1. Build stage ----
FROM eclipse-temurin:24-jdk AS build

RUN apt-get update && apt-get install -y maven

WORKDIR /app

COPY . .

# Build chỉ module file-service
RUN mvn -B clean package -Dmaven.test.skip=true -pl file-service -am

# ---- 2. Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

# ffmpeg cho HlsTranscodeJob (remux MP4 -> segment .ts + manifest .m3u8, "-c copy" nên không encode
# lại - nhẹ CPU, phù hợp máy yếu).
RUN apk add --no-cache ffmpeg

WORKDIR /app

COPY --from=build /app/file-service/target/file-service-*.jar app.jar

EXPOSE 8084

# Hạ từ 75 -> 60: ffmpeg giờ chạy như 1 tiến trình OS song song trong cùng container, chiếm RAM
# ngoài heap JVM - 75% từng để lại quá ít margin, có thể khiến cả container bị OOM-kill dù JVM vẫn
# nằm trong ngân sách heap của chính nó.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
