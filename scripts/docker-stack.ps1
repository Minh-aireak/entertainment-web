<#
.SYNOPSIS
    Start the whole Docker Compose stack one container at a time (low-RAM friendly).

.DESCRIPTION
    Every step runs "docker compose up -d --no-deps <service>" so Compose never
    brings up a dependency graph in parallel. Each container must become ready
    (health check when the service defines one, otherwise a TCP probe on the
    published port) before the next step starts, and the script pauses for a
    cooldown between steps so a weak machine can settle.

.PARAMETER Action
    up      Start every selected step in order (default).
    stop    Stop the selected steps in reverse order, one at a time.
    down    Stop in reverse order, then remove containers (volumes are kept).
    restart Stop in reverse order, then start again in order.
    status  Print "docker compose ps" plus a CPU/memory snapshot.

.EXAMPLE
    .\scripts\docker-stack.ps1
    Start the full stack sequentially with the images that already exist.

.EXAMPLE
    .\scripts\docker-stack.ps1 up -Build
    Rebuild each application image right before starting it, one build at a time.

.EXAMPLE
    .\scripts\docker-stack.ps1 up -From identity
    Resume at the identity step when infrastructure is already running.

.EXAMPLE
    .\scripts\docker-stack.ps1 up -Only room,gateway -Build -Recreate
    Rebuild and recreate two services without touching the rest.

.EXAMPLE
    .\scripts\docker-stack.ps1 stop -Skip mysql,mongodb
    Stop everything but keep the databases running.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'stop', 'down', 'restart', 'status')]
    [string]$Action = 'up',

    # Run only these compose services, keeping the canonical order.
    [string[]]$Only = @(),

    # Resume the sequence at this step and run everything after it.
    [string]$From = '',

    # Drop these steps from the sequence.
    [string[]]$Skip = @(),

    # Build the application image before starting it; infrastructure is never built.
    [switch]$Build,

    # Pass --force-recreate so a container picks up new .env values.
    [switch]$Recreate,

    # Readiness budget per container.
    [int]$TimeoutSeconds = 600,

    # Idle gap between two steps, to let CPU and disk calm down.
    [int]$CooldownSeconds = 5,

    # Continue with the next step instead of aborting when one fails.
    [switch]$KeepGoing
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# Canonical boot order. Port is the port INSIDE the container; the published host
# port is resolved at runtime through "docker compose port" so .env overrides work.
# Kind: infra = pulled image, job = run-once container, app = image built here.
$Steps = @(
    [pscustomobject]@{ Name = 'mysql'; Kind = 'infra'; Port = 3306; Note = 'relational store + binlog for Debezium' }
    [pscustomobject]@{ Name = 'mongodb'; Kind = 'infra'; Port = 27017; Note = 'document store, has a health check' }
    [pscustomobject]@{ Name = 'mongo-init'; Kind = 'job'; Port = 0; Note = 'replica set init + Mongo seed, must exit 0' }
    [pscustomobject]@{ Name = 'kafka'; Kind = 'infra'; Port = 9094; Note = 'KRaft broker' }
    [pscustomobject]@{ Name = 'redis'; Kind = 'infra'; Port = 6379; Note = 'cache and presence' }
    [pscustomobject]@{ Name = 'elasticsearch'; Kind = 'infra'; Port = 9200; Note = 'search index, heaviest infra container' }
    [pscustomobject]@{ Name = 'connect'; Kind = 'infra'; Port = 8083; Note = 'Debezium Kafka Connect' }
    [pscustomobject]@{ Name = 'debezium-manager'; Kind = 'app'; Port = 9000; Note = 'registers connectors, identity waits for it' }
    [pscustomobject]@{ Name = 'identity'; Kind = 'app'; Port = 8080; Note = 'auth and user seed through the outbox' }
    [pscustomobject]@{ Name = 'profile'; Kind = 'app'; Port = 8081; Note = '' }
    [pscustomobject]@{ Name = 'notification'; Kind = 'app'; Port = 8082; Note = '' }
    [pscustomobject]@{ Name = 'post'; Kind = 'app'; Port = 8083; Note = '' }
    [pscustomobject]@{ Name = 'file'; Kind = 'app'; Port = 8084; Note = 'Backblaze B2 gateway' }
    [pscustomobject]@{ Name = 'chat'; Kind = 'app'; Port = 8086; Note = '' }
    [pscustomobject]@{ Name = 'friend'; Kind = 'app'; Port = 8087; Note = '' }
    [pscustomobject]@{ Name = 'socket'; Kind = 'app'; Port = 8088; Note = 'WebSocket fan-out' }
    [pscustomobject]@{ Name = 'comment'; Kind = 'app'; Port = 8089; Note = '' }
    [pscustomobject]@{ Name = 'film'; Kind = 'app'; Port = 8090; Note = '' }
    [pscustomobject]@{ Name = 'room'; Kind = 'app'; Port = 8091; Note = 'Watch Together rooms' }
    [pscustomobject]@{ Name = 'gateway'; Kind = 'app'; Port = 8888; Note = 'API gateway, entry point of the backend' }
    [pscustomobject]@{ Name = 'web-app'; Kind = 'app'; Port = 8080; Note = 'frontend, published on host port 5173' }
)

