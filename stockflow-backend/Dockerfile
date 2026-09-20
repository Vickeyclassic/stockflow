FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S stockflow && adduser -S stockflow -G stockflow
WORKDIR /app
COPY --from=build --chown=stockflow:stockflow /build/target/stockflow-backend-*.jar app.jar
USER stockflow
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health/ready || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
