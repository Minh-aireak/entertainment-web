param(
    [string]$EnvFile = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..")).Path ".env")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Missing environment file: $EnvFile"
}

foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], "Process")
    }
}

# .env is container-oriented. A backend process launched from the host must use
# published localhost ports instead of Docker DNS service names.
$localOverrides = @{
    MYSQL_HOST               = "localhost"
    MONGODB_HOST             = "localhost"
    REDIS_HOST               = "localhost"
    KAFKA_BOOTSTRAP_SERVERS  = "localhost:9094"
    ELASTICSEARCH_HOST       = "localhost"
    ELASTICSEARCH_URIS       = "http://localhost:9200"
    DEBEZIUM_CONNECT_URL     = "http://localhost:8100"
    IDENTITY_SERVICE_URL     = "http://localhost:8080"
    PROFILE_SERVICE_URL      = "http://localhost:8081"
    PROFILES_SERVICE_URL     = "http://localhost:8081"
    NOTIFICATION_SERVICE_URL = "http://localhost:8082"
    POST_SERVICE_URL         = "http://localhost:8083"
    FILE_SERVICE_URL         = "http://localhost:8084"
    CHAT_SERVICE_URL         = "http://localhost:8086"
    FRIEND_SERVICE_URL       = "http://localhost:8087"
    SOCKET_SERVICE_URL       = "http://localhost:8088"
    SOCKET_SERVICE_WS_URL    = "ws://localhost:8088"
    COMMENT_SERVICE_URL      = "http://localhost:8089"
    FILM_SERVICE_URL         = "http://localhost:8090"
    ROOM_SERVICE_URL         = "http://localhost:8091"
}
foreach ($entry in $localOverrides.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
}

Write-Host "Loaded .env into the current PowerShell process with localhost service overrides."
