[CmdletBinding()]
param(
    [AllowEmptyString()]
    [string]$ReleaseVersion = "",

    [ValidateRange(30, 600)]
    [int]$DockerTimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$runId = "$(Get-Date -Format 'yyyyMMdd-HHmmss-fff')-$(([Guid]::NewGuid().ToString('N')).Substring(0, 8))".ToLowerInvariant()
$releaseGateRoot = Join-Path $repoRoot "target\release-gate"
$dockerRuntimeRoot = Join-Path $repoRoot "target\docker-runtime"
$releaseArtifactRoot = Join-Path $repoRoot "target\release"
$releaseManifestRoot = Join-Path $repoRoot "target\release-manifest"
$finalEvidenceDir = Join-Path $releaseGateRoot $runId
$workingEvidenceDir = Join-Path ([System.IO.Path]::GetTempPath()) `
    "gameexchange-release-gate-$runId"
$preservedEvidenceDir = Join-Path ([System.IO.Path]::GetTempPath()) `
    "gameexchange-release-gate-preserved-$runId"
$runtimeScript = Join-Path $repoRoot "tools\docker\verify-compose-runtime.ps1"
$manifestScript = Join-Path $repoRoot `
    "tools\release\generate-artifact-manifest.ps1"
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
$startedAt = (Get-Date).ToString("o")

$script:currentGate = "Initialization"
$script:validatedReleaseVersion = ""
$script:commitHash = ""
$script:branch = ""
$script:warPath = ""
$script:warSha256 = ""
$script:imageName = ""
$script:imageId = ""
$script:imageDigest = ""
$script:runtimeEvidence = ""
$script:manifestEvidence = ""
$script:failure = $null

function Convert-NativeOutputToText {
    param([object[]]$Output)

    if ($null -eq $Output) {
        return ""
    }
    return (($Output | ForEach-Object { $_.ToString() }) -join
            [Environment]::NewLine).Trim()
}

function Write-Evidence {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowEmptyString()][string]$Value
    )

    Set-Content -LiteralPath (Join-Path $script:workingEvidenceDir $Name) `
        -Value $Value -Encoding UTF8
}

