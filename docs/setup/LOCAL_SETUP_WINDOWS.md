# Windows 10/11 local setup (PowerShell)

**NOT VERIFIED ON TARGET OS:** these instructions have not been run on a Windows machine. Use an isolated local database only. See [common reference](LOCAL_SETUP_COMMON.md) and [troubleshooting](LOCAL_SETUP_TROUBLESHOOTING.md). Do not disable Windows protections or change the system-wide execution policy.

1. Install Git for Windows, [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) and separate JDK 17 and JDK 25 installations for your CPU from a trusted distributor such as [Eclipse Temurin](https://adoptium.net/installation). Start Docker Desktop and confirm `docker info` succeeds. If Docker requires WSL 2 or a supported Windows edition, follow Docker's current prerequisites.

2. In PowerShell, clone or update. Check local changes before switching branches:

   ```powershell
   git clone https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE.git
   Set-Location CP_FA26_PASPM_BE
   git status --short --branch
   git fetch origin
   git switch dev
   git pull --ff-only origin dev
   ```

   Use the intended branch instead of `dev` if directed. Unpushed changes cannot be obtained from GitHub.

3. Select the two installed JDKs separately. Enter **folder paths** as installed on your computer; quotes are handled by PowerShell, including spaces:

   ```powershell
   $env:JAVA_HOME = Read-Host 'JDK 17 home directory'
   $jdk25 = Read-Host 'JDK 25 home directory'
   $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
   $env:PASPM_MINING_JAVA_EXECUTABLE = Join-Path $jdk25 'bin\java.exe'
   & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version
   & (Join-Path $env:JAVA_HOME 'bin\javac.exe') -version
   & $env:PASPM_MINING_JAVA_EXECUTABLE -version
   .\mvnw.cmd -version
   ```

   Backend/Maven must report Java 17; SPMF must report 25 or later. `JAVA_HOME` does not set the SPMF executable. Run these commands in PowerShell, not Command Prompt.

4. Create local Compose settings without overwriting an existing file, fill them, then start MySQL/Mailpit:

   ```powershell
   if (-not (Test-Path .env)) { Copy-Item .env.example .env }
   notepad .env
   docker compose config --services
   docker compose up -d mysql mailpit
   docker compose ps
   ```

   Set nonempty local-only `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` before starting Compose. Never commit or share `.env`. Compose uses database and user `paspm`, port `127.0.0.1:3306`, and a persistent local volume.

5. Set Backend process variables in the **same PowerShell window**. Use the same database password as `.env`; choose separate private JWT and OTP values of at least 32 UTF-8 bytes. The bootstrap password needs 12 characters, uppercase, lowercase and a digit:

   ```powershell
   $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/paspm?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
   $env:DB_USER = 'paspm'
   $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Local database password' -AsSecureString)).Password
   $env:JWT_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host 'Local JWT secret' -AsSecureString)).Password
   $env:OTP_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host 'Local OTP secret' -AsSecureString)).Password
   $env:BOOTSTRAP_EMAIL = 'admin@local.test'
   $env:BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Local System Admin password' -AsSecureString)).Password
   $env:SPRING_PROFILES_ACTIVE = 'dev'
   ```

   `dev` seeds publicly known accounts and sample data; only use it with the local disposable database. Omit it otherwise. Bootstrap creates a SYSTEM_ADMIN only if that email is absent. PowerShell variables are process-local and must be configured again in a new window.

6. Supply the absolute path to the approved SPMF JAR, kept outside Git, and verify it with the distinct Java 25 runtime:

   ```powershell
   $env:PASPM_MINING_SPMF_JAR = Read-Host 'Absolute path to approved SPMF JAR'
   (Get-FileHash -LiteralPath $env:PASPM_MINING_SPMF_JAR -Algorithm SHA256).Hash
   & $env:PASPM_MINING_JAVA_EXECUTABLE -Djava.awt.headless=true -jar $env:PASPM_MINING_SPMF_JAR version
   ```

   Compare hash and banner with the [common reference](LOCAL_SETUP_COMMON.md). If unavailable, leave the mining paths unset: Backend can start, but new mining requests return 503. Do not accept an unpinned replacement.

7. Build and start using the Windows wrapper. No PowerShell execution-policy change is needed:

   ```powershell
   .\mvnw.cmd clean verify "-Dpaspm.test.spmf-jar=$env:PASPM_MINING_SPMF_JAR" "-Dpaspm.test.spmf-java=$env:PASPM_MINING_JAVA_EXECUTABLE"
   .\mvnw.cmd spring-boot:run
   ```

   The full mining tests require the approved JAR and Java 25. Flyway applies migrations at Backend startup; inspect logs. In a second PowerShell window from the repository, inspect history (enter your local database password when prompted):

   ```powershell
   docker compose exec mysql mysql -u paspm -p paspm -e 'SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;'
   ```

8. Open `http://localhost:8080/swagger` and follow [authentication and F03/F05 checks](LOCAL_SETUP_COMMON.md#flyway-accounts-and-verification). Prepare valid F04 transactions and a READY snapshot before mining. A 503 when enabling scheduling is expected while it is disabled. Windows host process cleanup has not been demonstrated to have Linux container parent-death guarantees.

9. Stop Backend with `Ctrl+C`, then run `docker compose down`. This does not delete the database volume. See [troubleshooting](LOCAL_SETUP_TROUBLESHOOTING.md) before changing settings or data.


The working tree currently includes Flyway V1–V17. F05 mining migrations are V13–V17; V12 is F04 `f04_preserve_large_raw_import_rows`. The Linux Docker image and crash acceptance are still under verification; a successful host startup does not validate Linux container process supervision. Scheduler activation remains 503. See the [runtime guide](F05_SPMF_RUNTIME_SETUP.md#linux-docker-image-working-tree-acceptance-pending). This guide has not been verified on a clean target-OS installation.
