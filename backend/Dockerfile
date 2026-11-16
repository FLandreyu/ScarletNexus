FROM maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /build
COPY pom.xml .
COPY .mvn ./.mvn
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
RUN addgroup -S nexus && adduser -S -G nexus nexus
RUN mkdir -p /var/lib/scarlet/attachments && chown -R nexus:nexus /var/lib/scarlet
ENV APP_ATTACHMENTS_DIRECTORY=/var/lib/scarlet/attachments
WORKDIR /app
COPY --from=build /build/target/scarlet-nexus-0.1.0-SNAPSHOT.jar app.jar
USER nexus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
