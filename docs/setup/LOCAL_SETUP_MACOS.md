# macOS local setup (zsh)

**Verification:** commands were checked against the repository and selected tools ran on an Apple Silicon macOS workstation; a complete fresh-clone walkthrough and Intel Mac run are **NOT VERIFIED ON TARGET OS**. Use only a new, disposable local database. See the [common reference](LOCAL_SETUP_COMMON.md) and [troubleshooting](LOCAL_SETUP_TROUBLESHOOTING.md).

1. Install Git, [Docker Desktop for Mac](https://docs.docker.com/desktop/setup/install/mac-install/) and separate JDK 17 and JDK 25 installations from a trusted distributor such as [Eclipse Temurin](https://adoptium.net/installation). Choose `aarch64/arm64` for Apple Silicon or `x64` for Intel. Open Docker Desktop and wait until `docker info` succeeds. No Homebrew path is assumed.

2. Clone or update, then inspect your working tree before switching branches:

   ```zsh
   git clone https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE.git
   cd CP_FA26_PASPM_BE
   git status --short --branch
   git fetch origin
   git switch dev
   git pull --ff-only origin dev
   ```

   Use the intended branch instead of `dev` if instructed. Uncommitted local changes require care; do not discard them. Changes not yet pushed by their author cannot be obtained by cloning.

3. Select JDK 17 for Maven/Backend and Java 25 for SPMF independently:

   ```zsh
   export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
   export PATH="$JAVA_HOME/bin:$PATH"
   export PASPM_MINING_JAVA_EXECUTABLE="$(/usr/libexec/java_home -v 25)/bin/java"
   "$JAVA_HOME/bin/java" -version
   "$JAVA_HOME/bin/javac" -version
   "$PASPM_MINING_JAVA_EXECUTABLE" -version
   sh ./mvnw -version
   ```

   If `java_home` cannot find a JDK, install/register it and retry. Verify architecture with `uname -m`; do not copy `/opt/homebrew` paths from a different Mac.

4. Create local Compose settings without overwriting existing settings, edit them and start services:

   ```zsh
   test -e .env || cp .env.example .env
   open -e .env
   docker compose config --services
   docker compose up -d mysql mailpit
   docker compose ps
   ```

   Set nonempty local-only `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` in `.env` before starting Compose. Never paste the file contents into chat or commit it. The database/user are both `paspm` and host port is 3306. Compose stores local data in a persistent volume; `docker compose down` does not delete it.

5. Export Backend variables in the **same terminal**. Compose's `.env` is not loaded by Spring Boot. Enter the same database password used in `.env`; choose private distinct JWT/OTP values at least 32 bytes. Choose a local bootstrap password of at least 12 characters including upper/lowercase and a digit:

   ```zsh
   export DB_URL='jdbc:mysql://127.0.0.1:3306/paspm?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
   export DB_USER=paspm
   read -s 'DB_PASSWORD?Local database password: '; echo; export DB_PASSWORD
   read -s 'JWT_SECRET?Local JWT secret: '; echo; export JWT_SECRET
   read -s 'OTP_SECRET?Local OTP secret: '; echo; export OTP_SECRET
   export BOOTSTRAP_EMAIL='admin@local.test'
   read -s 'BOOTSTRAP_PASSWORD?New local System Admin password: '; echo; export BOOTSTRAP_PASSWORD
   export SPRING_PROFILES_ACTIVE=dev
   ```

   `dev` deliberately seeds publicly known demo accounts and sample data: use only this disposable database. Omit it to avoid sample data. Bootstrap credentials create an account only when that email does not already exist.

6. Configure the approved SPMF JAR, stored outside Git. Replace the prompted path with your local absolute path:

   ```zsh
   read 'PASPM_MINING_SPMF_JAR?Absolute approved SPMF JAR path: '; export PASPM_MINING_SPMF_JAR
   shasum -a 256 "$PASPM_MINING_SPMF_JAR"
   "$PASPM_MINING_JAVA_EXECUTABLE" -Djava.awt.headless=true -jar "$PASPM_MINING_SPMF_JAR" version
   ```

   Compare hash and banner against the [common reference](LOCAL_SETUP_COMMON.md). If the approved file is unavailable, leave the two mining variables unset; Backend can start but F05 mining returns 503. Do not download or accept an unpinned replacement.

7. Build and start (startup runs Flyway automatically):

   ```zsh
   sh ./mvnw clean verify -Dpaspm.test.spmf-jar="$PASPM_MINING_SPMF_JAR" -Dpaspm.test.spmf-java="$PASPM_MINING_JAVA_EXECUTABLE"
   sh ./mvnw spring-boot:run
   ```

   If SPMF is missing, the full mining tests cannot be claimed as passed; configure it first. Watch logs for Flyway and startup failures. From another terminal, verify migrations using your local password when prompted:

   ```zsh
   docker compose exec mysql mysql -u paspm -p paspm -e 'SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;'
   ```

8. Open `http://localhost:8080/swagger`, or the chosen `PORT`; OpenAPI is `http://localhost:8080/v3/api-docs`. Follow [authentication and F03/F05 checks](LOCAL_SETUP_COMMON.md#flyway-accounts-and-verification). For an authenticated demo Merchandiser, use the `dev` fixture only on an isolated database. Prepare F04 transactions and a READY snapshot before real mining.

9. Stop Backend with `Ctrl+C`, then `docker compose down`. This leaves the local database volume intact. The documented `dev` fixture and SPMF success require separate verification in your environment; the Linux Docker parent-death mechanism does not apply to macOS host processes.


The working tree currently includes Flyway V1–V17. F05 mining migrations are V13–V17; V12 is F04 `f04_preserve_large_raw_import_rows`. The Linux Docker image and crash acceptance are still under verification; a successful host startup does not validate Linux container process supervision. Scheduler activation remains 503. See the [runtime guide](F05_SPMF_RUNTIME_SETUP.md#linux-docker-image-working-tree-acceptance-pending). This guide has not been verified on a clean target-OS installation.
