FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/account-service-1.0-SNAPSHOT-all.jar app.jar
EXPOSE 8081 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
