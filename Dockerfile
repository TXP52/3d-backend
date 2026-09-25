# ============================================================
# Đóng gói backend để chạy trên máy chủ thuê (Render, Railway, Fly.io...).
#
# Vì sao cần Docker: Render không có sẵn môi trường Java (chỉ Node, Python, Ruby,
# Go và Docker), nên phải tự mô tả cách dựng và cách chạy.
#
# Hai tầng: tầng "dung" có Maven + JDK để biên dịch (nặng ~800MB), tầng cuối chỉ
# có JRE và một file jar (~200MB). Máy chủ chỉ tải tầng cuối nên khởi động nhanh.
# ============================================================

# ---------- Tầng 1: biên dịch ----------
FROM maven:3.9-eclipse-temurin-17 AS dung
WORKDIR /nguon

# Chép riêng pom.xml trước rồi tải thư viện: lần sau chỉ sửa code mà không đổi
# pom thì Docker dùng lại lớp này, khỏi tải lại vài trăm MB thư viện.
# "|| true": bước tải trước này chỉ để chạy nhanh hơn, thỉnh thoảng Maven vướng
# một thư viện phụ của plugin. Hỏng ở đây thì kệ, bước package bên dưới tải nốt.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Tầng 2: chạy ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=dung /nguon/target/*.jar app.jar

# Gói miễn phí của Render chỉ có 512MB RAM. MaxRAMPercentage bảo máy ảo Java tự
# đo RAM của container rồi lấy 70% làm vùng nhớ — khỏi phải sửa số khi đổi gói.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

# Chỉ để ghi chú; cổng thật lấy từ biến môi trường PORT (xem application.properties)
EXPOSE 8090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
