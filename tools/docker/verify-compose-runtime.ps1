[CmdletBinding()]
param(
    [AllowEmptyString()]
    [string]$ImageName = "",

    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$baseCompose = Join-Path $repoRoot "compose.yaml"
$testOverride = Join-Path $PSScriptRoot "compose.runtime-test.yaml"
$runId = "$(Get-Date -Format 'yyyyMMdd-HHmmss')-$(([Guid]::NewGuid().ToString('N')).Substring(0, 8))".ToLowerInvariant()
$project = "gameexchange-rt-$runId"
$volumeName = "${project}_mysql-data"
$networkName = "${project}_backend"
$sentinelUsername = "rtv_$(([Guid]::NewGuid().ToString('N')).Substring(0, 10))"
$mysqlImage = "mysql:8.4@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"
$evidenceDir = Join-Path $repoRoot "target\docker-runtime\$runId"
$composeArguments = @(
    "--file", $baseCompose,
    "--file", $testOverride,
    "--project-name", $project
)

$environmentNames = @(
    "RC_IMAGE",
    "APP_PORT",
    "MYSQL_DATABASE",
    "MYSQL_USER",
    "MYSQL_PASSWORD",
    "MYSQL_ROOT_PASSWORD",
    "SIMULATOR_ENABLED",
    "MYSQL_PWD"
)
$originalEnvironment = @{}
foreach ($name in $environmentNames) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable(
        $name, "Process")
}

$script:dockerReady = $false
$script:appContainerId = ""
$script:mysqlContainerId = ""
$script:expectedImageId = ""
$script:failure = $null

function Get-RandomPort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Get-RandomSecret {
    return ([Guid]::NewGuid().ToString("N") +
            [Guid]::NewGuid().ToString("N"))
}

function Convert-NativeOutputToText {
    param([object[]]$Output)

    if ($null -eq $Output) {
        return ""
    }
    return (($Output | ForEach-Object { $_.ToString() }) -join
            [Environment]::NewLine).Trim()
}

function Invoke-Docker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [switch]$Capture,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $text = Convert-NativeOutputToText -Output $output
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw ("docker $($Arguments -join ' ') failed with exit code " +
                "$exitCode`n$text")
    }
    if ($Capture) {
        return $text
    }
    if (-not [string]::IsNullOrWhiteSpace($text)) {
        Write-Host $text
    }
}

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [switch]$Capture,
        [switch]$AllowFailure
    )

    $dockerArguments = @("compose") + $script:composeArguments + $Arguments
    if ($Capture) {
        return Invoke-Docker -Arguments $dockerArguments -Capture `
            -AllowFailure:$AllowFailure
    }
    Invoke-Docker -Arguments $dockerArguments -AllowFailure:$AllowFailure
}

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)]$Actual,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if ($Expected -ne $Actual) {
        throw "$Message. Expected=[$Expected], Actual=[$Actual]"
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Write-Evidence {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowEmptyString()][string]$Value
    )

    Set-Content -LiteralPath (Join-Path $script:evidenceDir $Name) `
        -Value $Value -Encoding UTF8
}

