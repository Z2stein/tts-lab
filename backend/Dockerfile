FROM gradle:8.8-jdk21-alpine AS build
WORKDIR /app
COPY backend/build.gradle backend/settings.gradle ./
COPY backend/src ./src
COPY shared ./shared
COPY test-contracts ./test-contracts
RUN gradle --no-daemon clean test bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
