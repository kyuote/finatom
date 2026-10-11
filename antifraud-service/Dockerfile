FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/antifraud-service-1.0-SNAPSHOT-all.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