function Get-ComposeContainerId {
    param([Parameter(Mandatory = $true)][string]$Service)

    return (Invoke-Compose -Arguments @("ps", "--quiet", $Service) `
            -Capture).Trim()
}

function Get-ContainerHealth {
    param([Parameter(Mandatory = $true)][string]$ContainerId)

    return (Invoke-Docker -Arguments @(
                "inspect",
                '--format={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}',
                $ContainerId
            ) -Capture).Trim()
}

function Get-LocalImageId {
    param([Parameter(Mandatory = $true)][string]$ImageReference)

    $imageId = (Invoke-Docker -Arguments @(
                "image", "inspect", '--format={{.Id}}', $ImageReference
            ) -Capture).Trim()
    Assert-True -Condition ($imageId -match '^sha256:[0-9a-f]{64}$') `
        -Message "Specified image ID is invalid"
    return $imageId
}

function Assert-ContainerImageIdentity {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string]$EvidencePrefix
    )

    $configuredImage = (Invoke-Docker -Arguments @(
                "inspect", '--format={{.Config.Image}}', $ContainerId
            ) -Capture).Trim()
    $containerImageId = (Invoke-Docker -Arguments @(
                "inspect", '--format={{.Image}}', $ContainerId
            ) -Capture).Trim()
    Assert-Equal -Expected $script:ImageName -Actual $configuredImage `
        -Message "Container image name does not match the specified RC image"
    Assert-Equal -Expected $script:expectedImageId -Actual $containerImageId `
        -Message "Container image ID does not match the specified RC image"
    Write-Evidence -Name "$EvidencePrefix-image-identity.txt" -Value (
        "imageName=$script:ImageName`n" +
        "imageId=$script:expectedImageId`n" +
        "containerImageId=$containerImageId`n" +
        "container=$ContainerId")
}

function Assert-ProjectContainer {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string]$Service
    )

    $labels = Invoke-Docker -Arguments @(
        "inspect", '--format={{json .Config.Labels}}', $ContainerId
    ) -Capture | ConvertFrom-Json
    $actualProject = $labels.'com.docker.compose.project'
    $actualService = $labels.'com.docker.compose.service'
    Assert-Equal -Expected $script:project -Actual $actualProject `
        -Message "Container project label mismatch"
    Assert-Equal -Expected $Service -Actual $actualService `
        -Message "Container service label mismatch"
}

