# syntax=docker/dockerfile:1
# Build with named contexts `java25` (JRE home) and `spmf` (directory containing spmf.jar); use scripts/staging/build-image.sh,
# which builds linux/amd64 by default. SPMF is GPL v3 and runs only as a separate process: the license, SOURCE.md and
# THIRD_PARTY_NOTICES.md are copied to /opt/paspm/licenses/ next to the jar (DEC-20261125-01).
# The Backend JAR is built separately with Java 17: mvn -DskipTests package
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${RUNTIME_IMAGE}

USER root
RUN if ! command -v python3 >/dev/null 2>&1; then apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*; fi \
    && python3 -I -c 'import ctypes' \
    && groupadd --system --gid 10001 paspm \
    && useradd --system --uid 10001 --gid 10001 --home-dir /var/lib/paspm --create-home --shell /usr/sbin/nologin paspm \
    && mkdir -p /var/lib/paspm/media/avatars && chown -R 10001:10001 /var/lib/paspm/media
WORKDIR /opt/paspm
COPY --from=java25 / /opt/paspm/java25/
COPY --from=spmf /spmf.jar /opt/paspm/spmf.jar
COPY third_party/spmf/ /opt/paspm/licenses/spmf/
COPY THIRD_PARTY_NOTICES.md /opt/paspm/licenses/THIRD_PARTY_NOTICES.md
COPY target/paspm-backend-0.1.0.jar /opt/paspm/backend.jar
RUN echo '4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a  /opt/paspm/spmf.jar' | sha256sum -c - \
    && chmod -R a+rX /opt/paspm \
    && /opt/paspm/java25/bin/java -version
ENV PASPM_MINING_SPMF_JAR=/opt/paspm/spmf.jar \
    PASPM_MINING_JAVA_EXECUTABLE=/opt/paspm/java25/bin/java \
    PASPM_MEDIA_ROOT=/var/lib/paspm/media \
    PROFILE_IMAGE_DIRECTORY=/var/lib/paspm/media/avatars
# DEC-20261123-01: Store images (PASPM_MEDIA_ROOT) and avatars (PROFILE_IMAGE_DIRECTORY) both live under
# /var/lib/paspm/media. Mount a named volume there (compose.backend.yml: paspm_media) so they survive re-creation.
LABEL paspm.third-party.spmf.version="2.67a" \
      paspm.third-party.spmf.license="GPL-3.0" \
      paspm.third-party.spmf.sha256="4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a" \
      paspm.third-party.spmf.source="https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/releases/tag/spmf-2.67a" \
      paspm.third-party.spmf.usage="unmodified, run as a separate process; see /opt/paspm/licenses"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/paspm/backend.jar"]
