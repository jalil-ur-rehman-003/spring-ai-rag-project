# Multi-stage build: compile with the full Maven+JDK image, then ship only the
# built jar on a slim JRE so the runtime image doesn't carry build tooling.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
# Pre-fetch dependencies as a separate layer so code-only changes don't
# re-download the world on every rebuild.
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