function Invoke-MavenBuild {
    $preservedDirectories = @()
    try {
        foreach ($evidenceRoot in @(
                $script:releaseGateRoot,
                $script:dockerRuntimeRoot,
                $script:releaseArtifactRoot,
                $script:releaseManifestRoot)) {
            if (-not (Test-Path -LiteralPath $evidenceRoot)) {
                continue
            }
            if (-not (Test-Path -LiteralPath $script:preservedEvidenceDir)) {
                New-Item -ItemType Directory `
                    -Path $script:preservedEvidenceDir | Out-Null
            }
            $directoryName = Split-Path -Leaf $evidenceRoot
            $backupPath = Join-Path $script:preservedEvidenceDir $directoryName
            Copy-Item -LiteralPath $evidenceRoot `
                -Destination $backupPath -Recurse
            $preservedDirectories += [pscustomobject]@{
                Source = $backupPath
                Destination = $evidenceRoot
            }
        }

        Invoke-LoggedCommand -FilePath $script:mavenWrapper -Arguments @(
            "--batch-mode",
            "--no-transfer-progress",
            "clean",
            "verify"
        ) -LogName "maven-verify.log" | Out-Null
    } finally {
        foreach ($preserved in $preservedDirectories) {
            New-Item -ItemType Directory -Path $preserved.Destination `
                -Force | Out-Null
            Copy-Item -Path (Join-Path $preserved.Source "*") `
                -Destination $preserved.Destination -Recurse -Force
        }
        if (Test-Path -LiteralPath $script:preservedEvidenceDir) {
            Remove-Item -LiteralPath $script:preservedEvidenceDir `
                -Recurse -Force
        }
    }
}

function Publish-CurrentEvidence {
    if (Test-Path -LiteralPath $script:finalEvidenceDir) {
        throw "Release Gate evidence already exists: $script:finalEvidenceDir"
    }
    New-Item -ItemType Directory -Path $script:releaseGateRoot `
        -Force | Out-Null
    Move-Item -LiteralPath $script:workingEvidenceDir `
        -Destination $script:finalEvidenceDir
}

function Invoke-LoggedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$LogName
    )

    Get-Command -Name $FilePath -ErrorAction Stop | Out-Null
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $FilePath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $text = Convert-NativeOutputToText -Output $output
    $commandLine = "$FilePath $($Arguments -join ' ')".Trim()
    Write-Evidence -Name $LogName -Value (
        "command=$commandLine`nexitCode=$exitCode`n`n$text")

    if (-not [string]::IsNullOrWhiteSpace($text)) {
        Write-Host $text
    }
    if ($exitCode -ne 0) {
        throw "$commandLine failed with exit code $exitCode"
    }
    return $text
}

function Get-TestSummary {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ReportDirectory
    )

    $reports = @(Get-ChildItem -LiteralPath $ReportDirectory `
            -Filter "TEST-*.xml" -File -ErrorAction SilentlyContinue)
    if ($reports.Count -eq 0) {
        throw "$Name reports are missing: $ReportDirectory"
    }

    $tests = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    foreach ($report in $reports) {
        [xml]$document = Get-Content -Raw -LiteralPath $report.FullName
        $suite = $document.testsuite
        if ($null -eq $suite) {
            throw "Invalid test report: $($report.FullName)"
        }
        $tests += [int]$suite.tests
        $failures += [int]$suite.failures
        $errors += [int]$suite.errors
        $skipped += [int]$suite.skipped
    }

    return [pscustomobject]@{
        Name = $Name
        Suites = $reports.Count
        Tests = $tests
        Failures = $failures
        Errors = $errors
        Skipped = $skipped
    }
}

function Assert-TestSummary {
    param([Parameter(Mandatory = $true)]$Summary)

    if ($Summary.Tests -le 0) {
        throw "$($Summary.Name) did not execute any tests"
    }
    if ($Summary.Failures -ne 0 -or $Summary.Errors -ne 0) {
        throw ("$($Summary.Name) failed: failures=$($Summary.Failures), " +
                "errors=$($Summary.Errors)")
    }
    if ($Summary.Skipped -ne 0) {
        throw "$($Summary.Name) contains $($Summary.Skipped) skipped tests"
    }
}

function Format-TestSummary {
    param([Parameter(Mandatory = $true)]$Summary)

    return ("$($Summary.Name): suites=$($Summary.Suites), " +
            "tests=$($Summary.Tests), failures=$($Summary.Failures), " +
            "errors=$($Summary.Errors), skipped=$($Summary.Skipped)")
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

function Get-MavenProjectIdentity {
    $pomPath = Join-Path $script:repoRoot "pom.xml"
    try {
        [xml]$pom = Get-Content -Raw -LiteralPath $pomPath
        $namespace = [System.Xml.XmlNamespaceManager]::new($pom.NameTable)
        $namespace.AddNamespace("m", $pom.DocumentElement.NamespaceURI)
        $artifactIdNode = $pom.SelectSingleNode(
            "/m:project/m:artifactId", $namespace)
        $versionNode = $pom.SelectSingleNode(
            "/m:project/m:version", $namespace)
    } catch {
        throw "Could not read Maven project identity from pom.xml"
    }
    if ($null -eq $artifactIdNode -or $null -eq $versionNode -or
        [string]::IsNullOrWhiteSpace($artifactIdNode.InnerText) -or
        [string]::IsNullOrWhiteSpace($versionNode.InnerText)) {
        throw "Maven artifactId or version is missing from pom.xml"
    }
    return [pscustomobject]@{
        ArtifactId = $artifactIdNode.InnerText.Trim()
        Version = $versionNode.InnerText.Trim()
    }
}

function Get-DockerImageIdentity {
    param([Parameter(Mandatory = $true)][string]$ImageName)

    $inspectJson = Invoke-LoggedCommand -FilePath "docker" `
        -Arguments @(
            "image", "inspect", "--format={{json .}}", $ImageName
        ) -LogName "docker-image-inspect.log"
    try {
        $image = $inspectJson | ConvertFrom-Json
    } catch {
        throw "Docker image inspect returned invalid JSON"
    }

    $imageId = [string]$image.Id
    if ($imageId -notmatch '^sha256:[0-9a-f]{64}$') {
        throw "Docker image ID is invalid: $imageId"
    }

    $repoDigests = @($image.RepoDigests | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_)
        })
    $repoDigest = if ($repoDigests.Count -gt 0) {
        [string]$repoDigests[0]
    } else {
        $null
    }
    $hasDescriptor = $null -ne $image.PSObject.Properties['Descriptor']
    $descriptorDigest = if ($hasDescriptor -and
        $null -ne $image.Descriptor) {
        [string]$image.Descriptor.digest
    } else {
        $null
    }
    $repoDigestMatch = if ($null -ne $repoDigest) {
        [regex]::Match($repoDigest, '@(?<digest>sha256:[0-9a-f]{64})$')
    } else {
        $null
    }
    $digest = if ($descriptorDigest -match '^sha256:[0-9a-f]{64}$') {
        $descriptorDigest
    } elseif ($null -ne $repoDigestMatch -and $repoDigestMatch.Success) {
        $repoDigestMatch.Groups['digest'].Value
    } else {
        throw "Docker image digest is unavailable"
    }
    if ($null -ne $repoDigestMatch -and $repoDigestMatch.Success -and
        $repoDigestMatch.Groups['digest'].Value -ne $digest) {
        throw "Docker descriptor digest and repository digest do not match"
    }

    return [pscustomobject]@{
        Name = $ImageName
        Id = $imageId
        Digest = $digest
        RepoDigest = $repoDigest
    }
}

function Get-RequiredEvidenceValue {
    param(
        [Parameter(Mandatory = $true)][string]$Content,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $matches = [regex]::Matches(
        $Content,
        "(?m)^$([regex]::Escape($Name))=(?<value>[^`r`n]*)$")
    if ($matches.Count -ne 1 -or
        [string]::IsNullOrWhiteSpace($matches[0].Groups['value'].Value)) {
        throw "Evidence field $Name is missing or ambiguous"
    }
    return $matches[0].Groups['value'].Value.Trim()
}

function Write-GateSummary {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("PASSED", "BLOCKED")]
        [string]$Status,
        [AllowEmptyString()][string]$Reason
    )

    $finishedAt = (Get-Date).ToString("o")
    Write-Evidence -Name "summary.txt" -Value (
        "status=$Status`n" +
        "runId=$script:runId`n" +
        "startedAt=$script:startedAt`n" +
        "finishedAt=$finishedAt`n" +
        "gate=$script:currentGate`n" +
        "releaseVersion=$script:validatedReleaseVersion`n" +
        "commit=$script:commitHash`n" +
        "branch=$script:branch`n" +
        "war=$script:warPath`n" +
        "warSha256=$script:warSha256`n" +
        "image=$script:imageName`n" +
        "imageId=$script:imageId`n" +
        "imageDigest=$script:imageDigest`n" +
        "runtimeEvidence=$script:runtimeEvidence`n" +
        "manifestEvidence=$script:manifestEvidence`n" +
        "reason=$Reason")
}

