# One image recipe for every Spring service: docker build -f deploy/Dockerfile --build-arg SERVICE=trip-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
ARG SERVICE
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl src/services/${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG SERVICE
COPY --from=build /src/src/services/${SERVICE}/target/${SERVICE}.jar /app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC"
ENTRYPOINT ["java", "-jar", "/app.jar"]
