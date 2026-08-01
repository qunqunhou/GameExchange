[CmdletBinding()]
param(
    [string]$ImageName = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$runId = "$(Get-Date -Format 'yyyyMMdd-HHmmss-fff')-$(([Guid]::NewGuid().ToString('N')).Substring(0, 8))".ToLowerInvariant()
$outputDirectory = Join-Path $repoRoot "target\release-manifest\$runId"
$outputPath = Join-Path $outputDirectory "release-manifest.json"
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

function Convert-NativeOutputToText {
    param([object[]]$Output)

    if ($null -eq $Output) {
        return ""
    }
    return (($Output | ForEach-Object { $_.ToString() }) -join
            [Environment]::NewLine).Trim()
}

function Invoke-Probe {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    try {
        Get-Command -Name $FilePath -ErrorAction Stop | Out-Null
    } catch {
        return [pscustomobject]@{
            Available = $false
            Output = $null
            ExitCode = $null
            Reason = "command unavailable"
        }
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $FilePath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } catch {
        return [pscustomobject]@{
            Available = $false
            Output = $null
            ExitCode = $null
            Reason = "command execution failed"
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $text = Convert-NativeOutputToText -Output $output
    if ($exitCode -ne 0) {
        return [pscustomobject]@{
            Available = $false
            Output = $null
            ExitCode = $exitCode
            Reason = "command returned exit code $exitCode"
        }
    }
    return [pscustomobject]@{
        Available = $true
        Output = $text
        ExitCode = $exitCode
        Reason = $null
    }
}

function Get-FirstOutputLine {
    param([Parameter(Mandatory = $true)]$Probe)

    if (-not $Probe.Available -or
        [string]::IsNullOrWhiteSpace($Probe.Output)) {
        return $null
    }
    return @($Probe.Output -split "`r?`n" | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_)
        })[0].Trim()
}

function Get-RelativeRepositoryPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $prefix = $script:repoRoot.TrimEnd('\', '/') +
        [System.IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith(
            $prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside repository: $fullPath"
    }
    return $fullPath.Substring($prefix.Length).Replace('\', '/')
}

function Get-GitIdentity {
    $commitProbe = Invoke-Probe -FilePath "git" `
        -Arguments @("rev-parse", "HEAD")
    $branchProbe = Invoke-Probe -FilePath "git" `
        -Arguments @("rev-parse", "--abbrev-ref", "HEAD")
    $statusProbe = Invoke-Probe -FilePath "git" `
        -Arguments @("status", "--porcelain=v1", "--untracked-files=all")

    $commitHash = if ($commitProbe.Available) {
        $commitProbe.Output.Trim()
    } else {
        $null
    }
    $branch = if ($branchProbe.Available -and
        $branchProbe.Output.Trim() -ne "HEAD") {
        $branchProbe.Output.Trim()
    } else {
        $null
    }
    $branchState = if (-not $branchProbe.Available) {
        "unavailable"
    } elseif ($null -eq $branch) {
        "detached"
    } else {
        "attached"
    }
    $workingTreeStatus = if (-not $statusProbe.Available) {
        "unavailable"
    } elseif ([string]::IsNullOrWhiteSpace($statusProbe.Output)) {
        "clean"
    } else {
        "dirty"
    }

    return [ordered]@{
        status = if ($commitProbe.Available -and $branchProbe.Available -and
            $statusProbe.Available) { "available" } else { "partial" }
        commitHash = $commitHash
        branch = $branch
        branchState = $branchState
        workingTreeStatus = $workingTreeStatus
    }
}

function Get-BuildIdentity {
    $javaProbe = Invoke-Probe -FilePath "java" -Arguments @("-version")
    $mavenProbe = if (Test-Path -LiteralPath $script:mavenWrapper `
            -PathType Leaf) {
        Invoke-Probe -FilePath $script:mavenWrapper -Arguments @("--version")
    } else {
        [pscustomobject]@{
            Available = $false
            Output = $null
            ExitCode = $null
            Reason = "Maven Wrapper unavailable"
        }
    }

    $warFiles = @(Get-ChildItem -LiteralPath (Join-Path $script:repoRoot "target") `
            -Filter "*.war" -File -ErrorAction SilentlyContinue)
    $warStatus = "unavailable"
    $warReason = "WAR not found"
    $warPath = $null
    $warSha256 = $null
    if ($warFiles.Count -eq 1) {
        $warPath = Get-RelativeRepositoryPath -Path $warFiles[0].FullName
        try {
            $warSha256 = (Get-FileHash -LiteralPath $warFiles[0].FullName `
                    -Algorithm SHA256).Hash.ToUpperInvariant()
            $warStatus = "available"
            $warReason = $null
        } catch {
            $warReason = "WAR SHA-256 unavailable"
        }
    } elseif ($warFiles.Count -gt 1) {
        $warReason = "multiple WAR files found"
    }

    return [ordered]@{
        status = if ($javaProbe.Available -and $mavenProbe.Available -and
            $warStatus -eq "available") { "available" } else { "partial" }
        javaVersion = Get-FirstOutputLine -Probe $javaProbe
        javaStatus = if ($javaProbe.Available) { "available" } else { "unavailable" }
        mavenVersion = Get-FirstOutputLine -Probe $mavenProbe
        mavenStatus = if ($mavenProbe.Available) { "available" } else { "unavailable" }
        war = [ordered]@{
            status = $warStatus
            path = $warPath
            sha256 = $warSha256
            reason = $warReason
        }
    }
}

