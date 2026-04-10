# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS builder

ARG MODULE
WORKDIR /workspace

# Copy all backend sources once so the selected module can be built.
COPY . .

RUN if [ -z "$MODULE" ]; then echo "MODULE build arg is required"; exit 1; fi
RUN mvn -pl ${MODULE} -am clean package -DskipTests

RUN JAR_PATH="$(find ${MODULE}/target -maxdepth 1 -name '*.jar' ! -name '*original*' | head -n 1)" \
    && if [ -z "$JAR_PATH" ]; then echo "No runnable jar found for module ${MODULE}"; exit 1; fi \
    && cp "$JAR_PATH" /tmp/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=builder /tmp/app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
