# ---- 1. Build stage ----
FROM maven:4.0.0-rc-5-amazoncorretto-25-al2023 AS build

WORKDIR /app

# Đây là quan trọng: copy **toàn bộ project**
COPY . .

# Build toàn bộ project
RUN mvn -B clean package -DskipTests

# ---- 2. Runtime stage ----
FROM amazoncorretto:24.0.1-alpine3.21

WORKDIR /app

# Copy jar của module identity-service
COPY --from=build /app/identity-service/target/identity-service-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

# Do chạy trên localhost của 1 container là local host của chính container đó nên phải thay localhost thanh network server cua container mysql
# docker run --name identity_service -p 8080:8080 -e DBMS_CONNECTION=jdbc:mysql://172.17.0.2:3306/identity_service identity_service:0.0.1

# Docker có hỗ trợ truy cập vào server giống cơ chế DNS thay vì gọi tới 1 IP thì sẽ gọi tới tên của container đó:
# Đây là tạo 1 DBMS mới luôn sử dụng image sql:
# B1: tạo network:
# docker network create aireak-network
# kiểm tra network đã tạo chưa: docker network ls
# B2: chạy MySql trên network
# docker run --network aireak-network --name <Create name container> -p 3306:3306 -e MYSQL_ROOT_PASSWORD=REDACTED_LEGACY_CREDENTIAL -e MYSQL_DATABASE=<TÊN CSDL> -d <Name Image of image sql>
# docker run --name identity_service --network aireak-network -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:mysql://MySQL:3306/identity_service -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=REDACTED_LEGACY_CREDENTIAL identity_service:0.0.1