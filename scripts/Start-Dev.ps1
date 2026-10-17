param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$EnvFile
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$EnvFile) {
    $defaultEnvFile = Join-Path $projectRoot '.env'
    if (Test-Path -LiteralPath $defaultEnvFile -PathType Leaf) {
        $EnvFile = $defaultEnvFile
    }
}
if ($EnvFile) {
    if (!(Test-Path -LiteralPath $EnvFile -PathType Leaf)) { throw 'Environment file does not exist.' }
    foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile).Path)) {
        if (!$line.Trim() -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw 'Invalid environment file line; use NAME=value.' }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($name -notmatch '^(DB_URL|DB_USER|DB_PASSWORD|MYSQL_ROOT_PASSWORD|JWT_SECRET|OTP_SECRET|BOOTSTRAP_EMAIL|BOOTSTRAP_PASSWORD|GOOGLE_CLIENT_ID|PORT|SMTP_HOST|SMTP_PORT|SMTP_USERNAME|SMTP_PASSWORD|SMTP_AUTH|SMTP_TLS|MAIL_FROM|TOKEN_TTL_SECONDS|OTP_TTL_SECONDS|OTP_COOLDOWN_SECONDS)$') {
            throw "Unsupported environment variable: $name"
        }
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}
if (!$JdkHome -or !(Test-Path -LiteralPath (Join-Path $JdkHome 'bin/java.exe'))) {
    throw 'Set JAVA_HOME or pass -JdkHome pointing to an existing JDK 17 directory.'
}
if (!(Test-Path -LiteralPath (Join-Path $JdkHome 'bin/javac.exe'))) { throw 'A full JDK 17 is required.' }
$javaVersion = (& (Join-Path $JdkHome 'bin/javac.exe') -version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'javac 17[.]') { throw 'This project requires JDK 17.' }
foreach ($name in @('DB_PASSWORD','JWT_SECRET','OTP_SECRET')) {
    if (![Environment]::GetEnvironmentVariable($name,'Process')) { throw "Set $name before starting the backend." }
}
foreach ($name in @('JWT_SECRET','OTP_SECRET')) {
    if ([Text.Encoding]::UTF8.GetByteCount([Environment]::GetEnvironmentVariable($name,'Process')) -lt 32) {
        throw "$name must contain at least 32 bytes."
    }
}
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkHome).Path
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
Push-Location $projectRoot
try {
    & ./mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error. Inspect the Maven/Spring Boot output.' }
} finally { Pop-Location }
