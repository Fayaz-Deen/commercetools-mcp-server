# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system mcp && useradd --system --gid mcp --no-create-home mcp
COPY --from=build /src/target/commercetools-mcp-server.jar /app/app.jar
USER mcp
# stdio transport by default: run with `docker run -i --rm --env-file .env ...`
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-jar", "/app/app.jar"]
