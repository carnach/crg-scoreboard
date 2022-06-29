
FROM ubuntu as builder
# download precomiled file and extract to temp directory
WORKDIR /app
COPY . .
RUN apt update && \
    apt install -y apt-utils openjdk-8-jdk ant git
RUN ant compile -noinput -buildfile build.xml

FROM openjdk:8u332-jre-slim-buster
# copy required files into base image
WORKDIR /app
COPY --from=builder /app .
EXPOSE 8000/tcp

ENTRYPOINT [ "java", "-Done-jar.silent=true", "-Dorg.eclipse.jetty.server.LEVEL=WARN", "-jar", "lib/crg-scoreboard.jar" ]