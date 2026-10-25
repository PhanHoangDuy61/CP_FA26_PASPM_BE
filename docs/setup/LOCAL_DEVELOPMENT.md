# Local Backend Development

## Prerequisites

Use Java 17 for Backend/Maven, an approved development or disposable MySQL 8.4 schema, and configured JWT/OTP/SMTP settings. Flyway owns schema, including Java V11; Hibernate validates only. Review existing database history and the [recovery guide](FLYWAY_MYSQL_V7_RECOVERY.md) before any startup that could migrate a populated schema. This guide does not authorize source-database mutation.

## Configuration

Read [application configuration](../../src/main/resources/application.yml) and [persistence settings](../../src/main/resources/persistence.properties). Supply `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `OTP_SECRET`, relevant SMTP values and optional `PORT` through private local environment configuration. Secrets must not be committed or pasted into reports. `BOOTSTRAP_EMAIL`/`BOOTSTRAP_PASSWORD` are optional, explicit bootstrap inputs; no real credentials are supplied here.

## Existing launch commands

Windows PowerShell at repository root:

```powershell
.\scripts\Start-Dev.ps1
```

The [existing script](../../scripts/Start-Dev.ps1) validates JDK17 and reads its whitelisted `.env` values as data. It does not support arbitrary additional variable names in that file. Preserve its behavior; configure any extra runtime properties through the existing supported application environment/tooling instead of changing this guide into a new launcher.

Linux/macOS, after exporting the private application environment:

```sh
./mvnw spring-boot:run
```

Maven does not automatically load `.env` here. Stop a running packaged JAR before rebuilding it on Windows. Default Swagger entry is `http://localhost:8080/swagger` and runtime OpenAPI is `/v3/api-docs`; PORT can override 8080.

## Mining and verification

Mining additionally needs the approved SPMF 2.67a binary and separately configured Java 25+ child runtime. Follow [SPMF setup](F05_SPMF_RUNTIME_SETUP.md); never substitute an unpinned latest JAR. Hosting/publication approval is separate from existing local use.

[Testing index](../testing/README.md) explains H2, disposable MySQL, opt-ins and evidence limits. No launch, migration, seed or Backend test is executed by this documentation change. [Original F01 startup notes](../archive/F01/ROOT_README_BASELINE.md) retain their historical context and must not override current schema recovery policy.