function Wait-ServiceHealthy {
    param(
        [Parameter(Mandatory = $true)][string]$Service,
        [Parameter(Mandatory = $true)][int]$Timeout
    )

    $deadline = (Get-Date).AddSeconds($Timeout)
    do {
        $containerId = Get-ComposeContainerId -Service $Service
        if (-not [string]::IsNullOrWhiteSpace($containerId)) {
            $health = Get-ContainerHealth -ContainerId $containerId
            Write-Host "[$Service] health=$health"
            if ($health -eq "healthy") {
                Assert-ProjectContainer -ContainerId $containerId `
                    -Service $Service
                return $containerId
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "Service $Service did not become healthy within $Timeout seconds"
}

function Test-VolumeExists {
    param([Parameter(Mandatory = $true)][string]$Name)

    & docker volume inspect $Name *> $null
    return $LASTEXITCODE -eq 0
}

function Get-ProjectResourceIds {
    param([Parameter(Mandatory = $true)][string]$ResourceType)

    switch ($ResourceType) {
        "container" {
            $text = Invoke-Docker -Arguments @(
                "ps", "--all", "--quiet", "--filter",
                "label=com.docker.compose.project=$script:project"
            ) -Capture
        }
        "volume" {
            $text = Invoke-Docker -Arguments @(
                "volume", "ls", "--quiet", "--filter",
                "label=com.docker.compose.project=$script:project"
            ) -Capture
        }
        "network" {
            $text = Invoke-Docker -Arguments @(
                "network", "ls", "--quiet", "--filter",
                "label=com.docker.compose.project=$script:project"
            ) -Capture
        }
        default {
            throw "Unsupported resource type: $ResourceType"
        }
    }

    if ([string]::IsNullOrWhiteSpace($text)) {
        return @()
    }
    return @($text -split "`r?`n" | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_)
        })
}

function Assert-CleanStart {
    Assert-True -Condition ($script:volumeName -ne "gameexchange_mysql-data") `
        -Message "Runtime verification must not use the development volume"
    Assert-Equal -Expected 0 `
        -Actual @(Get-ProjectResourceIds -ResourceType "container").Count `
        -Message "Test project already has containers"
    Assert-Equal -Expected 0 `
        -Actual @(Get-ProjectResourceIds -ResourceType "volume").Count `
        -Message "Test project already has volumes"
    Assert-Equal -Expected 0 `
        -Actual @(Get-ProjectResourceIds -ResourceType "network").Count `
        -Message "Test project already has networks"
}

function Invoke-MySqlScalar {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $previousPassword = [Environment]::GetEnvironmentVariable(
        "MYSQL_PWD", "Process")
    [Environment]::SetEnvironmentVariable(
        "MYSQL_PWD", $env:MYSQL_ROOT_PASSWORD, "Process")
    try {
        return (Invoke-Docker -Arguments @(
                    "run", "--rm",
                    "--network", $script:networkName,
                    "--env", "MYSQL_PWD",
                    $script:mysqlImage,
                    "mysql",
                    "--host=mysql",
                    "--user=root",
                    "--default-character-set=utf8mb4",
                    "--batch",
                    "--skip-column-names",
                    "--execute", $Sql
                ) -Capture).Trim()
    } finally {
        [Environment]::SetEnvironmentVariable(
            "MYSQL_PWD", $previousPassword, "Process")
    }
}

function Assert-InitialDatabaseState {
    Assert-Equal -Expected "1" -Actual (Invoke-MySqlScalar -Sql (
            "SELECT COUNT(*) FROM information_schema.schemata " +
            "WHERE schema_name='game_exchange'")) `
        -Message "game_exchange database was not initialized"

    $tables = Invoke-MySqlScalar -Sql (
        "SELECT GROUP_CONCAT(table_name ORDER BY table_name SEPARATOR ',') " +
        "FROM information_schema.tables " +
        "WHERE table_schema='game_exchange' AND table_type='BASE TABLE'")
    Assert-Equal -Expected (
        "battle_record,game_event,item,market,player,trade_record") `
        -Actual $tables -Message "Core table set mismatch"

    Assert-Equal -Expected "1" -Actual (Invoke-MySqlScalar -Sql (
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema='game_exchange' AND table_name='player' " +
            "AND column_name='last_seen_at'")) `
        -Message "player.last_seen_at is missing"
    Assert-Equal -Expected "0" -Actual (Invoke-MySqlScalar -Sql (
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema='game_exchange' AND table_name='player' " +
            "AND column_name='online_status'")) `
        -Message "player.online_status must not exist"

    Assert-Equal -Expected "1" -Actual (Invoke-MySqlScalar -Sql (
            "SELECT COUNT(*) FROM game_exchange.player")) `
        -Message "Seed player count mismatch"
    Assert-Equal -Expected "3" -Actual (Invoke-MySqlScalar -Sql (
            "SELECT COUNT(*) FROM game_exchange.item")) `
        -Message "Seed item count mismatch"

    $itemHex = Invoke-MySqlScalar -Sql (
        "SELECT GROUP_CONCAT(CONCAT(id, ':', HEX(item_name), ':', " +
        "HEX(rarity)) ORDER BY id SEPARATOR '|') FROM game_exchange.item")
    $expectedItemHex = "1:E696B0E6898BE99381E58991:E699AEE9809A" +
        "|2:E6989FE5B098E995BFE58991:E7A880E69C89" +
        "|3:E9BE99E9B39EE68AA4E794B2:E58FB2E8AF97"
    Assert-Equal -Expected $expectedItemHex -Actual $itemHex `
        -Message "Seed item UTF-8 HEX mismatch"

    $eventHex = Invoke-MySqlScalar -Sql (
        "SELECT GROUP_CONCAT(CONCAT(id, ':', HEX(event_desc)) " +
        "ORDER BY id SEPARATOR '|') FROM game_exchange.game_event")
    $expectedEventHex = "1:E88EB7E5BE97E38090E7A880E69C89E38091" +
        "E8A385E5A487EFBC9AE6989FE5B098E995BFE58991" +
        "|2:E88EB7E5BE97E38090E58FB2E8AF97E38091" +
        "E8A385E5A487EFBC9AE9BE99E9B39EE68AA4E794B2"
    Assert-Equal -Expected $expectedEventHex -Actual $eventHex `
        -Message "Seed event UTF-8 HEX mismatch"
}

function Assert-AppRuntime {
    param([Parameter(Mandatory = $true)][string]$ContainerId)

    $containerPid = (Invoke-Docker -Arguments @(
                "inspect", '--format={{.State.Pid}}', $ContainerId
            ) -Capture).Trim()
    Assert-True -Condition ([int64]$containerPid -gt 0) `
        -Message "App PID is not running"

    $processStatus = Invoke-Docker -Arguments @(
        "exec", $ContainerId, "cat", "/proc/1/status"
    ) -Capture
    $uidMatch = [regex]::Match($processStatus, '(?m)^Uid:\s+(\d+)')
    Assert-True -Condition $uidMatch.Success `
        -Message "Could not read PID 1 UID"
    Assert-Equal -Expected "10001" -Actual $uidMatch.Groups[1].Value `
        -Message "App PID 1 must not run as root"

    Invoke-Docker -Arguments @(
        "exec", $ContainerId, "test", "-f",
        "/usr/local/tomcat/webapps/ROOT.war"
    )
    Write-Evidence -Name "app-runtime.txt" -Value (
        "container=$ContainerId`npid=$containerPid`nuid=10001`nrootWar=true")
}

function Invoke-HttpSmoke {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$EvidencePrefix
    )

    $baseUri = "http://127.0.0.1:$Port"
    foreach ($path in @(
            "/vue/assets/js/app-config.js",
            "/vue/login.html")) {
        $response = Invoke-WebRequest -Uri "$baseUri$path" `
            -UseBasicParsing -TimeoutSec 15
        Assert-Equal -Expected 200 -Actual $response.StatusCode `
            -Message "HTTP smoke failed for $path"
        Assert-True -Condition (-not [string]::IsNullOrWhiteSpace(
                $response.Content)) -Message "Empty HTTP response for $path"
    }

    $statsResponse = Invoke-WebRequest -Uri "$baseUri/stats" `
        -UseBasicParsing -TimeoutSec 15
    Assert-Equal -Expected 200 -Actual $statsResponse.StatusCode `
        -Message "Stats HTTP status mismatch"
    $stats = $statsResponse.Content | ConvertFrom-Json
    Assert-Equal -Expected 200 -Actual ([int]$stats.code) `
        -Message "Stats JSON code mismatch"
    Write-Evidence -Name "$EvidencePrefix-http-smoke.txt" -Value (
        "app-config=200`nlogin=200`nstats-http=200`n" +
        "stats-code=$($stats.code)`nstats-body=$($statsResponse.Content)")
}

function Wait-AppRestartedAndHealthy {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][int]$PreviousRestartCount,
        [Parameter(Mandatory = $true)][int]$Timeout
    )

    $deadline = (Get-Date).AddSeconds($Timeout)
    do {
        $restartCount = [int](Invoke-Docker -Arguments @(
                    "inspect", '--format={{.RestartCount}}', $ContainerId
                ) -Capture)
        $health = Get-ContainerHealth -ContainerId $ContainerId
        Write-Host "[app restart] count=$restartCount health=$health"
        if ($restartCount -gt $PreviousRestartCount -and
            $health -eq "healthy") {
            return $restartCount
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "App did not restart and become healthy within $Timeout seconds"
}

function Save-Diagnostics {
    try {
        Write-Evidence -Name "compose-ps.txt" -Value (
            Invoke-Compose -Arguments @("ps", "--all") -Capture `
                -AllowFailure)
        foreach ($service in @("app", "mysql")) {
            $containerId = Get-ComposeContainerId -Service $service
            if ([string]::IsNullOrWhiteSpace($containerId)) {
                continue
            }
            Write-Evidence -Name "$service-logs.txt" -Value (
                Invoke-Docker -Arguments @("logs", $containerId) `
                    -Capture -AllowFailure)
            Write-Evidence -Name "$service-inspect.txt" -Value (
                Invoke-Docker -Arguments @(
                    "inspect",
                    '--format=state={{json .State}} imageName={{json .Config.Image}} imageId={{json .Image}} user={{json .Config.User}} labels={{json .Config.Labels}} healthcheck={{json .Config.Healthcheck}}',
                    $containerId
                ) -Capture -AllowFailure)
        }
    } catch {
        Write-Warning "Failed to save Docker diagnostics: $($_.Exception.Message)"
    }
}

