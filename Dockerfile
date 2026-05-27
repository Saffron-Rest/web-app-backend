# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /data/uploads \
    && chown -R app:app /data
USER app
COPY --from=build /app/target/storefront-api-*.jar /app/app.jar
EXPOSE 3002
# Persistent storage for admin-uploaded product images. The production
# docker-compose mounts a volume here so files survive container redeploys.
VOLUME ["/data/uploads"]
ENV STOREFRONT_UPLOADS_DIR=/data/uploads
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