function Get-LocalDockerContext {
    $contextProbe = Invoke-Probe -FilePath "docker" `
        -Arguments @("context", "show")
    if (-not $contextProbe.Available) {
        return [ordered]@{
            status = "unavailable"
            name = $null
            endpoint = $null
            reason = $contextProbe.Reason
        }
    }

    $contextName = $contextProbe.Output.Trim()
    $inspectProbe = Invoke-Probe -FilePath "docker" `
        -Arguments @("context", "inspect", $contextName)
    if (-not $inspectProbe.Available) {
        return [ordered]@{
            status = "unavailable"
            name = $contextName
            endpoint = $null
            reason = $inspectProbe.Reason
        }
    }

    try {
        $context = @($inspectProbe.Output | ConvertFrom-Json)[0]
        $endpoint = [string]$context.Endpoints.docker.Host
    } catch {
        return [ordered]@{
            status = "unavailable"
            name = $contextName
            endpoint = $null
            reason = "Docker context metadata is invalid"
        }
    }

    $effectiveEndpoint = if (-not [string]::IsNullOrWhiteSpace($env:DOCKER_HOST)) {
        $env:DOCKER_HOST
    } else {
        $endpoint
    }
    $isLocal = $effectiveEndpoint -match '^(npipe|unix)://'
    return [ordered]@{
        status = if ($isLocal) { "local" } else { "blocked" }
        name = $contextName
        endpoint = if ($isLocal) { $effectiveEndpoint } else { $null }
        reason = if ($isLocal) {
            $null
        } else {
            "non-local Docker endpoint is not inspected"
        }
    }
}

function Get-DockerEnvironment {
    param([Parameter(Mandatory = $true)]$DockerContext)

    if ($DockerContext.status -ne "local") {
        return [ordered]@{
            dockerStatus = "unavailable"
            dockerClientVersion = $null
            dockerServerVersion = $null
            composeStatus = "unavailable"
            composeVersion = $null
        }
    }

    $dockerProbe = Invoke-Probe -FilePath "docker" `
        -Arguments @("version", "--format={{json .}}")
    $dockerClientVersion = $null
    $dockerServerVersion = $null
    $dockerStatus = "unavailable"
    if ($dockerProbe.Available) {
        try {
            $dockerVersion = $dockerProbe.Output | ConvertFrom-Json
            $dockerClientVersion = [string]$dockerVersion.Client.Version
            $dockerServerVersion = if ($null -ne $dockerVersion.Server) {
                [string]$dockerVersion.Server.Version
            } else {
                $null
            }
            $dockerStatus = "available"
        } catch {
            $dockerStatus = "unavailable"
        }
    }

    $composeProbe = Invoke-Probe -FilePath "docker" `
        -Arguments @("compose", "version", "--short")
    return [ordered]@{
        dockerStatus = $dockerStatus
        dockerClientVersion = $dockerClientVersion
        dockerServerVersion = $dockerServerVersion
        composeStatus = if ($composeProbe.Available) {
            "available"
        } else {
            "unavailable"
        }
        composeVersion = if ($composeProbe.Available) {
            $composeProbe.Output.Trim()
        } else {
            $null
        }
    }
}

