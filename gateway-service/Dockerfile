FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
RUN chmod +x gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8090
# Netty (motor de Spring Cloud Gateway) carga librerías nativas. Desde JDK 24
# eso exige permiso explícito o se emite un aviso en cada arranque.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