if (Test-Path -LiteralPath $finalEvidenceDir) {
    throw "Release Gate evidence directory already exists: $finalEvidenceDir"
}
if (Test-Path -LiteralPath $workingEvidenceDir) {
    throw "Release Gate working directory already exists: $workingEvidenceDir"
}
New-Item -ItemType Directory -Path $workingEvidenceDir | Out-Null

Push-Location $repoRoot
try {
    $script:currentGate = "Git Identity Check"
    $repositoryPath = (Invoke-LoggedCommand -FilePath "git" `
            -Arguments @("rev-parse", "--show-toplevel") `
            -LogName "git-root.txt").Trim()
    if (-not [System.IO.Path]::GetFullPath($repositoryPath).Equals(
            [System.IO.Path]::GetFullPath($repoRoot),
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Git repository root mismatch: $repositoryPath"
    }

    $script:commitHash = (Invoke-LoggedCommand -FilePath "git" `
            -Arguments @("rev-parse", "HEAD") `
            -LogName "git-commit.txt").Trim()
    if ($script:commitHash -notmatch '^[0-9a-f]{40}$') {
        throw "Git commit hash is invalid: $script:commitHash"
    }

    $script:branch = (Invoke-LoggedCommand -FilePath "git" `
            -Arguments @("rev-parse", "--abbrev-ref", "HEAD") `
            -LogName "git-branch.txt").Trim()
    $workingTree = Invoke-LoggedCommand -FilePath "git" `
        -Arguments @("status", "--porcelain=v1", "--untracked-files=all") `
        -LogName "git-status.txt"
    if (-not [string]::IsNullOrWhiteSpace($workingTree)) {
        throw "Working tree is not clean"
    }
    Write-Evidence -Name "git-identity.txt" -Value (
        "commit=$script:commitHash`nbranch=$script:branch`nworkingTree=clean")

    $script:currentGate = "Release Version Check"
    if ([string]::IsNullOrWhiteSpace($ReleaseVersion) -or
        $ReleaseVersion -notmatch
            '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z][0-9A-Za-z.-]*)?$') {
        throw "ReleaseVersion must be a Docker-safe semantic version"
    }
    $mavenProject = Get-MavenProjectIdentity
    if ($mavenProject.Version -ne $ReleaseVersion) {
        throw ("ReleaseVersion does not match pom.xml: " +
                "expected=$ReleaseVersion actual=$($mavenProject.Version)")
    }
    $script:validatedReleaseVersion = $ReleaseVersion
    Write-Evidence -Name "release-version.txt" -Value (
        "releaseVersion=$script:validatedReleaseVersion`n" +
        "mavenArtifactId=$($mavenProject.ArtifactId)`n" +
        "mavenVersion=$($mavenProject.Version)")

    $script:currentGate = "Environment Check"
    if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
        throw "Maven Wrapper is missing: $mavenWrapper"
    }
    if (-not (Test-Path -LiteralPath $manifestScript -PathType Leaf)) {
        throw "Artifact Manifest generator is missing: $manifestScript"
    }
    Invoke-LoggedCommand -FilePath "java" -Arguments @("-version") `
        -LogName "java-version.txt" | Out-Null
    Invoke-LoggedCommand -FilePath $mavenWrapper -Arguments @("--version") `
        -LogName "maven-version.txt" | Out-Null
    Invoke-LoggedCommand -FilePath "docker" -Arguments @("version") `
        -LogName "docker-version.txt" | Out-Null
    Invoke-LoggedCommand -FilePath "docker" `
        -Arguments @("compose", "version") `
        -LogName "docker-compose-version.txt" | Out-Null

    $script:currentGate = "Build Verification"
    Invoke-MavenBuild

    $script:currentGate = "Test Verification"
    $unitSummary = Get-TestSummary -Name "Unit Test" `
        -ReportDirectory (Join-Path $repoRoot "target\surefire-reports")
    $integrationSummary = Get-TestSummary -Name "Integration Test" `
        -ReportDirectory (Join-Path $repoRoot "target\failsafe-reports")
    Assert-TestSummary -Summary $unitSummary
    Assert-TestSummary -Summary $integrationSummary
    Write-Evidence -Name "test-summary.txt" -Value (
        "$(Format-TestSummary -Summary $unitSummary)`n" +
        "$(Format-TestSummary -Summary $integrationSummary)")

    $script:currentGate = "Artifact Evidence"
    $warFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "target") `
            -Filter "*.war" -File)
    if ($warFiles.Count -ne 1) {
        throw "Expected exactly one WAR, found $($warFiles.Count)"
    }
    $war = $warFiles[0]
    $script:warPath = Get-RelativeRepositoryPath -Path $war.FullName
    $script:warSha256 = (Get-FileHash -LiteralPath $war.FullName `
            -Algorithm SHA256).Hash.ToUpperInvariant()
    Write-Evidence -Name "war-artifact.txt" -Value (
        "path=$script:warPath`nsize=$($war.Length)`nsha256=$script:warSha256")

    $script:currentGate = "Docker Image Build"
    $commitPrefix = $script:commitHash.Substring(0, 12)
    $script:imageName = (
        "gameexchange-rc:$script:validatedReleaseVersion-$commitPrefix-$runId")
    Invoke-LoggedCommand -FilePath "docker" -Arguments @(
        "build",
        "--file", (Join-Path $repoRoot "Dockerfile"),
        "--tag", $script:imageName,
        $repoRoot
    ) -LogName "docker-image-build.log" | Out-Null
    $imageIdentity = Get-DockerImageIdentity `
        -ImageName $script:imageName
    $script:imageId = $imageIdentity.Id
    $script:imageDigest = $imageIdentity.Digest
    Write-Evidence -Name "image-identity.txt" -Value (
        "imageName=$script:imageName`n" +
        "imageId=$script:imageId`n" +
        "digest=$script:imageDigest`n" +
        "repoDigest=$($imageIdentity.RepoDigest)")

    $script:currentGate = "Docker Runtime Gate"
    if (-not (Test-Path -LiteralPath $runtimeScript -PathType Leaf)) {
        throw "Docker Runtime verifier is missing: $runtimeScript"
    }
    $runtimeOutput = Invoke-LoggedCommand -FilePath "powershell.exe" `
        -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", $runtimeScript,
            "-ImageName", $script:imageName,
            "-TimeoutSeconds", $DockerTimeoutSeconds.ToString()
        ) -LogName "docker-runtime.log"
    $runtimeMatches = [regex]::Matches(
        $runtimeOutput,
        '(?m)^Starting isolated project gameexchange-rt-(?<runId>[0-9a-z-]+) on port \d+\s*$')
    if ($runtimeMatches.Count -ne 1) {
        throw "Could not identify exactly one Docker Runtime evidence run"
    }
    $runtimeRunId = $runtimeMatches[0].Groups['runId'].Value
    $runtimeEvidencePath = Join-Path $repoRoot `
        "target\docker-runtime\$runtimeRunId"
    $runtimeResultPath = Join-Path $runtimeEvidencePath "result.txt"
    if (-not (Test-Path -LiteralPath $runtimeResultPath -PathType Leaf)) {
        throw "Docker Runtime result evidence is missing: $runtimeResultPath"
    }
    $runtimeResult = Get-Content -Raw -LiteralPath $runtimeResultPath
    if ($runtimeResult -notmatch '(?m)^status=PASS\s*$') {
        throw "Docker Runtime result is not PASS"
    }
    $runtimeImageName = Get-RequiredEvidenceValue `
        -Content $runtimeResult -Name "image"
    $runtimeImageId = Get-RequiredEvidenceValue `
        -Content $runtimeResult -Name "imageId"
    if ($runtimeImageName -ne $script:imageName) {
        throw "Docker Runtime used a different image name"
    }
    if ($runtimeImageId -ne $script:imageId) {
        throw "Docker Runtime used a different image ID"
    }
    $script:runtimeEvidence = Get-RelativeRepositoryPath `
        -Path $runtimeEvidencePath
    Write-Evidence -Name "docker-runtime-evidence.txt" -Value (
        "runId=$runtimeRunId`npath=$script:runtimeEvidence`nstatus=PASS`n" +
        "imageName=$runtimeImageName`nimageId=$runtimeImageId")

    $script:currentGate = "Artifact Manifest"
    $manifestOutput = Invoke-LoggedCommand -FilePath "powershell.exe" `
        -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", $manifestScript,
            "-ReleaseVersion", $script:validatedReleaseVersion,
            "-ImageName", $script:imageName
        ) -LogName "artifact-manifest.log"
    $manifestMatches = [regex]::Matches(
        $manifestOutput,
        '(?m)^Output:\s*(?<path>[^\r\n]+?)\s*$')
    if ($manifestMatches.Count -ne 1) {
        throw "Could not identify exactly one Artifact Manifest output"
    }
    $manifestPath = $manifestMatches[0].Groups['path'].Value.Trim()
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Artifact Manifest is missing: $manifestPath"
    }
    try {
        $manifest = Get-Content -Raw -LiteralPath $manifestPath |
            ConvertFrom-Json
    } catch {
        throw "Artifact Manifest contains invalid JSON"
    }
    if ($manifest.status -ne "complete") {
        throw "Artifact Manifest status is not complete: $($manifest.status)"
    }
    if ($manifest.release.version -ne $script:validatedReleaseVersion -or
        $manifest.release.artifactIdentity.mavenVersion -ne
            $script:validatedReleaseVersion -or
        $manifest.release.artifactIdentity.warVersion -ne
            $script:validatedReleaseVersion -or
        $manifest.release.artifactIdentity.dockerImageVersion -ne
            $script:validatedReleaseVersion) {
        throw "Artifact Manifest release identity does not match the release"
    }
    if ($manifest.git.commitHash -ne $script:commitHash -or
        $manifest.git.workingTreeStatus -ne "clean") {
        throw "Artifact Manifest Git identity does not match the release"
    }
    if ($manifest.build.war.path -ne $script:warPath -or
        $manifest.build.war.sha256 -ne $script:warSha256) {
        throw "Artifact Manifest WAR identity does not match the release"
    }
    if ($manifest.docker.imageName -ne $script:imageName -or
        $manifest.docker.imageId -ne $script:imageId) {
        throw "Artifact Manifest image identity does not match the release"
    }
    $manifestDigestMatch = [regex]::Match(
        [string]$manifest.docker.digest,
        '(?<digest>sha256:[0-9a-f]{64})$')
    if (-not $manifestDigestMatch.Success -or
        $manifestDigestMatch.Groups['digest'].Value -ne
            $script:imageDigest) {
        throw "Artifact Manifest image digest does not match the release"
    }
    $migrationFiles = @(Get-ChildItem -LiteralPath (
            Join-Path $repoRoot "database\migrations") `
            -Filter "*.sql" -File)
    if ([int]$manifest.database.count -ne $migrationFiles.Count -or
        @($manifest.database.migrations | Where-Object {
                $_.status -ne "available" -or
                $_.sha256 -notmatch '^[0-9A-F]{64}$'
            }).Count -ne 0) {
        throw "Artifact Manifest migration identity is incomplete"
    }
    $script:manifestEvidence = Get-RelativeRepositoryPath `
        -Path $manifestPath
    Write-Evidence -Name "artifact-manifest-evidence.txt" -Value (
        "path=$script:manifestEvidence`nstatus=complete`n" +
        "releaseVersion=$script:validatedReleaseVersion`n" +
        "commit=$script:commitHash`nwarSha256=$script:warSha256`n" +
        "imageName=$script:imageName`nimageId=$script:imageId`n" +
        "imageDigest=$script:imageDigest`n" +
        "migrationCount=$($manifest.database.count)")

    $script:currentGate = "Evidence Summary"
    Write-GateSummary -Status "PASSED" -Reason ""
} catch {
    $script:failure = $_
    $reason = $_.Exception.Message.Replace("`r", " ").Replace("`n", " ")
    Write-Evidence -Name "failure.txt" -Value (
        "status=BLOCKED`ngate=$script:currentGate`nreason=$reason")
    Write-GateSummary -Status "BLOCKED" -Reason $reason
    Write-Error -ErrorAction Continue (
        "Release Gate BLOCKED at $script:currentGate`: $reason")
} finally {
    Pop-Location
}

try {
    Publish-CurrentEvidence
} catch {
    $publishFailure = $_
    $script:currentGate = "Evidence Publication"
    $publishReason = $publishFailure.Exception.Message.Replace(
        "`r", " ").Replace("`n", " ")
    if ($null -eq $script:failure) {
        $script:failure = $publishFailure
    }
    if (Test-Path -LiteralPath $workingEvidenceDir) {
        Write-Evidence -Name "failure.txt" -Value (
            "status=BLOCKED`ngate=$script:currentGate`nreason=$publishReason")
        Write-GateSummary -Status "BLOCKED" -Reason $publishReason
    }
    Write-Error -ErrorAction Continue (
        "Release Gate evidence publication failed: " +
        $publishReason)
    Write-Host "Unpublished evidence: $workingEvidenceDir"
}

if ($null -ne $script:failure) {
    Write-Host "Release Gate BLOCKED"
    if (Test-Path -LiteralPath $finalEvidenceDir) {
        Write-Host "Evidence: $finalEvidenceDir"
    }
    exit 1
}

Write-Host "Release Gate PASSED"
Write-Host "Evidence: $finalEvidenceDir"
