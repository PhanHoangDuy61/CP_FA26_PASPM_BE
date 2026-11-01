# syntax=docker/dockerfile:1
# Build with named contexts `java25` (JRE home) and `spmf` (directory containing spmf.jar).
# The Backend JAR is built separately with Java 17: mvn -DskipTests package
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${RUNTIME_IMAGE}

USER root
RUN if ! command -v python3 >/dev/null 2>&1; then apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*; fi \
    && python3 -I -c 'import ctypes' \
    && groupadd --system --gid 10001 paspm \
    && useradd --system --uid 10001 --gid 10001 --home-dir /var/lib/paspm --create-home --shell /usr/sbin/nologin paspm
WORKDIR /opt/paspm
COPY --from=java25 / /opt/paspm/java25/
COPY --from=spmf /spmf.jar /opt/paspm/spmf.jar
COPY target/paspm-backend-0.1.0.jar /opt/paspm/backend.jar
RUN echo '4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a  /opt/paspm/spmf.jar' | sha256sum -c - \
    && chmod -R a+rX /opt/paspm \
    && /opt/paspm/java25/bin/java -version
ENV PASPM_MINING_SPMF_JAR=/opt/paspm/spmf.jar \
    PASPM_MINING_JAVA_EXECUTABLE=/opt/paspm/java25/bin/java \
    PROFILE_IMAGE_DIRECTORY=/var/lib/paspm/profile-images
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/paspm/backend.jar"]