function Write-Step([string]$Message) {
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Info([string]$Message) {
    Write-Host "    $Message" -ForegroundColor DarkGray
}

function Write-Ok([string]$Message) {
    Write-Host "    $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
    Write-Host "    $Message" -ForegroundColor Yellow
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-HostMemoryLine {
    try {
        $os = Get-CimInstance -ClassName Win32_OperatingSystem
        $freeGb = [math]::Round($os.FreePhysicalMemory / 1MB, 1)
        $totalGb = [math]::Round($os.TotalVisibleMemorySize / 1MB, 1)
        return "host RAM free $freeGb GB of $totalGb GB"
    }
    catch {
        return 'host RAM usage unavailable'
    }
}

function Get-ContainerId([string]$Service) {
    $id = & docker compose ps -a -q $Service 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $id) { return '' }
    return ([string[]]$id | Select-Object -First 1).Trim()
}

function Get-ContainerState([string]$ContainerId) {
    $format = '{{.State.Status}};{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}};{{.State.ExitCode}}'
    $raw = & docker inspect --format $format $ContainerId 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $raw) { return $null }
    $parts = ([string]$raw).Trim().Split(';')
    if ($parts.Count -lt 3) { return $null }
    return [pscustomobject]@{
        Status   = $parts[0]
        Health   = $parts[1]
        ExitCode = [int]$parts[2]
    }
}

function Get-PublishedPort([string]$Service, [int]$ContainerPort) {
    if ($ContainerPort -le 0) { return 0 }
    $raw = & docker compose port $Service $ContainerPort 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $raw) { return 0 }
    $line = ([string[]]$raw | Select-Object -First 1).Trim()
    $index = $line.LastIndexOf(':')
    if ($index -lt 0) { return 0 }
    $port = 0
    if ([int]::TryParse($line.Substring($index + 1), [ref]$port)) { return $port }
    return 0
}

function Test-TcpPort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return ($task.Wait(2000) -and $client.Connected)
    }
    catch {
        # Still booting; the caller retries until the deadline.
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Show-FailureLogs([string]$Service) {
    Write-Warn "last 40 log lines of ${Service}:"
    & docker compose logs --tail 40 --no-color $Service
}

function Wait-ServiceReady($Step) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $hostPort = 0
    $reportedHealth = ''

    while ([DateTime]::UtcNow -lt $deadline) {
        $id = Get-ContainerId $Step.Name
        if ($id) {
            $state = Get-ContainerState $id
            if ($null -ne $state) {
                if ($state.Status -eq 'exited' -or $state.Status -eq 'dead') {
                    Show-FailureLogs $Step.Name
                    throw "$($Step.Name) stopped while starting (status $($state.Status), exit code $($state.ExitCode))"
                }

                if ($state.Status -eq 'running') {
                    if ($state.Health -ne 'none') {
                        if ($state.Health -eq 'healthy') {
                            Write-Info 'health check passed'
                            return
                        }
                        if ($state.Health -ne $reportedHealth) {
                            $reportedHealth = $state.Health
                            Write-Info "health check: $($state.Health)"
                        }
                    }
                    elseif ($Step.Port -gt 0) {
                        if ($hostPort -eq 0) { $hostPort = Get-PublishedPort $Step.Name $Step.Port }
                        if ($hostPort -gt 0 -and (Test-TcpPort $hostPort)) {
                            Write-Info "accepting connections on host port $hostPort"
                            return
                        }
                    }
                    else {
                        return
                    }
                }
            }
        }
        Start-Sleep -Seconds 3
    }

    Show-FailureLogs $Step.Name
    throw "timed out after $TimeoutSeconds seconds waiting for $($Step.Name)"
}

function Invoke-RunOnceJob($Step) {
    Invoke-Compose @('up', '-d', '--no-deps', '--force-recreate', $Step.Name)
    $id = Get-ContainerId $Step.Name
    if (-not $id) { throw "$($Step.Name) container was not created" }

    $exitCode = & docker wait $id
    if ($LASTEXITCODE -ne 0) { throw "docker wait $($Step.Name) failed" }
    if ([int]$exitCode -ne 0) {
        Show-FailureLogs $Step.Name
        throw "$($Step.Name) finished with exit code $exitCode"
    }
    Write-Ok "$($Step.Name) completed successfully"
}

