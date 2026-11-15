FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY .mvn ./.mvn
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nexus && adduser -S -G nexus nexus
RUN mkdir -p /var/lib/scarlet/attachments && chown -R nexus:nexus /var/lib/scarlet
ENV APP_ATTACHMENTS_DIRECTORY=/var/lib/scarlet/attachments
WORKDIR /app
COPY --from=build /build/target/scarlet-nexus-0.1.0-SNAPSHOT.jar app.jar
USER nexus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
