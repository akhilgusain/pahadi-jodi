# syntax=docker/dockerfile:1.9
# ---- 1. build (layered jar, cached Maven repo)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY ../../pom.xml /parent/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination /layers

# ---- 2. minimal JRE via jlink
FROM eclipse-temurin:21-jdk AS jre
RUN jlink --add-modules java.base,java.logging,java.sql,java.naming,java.management,java.instrument,java.desktop,java.security.jgss,java.net.http,jdk.unsupported,jdk.crypto.ec,jdk.zipfs,java.xml,java.compiler,jdk.jfr \
    --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output /jre

# ---- 3. AppCDS archive for ~40% faster startup
FROM gcr.io/distroless/base-debian12:nonroot AS cds-base
FROM debian:12-slim AS cds
COPY --from=jre /jre /jre
COPY --from=build /layers/dependencies/ /app/
COPY --from=build /layers/spring-boot-loader/ /app/
COPY --from=build /layers/snapshot-dependencies/ /app/
COPY --from=build /layers/application/ /app/
WORKDIR /app
RUN /jre/bin/java -XX:ArchiveClassesAtExit=/app/app.jsa -Dspring.context.exit=onRefresh -Dspring.profiles.active=cds org.springframework.boot.loader.launch.JarLauncher || true

# ---- 4. runtime: distroless, non-root, read-only fs friendly
FROM gcr.io/distroless/base-debian12:nonroot
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.source="https://github.com/pahadi-jodi/platform" org.opencontainers.image.revision=$GIT_SHA
COPY --from=jre /jre /jre
COPY --from=cds /app /app
WORKDIR /app
USER 65532:65532
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:SharedArchiveFile=/app/app.jsa -Xshare:auto"
ENTRYPOINT ["/jre/bin/java","org.springframework.boot.loader.launch.JarLauncher"]
