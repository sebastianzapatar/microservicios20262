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
EXPOSE 8761
# XStream (dependencia interna de Eureka Server) usa sun.misc.Unsafe, que está
# marcado como "terminally deprecated" desde JDK 24 y avisa en cada arranque.
# El flag silencia el aviso; es cosa de XStream, no de nuestro código.
ENTRYPOINT ["java", "--sun-misc-unsafe-memory-access=allow", "-jar", "app.jar"]
