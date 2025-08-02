# --- 1. Build stage: dùng Maven để đóng gói JDK: 24, Mvn: 3.9.10---
FROM maven:3.9.10-amazoncorretto-24 AS build

# Copy pom và source to /app folder
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Bạn có thể thêm ARG để bật tắt skip-tests
RUN mvn clean package -DskipTests

# docker build -t identity_service:0.0.1 .

# --- 2. Create image: Run stage: chỉ chạy JAR đã được build ---
FROM amazoncorretto:24.0.1-alpine3.21

# Copy JAR từ stage build, đổi tên thành app.jar (tuỳ chọn)
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose port (có thể override qua build-arg hoặc env)
EXPOSE 8080

# Cho phép override port runtime qua biến môi trường
ENV SERVER_PORT=8080

ENTRYPOINT ["java","-jar","app.jar"]

# Do chạy trên localhost của 1 container là local host của chính container đó nên phải thay localhost thanh network server cua container mysql
# docker run --name identity_service -p 8080:8080 -e DBMS_CONNECTION=jdbc:mysql://172.17.0.2:3306/identity_service identity_service:0.0.1

# Docker có hỗ trợ truy cập vào server giống cơ chế DNS thay vì gọi tới 1 IP thì sẽ gọi tới tên của container đó:
# Đây là tạo 1 DBMS mới luôn sử dụng image sql:
# B1: tạo network:
# docker network create aireak-network
# kiểm tra network đã tạo chưa: docker network ls
# B2: chạy MySql trên network
# docker run --network aireak-network --name <Create name container> -p 3306:3306 -e MYSQL_ROOT_PASSWORD=1801062012 -e MYSQL_DATABASE=<TÊN CSDL> -d <Name Image of image sql>
# docker run --name identity_service --network aireak-network -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:mysql://MySQL:3306/identity_service -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=1801062012 identity_service:0.0.1