function Get-DockerImageIdentity {
    param(
        [Parameter(Mandatory = $true)]$DockerContext,
        [AllowEmptyString()][string]$RequestedImage
    )

    if ([string]::IsNullOrWhiteSpace($RequestedImage)) {
        return [ordered]@{
            status = "unavailable"
            imageName = $null
            imageId = $null
            digest = $null
            reason = "ImageName parameter not provided"
        }
    }
    if ($DockerContext.status -ne "local") {
        return [ordered]@{
            status = "unavailable"
            imageName = $RequestedImage
            imageId = $null
            digest = $null
            reason = "local Docker context unavailable"
        }
    }

    $imageProbe = Invoke-Probe -FilePath "docker" `
        -Arguments @("image", "inspect", "--format={{json .}}", $RequestedImage)
    if (-not $imageProbe.Available) {
        return [ordered]@{
            status = "unavailable"
            imageName = $RequestedImage
            imageId = $null
            digest = $null
            reason = $imageProbe.Reason
        }
    }

    try {
        $image = $imageProbe.Output | ConvertFrom-Json
        $repoDigests = @($image.RepoDigests | Where-Object {
                -not [string]::IsNullOrWhiteSpace($_)
            })
        return [ordered]@{
            status = "available"
            imageName = $RequestedImage
            imageId = [string]$image.Id
            digest = if ($repoDigests.Count -gt 0) {
                [string]$repoDigests[0]
            } else {
                $null
            }
            reason = if ($repoDigests.Count -gt 0) {
                $null
            } else {
                "repository digest unavailable"
            }
        }
    } catch {
        return [ordered]@{
            status = "unavailable"
            imageName = $RequestedImage
            imageId = $null
            digest = $null
            reason = "Docker image metadata is invalid"
        }
    }
}

function Get-MigrationIdentity {
    $migrationRoot = Join-Path $script:repoRoot "database\migrations"
    $migrationFiles = @(Get-ChildItem -LiteralPath $migrationRoot `
            -Filter "*.sql" -File -ErrorAction SilentlyContinue |
            Sort-Object Name)
    $migrations = @($migrationFiles | ForEach-Object {
            $migrationHash = $null
            $migrationStatus = "unavailable"
            $migrationReason = "Migration SHA-256 unavailable"
            try {
                $migrationHash = (Get-FileHash -LiteralPath $_.FullName `
                        -Algorithm SHA256).Hash.ToUpperInvariant()
                $migrationStatus = "available"
                $migrationReason = $null
            } catch {
            }
            [ordered]@{
                path = Get-RelativeRepositoryPath -Path $_.FullName
                sha256 = $migrationHash
                status = $migrationStatus
                reason = $migrationReason
            }
        })
    $unavailableMigrations = @($migrations | Where-Object {
            $_.status -ne "available"
        }).Count
    return [ordered]@{
        status = if ($migrations.Count -eq 0) {
            "unavailable"
        } elseif ($unavailableMigrations -eq 0) {
            "available"
        } else {
            "partial"
        }
        count = $migrations.Count
        migrations = $migrations
    }
}

if (Test-Path -LiteralPath $outputDirectory) {
    throw "Release manifest directory already exists: $outputDirectory"
}
New-Item -ItemType Directory -Path $outputDirectory | Out-Null

Push-Location $repoRoot
try {
    $gitIdentity = Get-GitIdentity
    $buildIdentity = Get-BuildIdentity
    $dockerContext = Get-LocalDockerContext
    $dockerEnvironment = Get-DockerEnvironment -DockerContext $dockerContext
    $dockerImage = Get-DockerImageIdentity `
        -DockerContext $dockerContext -RequestedImage $ImageName
    $migrationIdentity = Get-MigrationIdentity

    $sectionStatuses = @(
        $gitIdentity.status,
        $buildIdentity.status,
        $dockerImage.status,
        $migrationIdentity.status,
        $dockerEnvironment.dockerStatus,
        $dockerEnvironment.composeStatus
    )
    $manifestStatus = if (@($sectionStatuses | Where-Object {
                $_ -ne "available"
            }).Count -eq 0) {
        "complete"
    } else {
        "partial"
    }

    $manifest = [ordered]@{
        schemaVersion = "1.0"
        runId = $runId
        generatedAt = (Get-Date).ToString("o")
        status = $manifestStatus
        git = $gitIdentity
        build = $buildIdentity
        docker = $dockerImage
        database = $migrationIdentity
        environment = [ordered]@{
            dockerContext = $dockerContext
            dockerVersion = [ordered]@{
                status = $dockerEnvironment.dockerStatus
                client = $dockerEnvironment.dockerClientVersion
                server = $dockerEnvironment.dockerServerVersion
            }
            composeVersion = [ordered]@{
                status = $dockerEnvironment.composeStatus
                value = $dockerEnvironment.composeVersion
            }
        }
    }

    $json = $manifest | ConvertTo-Json -Depth 10
    Set-Content -LiteralPath $outputPath -Value $json -Encoding UTF8
    Write-Host "Release artifact manifest generated"
    Write-Host "Status: $manifestStatus"
    Write-Host "Output: $outputPath"
} finally {
    Pop-Location
}
