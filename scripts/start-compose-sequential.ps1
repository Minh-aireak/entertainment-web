param(
    [switch]$SkipBuild,
    [int]$TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Invoke-Compose([string[]]$Arguments) {
    & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Wait-TcpPort([string]$Name, [int]$Port) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $task = $client.ConnectAsync("127.0.0.1", $Port)
            if ($task.Wait(2000) -and $client.Connected) {
                Write-Host "$Name is accepting connections on port $Port"
                return
            }
        }
        catch {
            # The service is still starting; retry until the shared deadline.
        }
        finally {
            $client.Dispose()
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $Name on port $Port"
}

function Start-Infrastructure([string]$Name, [int]$Port) {
    Write-Host "Starting infrastructure: $Name"
    Invoke-Compose @("up", "-d", $Name)
    Wait-TcpPort $Name $Port
}

function Start-Application([string]$Name, [int]$Port) {
    Write-Host "Starting application: $Name"
    if (-not $SkipBuild) {
        Invoke-Compose @("build", $Name)
    }
    Invoke-Compose @("up", "-d", $Name)
    Wait-TcpPort $Name $Port
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not installed or is not available on PATH"
}

Push-Location $ProjectRoot
try {
    Start-Infrastructure "mysql" 3306
    Start-Infrastructure "mongodb" 27017

    Write-Host "Running MongoDB replica-set initialization and seed data"
    Invoke-Compose @("up", "-d", "--force-recreate", "mongo-init")
    $mongoInitExitCode = & docker wait mongo-init
    if ($LASTEXITCODE -ne 0 -or [int]$mongoInitExitCode -ne 0) {
        throw "mongo-init failed with container exit code $mongoInitExitCode"
    }

    Start-Infrastructure "kafka" 9094
    Start-Infrastructure "redis" 6379
    Start-Infrastructure "elasticsearch" 9200
    Start-Infrastructure "connect" 8100

    $applications = [ordered]@{
        "debezium-manager" = 9000
        "identity"         = 8080
        "profile"          = 8081
        "notification"     = 8082
        "post"             = 8083
        "file"             = 8084
        "chat"             = 8086
        "friend"           = 8087
        "socket"           = 8088
        "comment"          = 8089
        "film"             = 8090
        "room"             = 8091
        "gateway"          = 8888
        "web-app"          = 5173
    }
    foreach ($application in $applications.GetEnumerator()) {
        Start-Application $application.Key $application.Value
    }

    Invoke-Compose @("ps")
}
finally {
    Pop-Location
}