function Assert-CleanupScope {
    foreach ($containerId in (Get-ProjectResourceIds -ResourceType "container")) {
        $labels = Invoke-Docker -Arguments @(
            "inspect", '--format={{json .Config.Labels}}', $containerId
        ) -Capture | ConvertFrom-Json
        $label = $labels.'com.docker.compose.project'
        Assert-Equal -Expected $script:project -Actual $label `
            -Message "Refusing cleanup: container project label mismatch"
    }

    foreach ($name in (Get-ProjectResourceIds -ResourceType "volume")) {
        Assert-True -Condition ($name -ne "gameexchange_mysql-data") `
            -Message "Refusing cleanup of development volume"
        Assert-True -Condition ($name -like "${script:project}_*") `
            -Message "Refusing cleanup: volume name is outside test project"
        $labels = Invoke-Docker -Arguments @(
            "volume", "inspect", '--format={{json .Labels}}', $name
        ) -Capture | ConvertFrom-Json
        $label = $labels.'com.docker.compose.project'
        Assert-Equal -Expected $script:project -Actual $label `
            -Message "Refusing cleanup: volume project label mismatch"
    }

    foreach ($networkId in (Get-ProjectResourceIds -ResourceType "network")) {
        $labels = Invoke-Docker -Arguments @(
            "network", "inspect", '--format={{json .Labels}}', $networkId
        ) -Capture | ConvertFrom-Json
        $label = $labels.'com.docker.compose.project'
        Assert-Equal -Expected $script:project -Actual $label `
            -Message "Refusing cleanup: network project label mismatch"
    }
}

function Remove-TestProject {
    if (-not $script:dockerReady) {
        return
    }

    Assert-CleanupScope
    Invoke-Compose -Arguments @(
        "down", "--volumes", "--remove-orphans", "--timeout", "30"
    ) -AllowFailure

    Assert-Equal -Expected 0 `
        -Actual @(Get-ProjectResourceIds -ResourceType "container").Count `
        -Message "Test containers remain after cleanup"
    Assert-Equal -Expected 0 `
        -Actual @(Get-ProjectResourceIds -ResourceType "volume").Count `
        -Message "Test volumes remain after cleanup"
    Assert-Equal -Expected 0 `
        -Actual @(Get-ProjectResourceIds -ResourceType "network").Count `
        -Message "Test networks remain after cleanup"
    Write-Evidence -Name "cleanup.txt" -Value (
        "project=$script:project`ncontainers=0`nvolumes=0`nnetworks=0")
    Write-Host "Cleanup verified for isolated project $script:project"
}

$appPort = Get-RandomPort
[Environment]::SetEnvironmentVariable("RC_IMAGE", $ImageName, "Process")
[Environment]::SetEnvironmentVariable(
    "APP_PORT", $appPort.ToString(), "Process")
[Environment]::SetEnvironmentVariable(
    "MYSQL_DATABASE", "game_exchange", "Process")
[Environment]::SetEnvironmentVariable(
    "MYSQL_USER", "gameexchange_app", "Process")
[Environment]::SetEnvironmentVariable(
    "MYSQL_PASSWORD", (Get-RandomSecret), "Process")
[Environment]::SetEnvironmentVariable(
    "MYSQL_ROOT_PASSWORD", (Get-RandomSecret), "Process")
[Environment]::SetEnvironmentVariable(
    "SIMULATOR_ENABLED", "false", "Process")

New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
Push-Location $repoRoot
try {
    Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($ImageName)) `
        -Message "ImageName must identify an existing RC image"
    Assert-True -Condition ($ImageName -ne "gameexchange:dev") `
        -Message "Runtime verification must not use gameexchange:dev"

    $dockerVersion = Invoke-Docker -Arguments @("version") -Capture
    $composeVersion = Invoke-Docker -Arguments @(
        "compose", "version"
    ) -Capture
    $buildxVersion = Invoke-Docker -Arguments @(
        "buildx", "version"
    ) -Capture
    $platform = (Invoke-Docker -Arguments @(
            "info", '--format={{.OSType}}/{{.Architecture}}'
        ) -Capture).Trim()
    Assert-True -Condition ($platform -match '^linux/(amd64|x86_64)$') `
        -Message "Pinned images require a verified linux/amd64 Docker engine"
    $script:dockerReady = $true
    $script:expectedImageId = Get-LocalImageId -ImageReference $ImageName
    Write-Evidence -Name "preflight.txt" -Value (
        "$dockerVersion`n$composeVersion`n$buildxVersion`nplatform=$platform`n" +
        "imageName=$ImageName`nimageId=$script:expectedImageId")

    Assert-CleanStart
    Invoke-Compose -Arguments @("config", "--quiet")
    $resolvedConfig = Invoke-Compose -Arguments @(
        "config", "--format", "json"
    ) -Capture | ConvertFrom-Json
    Assert-Equal -Expected $ImageName `
        -Actual $resolvedConfig.services.app.image `
        -Message "Runtime app image tag override was not applied"
    Assert-True -Condition (
        $resolvedConfig.services.app.image -ne "gameexchange:dev") `
        -Message "Runtime verification must not share gameexchange:dev"
    Assert-Equal -Expected 1 `
        -Actual @($resolvedConfig.services.app.ports).Count `
        -Message "Runtime app must publish exactly one port"
    Assert-Equal -Expected $appPort `
        -Actual ([int]$resolvedConfig.services.app.ports[0].published) `
        -Message "Runtime app port override was not applied"

    Write-Host "Starting isolated project $project on port $appPort"
    Invoke-Compose -Arguments @(
        "up", "--detach", "--no-build", "--pull", "never"
    )
    $script:mysqlContainerId = Wait-ServiceHealthy -Service "mysql" `
        -Timeout $TimeoutSeconds
    $script:appContainerId = Wait-ServiceHealthy -Service "app" `
        -Timeout $TimeoutSeconds
    Assert-True -Condition (Test-VolumeExists -Name $volumeName) `
        -Message "Expected isolated MySQL volume was not created"

    Assert-ContainerImageIdentity -ContainerId $script:appContainerId `
        -EvidencePrefix "initial"
    Assert-InitialDatabaseState
    Assert-AppRuntime -ContainerId $script:appContainerId
    Invoke-HttpSmoke -Port $appPort -EvidencePrefix "initial"

    $sentinelId = Invoke-MySqlScalar -Sql (
        "INSERT INTO game_exchange.player(username, password, gold) VALUES (" +
        "'$sentinelUsername', 'runtime-only', 1234); " +
        "SELECT LAST_INSERT_ID();")
    Assert-True -Condition ($sentinelId -match '^\d+$') `
        -Message "Sentinel insert did not return an ID"
    Write-Evidence -Name "sentinel.txt" -Value (
        "username=$sentinelUsername`nid=$sentinelId")

    $restartCountBefore = [int](Invoke-Docker -Arguments @(
                "inspect", '--format={{.RestartCount}}',
                $script:appContainerId
            ) -Capture)
    Invoke-Docker -Arguments @(
        "exec", "--user", "root", $script:appContainerId,
        "kill", "-TERM", "1"
    )
    $restartCountAfter = Wait-AppRestartedAndHealthy `
        -ContainerId $script:appContainerId `
        -PreviousRestartCount $restartCountBefore `
        -Timeout $TimeoutSeconds
    Invoke-HttpSmoke -Port $appPort -EvidencePrefix "after-restart"
    Write-Evidence -Name "restart.txt" -Value (
        "container=$($script:appContainerId)`n" +
        "before=$restartCountBefore`nafter=$restartCountAfter`nhealth=healthy")

    Save-Diagnostics
    Invoke-Compose -Arguments @(
        "down", "--remove-orphans", "--timeout", "30"
    )
    Assert-True -Condition (Test-VolumeExists -Name $volumeName) `
        -Message "Isolated volume was removed by compose down without --volumes"
    $volumeLabels = Invoke-Docker -Arguments @(
        "volume", "inspect", '--format={{json .Labels}}', $volumeName
    ) -Capture | ConvertFrom-Json
    $volumeProject = $volumeLabels.'com.docker.compose.project'
    Assert-Equal -Expected $project -Actual $volumeProject `
        -Message "Persisted volume project label mismatch"

    Invoke-Compose -Arguments @(
        "up", "--detach", "--no-build", "--pull", "never"
    )
    $script:mysqlContainerId = Wait-ServiceHealthy -Service "mysql" `
        -Timeout $TimeoutSeconds
    $script:appContainerId = Wait-ServiceHealthy -Service "app" `
        -Timeout $TimeoutSeconds
    Assert-ContainerImageIdentity -ContainerId $script:appContainerId `
        -EvidencePrefix "after-volume-reuse"

    Assert-Equal -Expected "1" -Actual (Invoke-MySqlScalar -Sql (
            "SELECT COUNT(*) FROM game_exchange.player " +
            "WHERE id=$sentinelId AND username='$sentinelUsername'")) `
        -Message "Sentinel did not survive container recreation"
    Assert-Equal -Expected "1" -Actual (Invoke-MySqlScalar -Sql (
            "SELECT COUNT(*) FROM game_exchange.player " +
            "WHERE username='seed_seller'")) `
        -Message "Seed player was duplicated after restart"
    Assert-Equal -Expected "3" -Actual (Invoke-MySqlScalar -Sql (
            "SELECT COUNT(*) FROM game_exchange.item")) `
        -Message "Seed items were duplicated after restart"

    $secondMysqlLogs = Invoke-Docker -Arguments @(
        "logs", $script:mysqlContainerId
    ) -Capture
    Assert-True -Condition (
        $secondMysqlLogs -notmatch '/docker-entrypoint-initdb.d/0[12]-') `
        -Message "Baseline initialization scripts reran on an existing volume"
    Invoke-HttpSmoke -Port $appPort -EvidencePrefix "after-volume-reuse"
    Write-Evidence -Name "volume-persistence.txt" -Value (
        "volume=$volumeName`nsentinel=present`nseed-player=1`n" +
        "seed-items=3`ninit-scripts-reran=false")

    Save-Diagnostics
    Write-Evidence -Name "result.txt" -Value (
        "status=PASS`nrunId=$runId`nproject=$project`nimage=$ImageName`n" +
        "imageId=$script:expectedImageId`n" +
        "port=$appPort`nmysql=healthy`napp=healthy")
    Write-Host "Phase 5C.7F.1 runtime verification PASSED"
} catch {
    $script:failure = $_
    $failureReason = $_.Exception.Message.Replace("`r", " ").Replace(
        "`n", " ")
    Write-Evidence -Name "failure.txt" -Value (
        "status=FAIL`nrunId=$runId`nproject=$project`n" +
        "image=$ImageName`nreason=$failureReason")
    Write-Error -ErrorAction Continue (
        "Runtime verification failed: $($_.Exception.Message)")
    if ($script:dockerReady) {
        Save-Diagnostics
    }
} finally {
    try {
        Remove-TestProject
    } catch {
        Write-Error -ErrorAction Continue (
            "Runtime cleanup failed: $($_.Exception.Message)")
        if ($null -eq $script:failure) {
            $script:failure = $_
        }
    }

    Pop-Location
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable(
            $name, $originalEnvironment[$name], "Process")
    }
}

if ($null -ne $script:failure) {
    throw $script:failure
}