function Start-Step($Step) {
    $started = [DateTime]::UtcNow
    $label = $Step.Name
    if ($Step.Note) { $label = "$label - $($Step.Note)" }
    Write-Step "starting $label"
    Write-Info (Get-HostMemoryLine)

    if ($Build -and $Step.Kind -eq 'app') {
        Write-Info 'building the image first, this is the slow part'
        Invoke-Compose @('build', $Step.Name)
    }

    if ($Step.Kind -eq 'job') {
        Invoke-RunOnceJob $Step
    }
    else {
        $upArgs = [System.Collections.Generic.List[string]]::new()
        $upArgs.Add('up')
        $upArgs.Add('-d')
        $upArgs.Add('--no-deps')
        if ($Recreate) { $upArgs.Add('--force-recreate') }
        $upArgs.Add($Step.Name)
        Invoke-Compose $upArgs.ToArray()
        Wait-ServiceReady $Step
        Write-Ok "$($Step.Name) is ready"
    }

    $elapsed = [math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 1)
    Write-Info "step finished in $elapsed seconds"
}

function Stop-Step($Step) {
    Write-Step "stopping $($Step.Name)"
    Invoke-Compose @('stop', $Step.Name)
}

function Select-Steps {
    $selected = $Steps
    $allNames = [string[]]($Steps | ForEach-Object { $_.Name })

    if ($Only.Count -gt 0) {
        $unknown = $Only | Where-Object { $allNames -notcontains $_ }
        if ($unknown) { throw "unknown service name: $($unknown -join ', ')" }
        $selected = $selected | Where-Object { $Only -contains $_.Name }
    }
    elseif ($From) {
        $index = [array]::IndexOf($allNames, $From)
        if ($index -lt 0) { throw "unknown -From service: $From" }
        $selected = $Steps[$index..($Steps.Count - 1)]
    }

    if ($Skip.Count -gt 0) {
        $unknownSkip = $Skip | Where-Object { $allNames -notcontains $_ }
        if ($unknownSkip) { throw "unknown -Skip service: $($unknownSkip -join ', ')" }
        $selected = $selected | Where-Object { $Skip -notcontains $_.Name }
    }

    $result = @($selected)
    if ($result.Count -eq 0) { throw 'the current filters selected no service' }
    return $result
}

function Invoke-UpSequence($Selected) {
    $failed = @()
    for ($i = 0; $i -lt $Selected.Count; $i++) {
        try {
            Start-Step $Selected[$i]
        }
        catch {
            if (-not $KeepGoing) { throw }
            $failed += $Selected[$i].Name
            Write-Warn "moving on after failure: $($_.Exception.Message)"
        }
        if ($CooldownSeconds -gt 0 -and $i -lt ($Selected.Count - 1)) {
            Start-Sleep -Seconds $CooldownSeconds
        }
    }
    if ($failed.Count -gt 0) {
        Write-Host ''
        Write-Warn "failed step(s): $($failed -join ', ')"
    }
}

function Invoke-StopSequence($Selected) {
    for ($i = $Selected.Count - 1; $i -ge 0; $i--) {
        Stop-Step $Selected[$i]
    }
}

function Show-Status {
    Write-Step 'docker compose ps'
    & docker compose ps
    Write-Step 'resource usage'
    Write-Info (Get-HostMemoryLine)
    & docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'docker is not installed or not on PATH'
}

& docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose v2 is unavailable, start Docker Desktop and try again'
}

Push-Location $ProjectRoot
try {
    if (-not (Test-Path (Join-Path $ProjectRoot '.env'))) {
        Write-Warn '.env is missing, compose will fail on the required variables; copy .env.example first'
    }

    switch ($Action) {
        'status' {
            Show-Status
        }
        'stop' {
            Invoke-StopSequence @(Select-Steps)
        }
        'down' {
            Invoke-StopSequence @(Select-Steps)
            Write-Step 'removing containers, volumes are kept'
            Invoke-Compose @('down', '--remove-orphans')
        }
        'restart' {
            $selected = @(Select-Steps)
            Invoke-StopSequence $selected
            Invoke-UpSequence $selected
            Show-Status
        }
        default {
            $selected = @(Select-Steps)
            $order = ($selected | ForEach-Object { $_.Name }) -join ' -> '
            Write-Step "sequential start of $($selected.Count) container(s)"
            Write-Info $order
            Invoke-UpSequence $selected
            Show-Status
            Write-Host ''
            Write-Ok 'stack is up: frontend http://localhost:5173, API gateway http://localhost:8888'
        }
    }
}
finally {
    Pop-Location
}
