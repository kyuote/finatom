FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/payment-service-1.0-SNAPSHOT-all.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
