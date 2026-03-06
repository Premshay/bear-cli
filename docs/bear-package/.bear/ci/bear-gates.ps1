$ErrorActionPreference = 'Stop'

$mode = 'enforce'
$baseShaOverride = $null
$blocksPath = 'bear.blocks.yaml'
for ($i = 0; $i -lt $args.Count; $i++) {
    switch ($args[$i]) {
        '--mode' {
            if ($i + 1 -ge $args.Count) {
                throw 'missing value for --mode'
            }
            $mode = $args[$i + 1]
            $i++
        }
        '--base-sha' {
            if ($i + 1 -ge $args.Count) {
                throw 'missing value for --base-sha'
            }
            $baseShaOverride = $args[$i + 1]
            $i++
        }
        '--blocks' {
            if ($i + 1 -ge $args.Count) {
                throw 'missing value for --blocks'
            }
            $blocksPath = $args[$i + 1]
            $i++
        }
        default {
            throw ('unsupported argument: ' + $args[$i])
        }
    }
}
if ($mode -ne 'enforce' -and $mode -ne 'observe') {
    throw ('unsupported value for --mode: ' + $mode)
}

function New-OrderedArray($value) {
    if ($null -eq $value) {
        return @()
    }
    return @($value)
}

function Get-PropertyValue($object, $name) {
    if ($null -eq $object) {
        return $null
    }
    $property = $object.PSObject.Properties[$name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Normalize-Lines($text) {
    if ($null -eq $text -or $text.Length -eq 0) {
        return @()
    }
    $lines = ($text -replace "`r`n", "`n" -replace "`r", "`n").Split("`n")
    while ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -eq '') {
        if ($lines.Count -eq 1) {
            return @()
        }
        $lines = $lines[0..($lines.Count - 2)]
    }
    return $lines
}

function New-InvalidFooter() {
    return [ordered]@{
        valid = $false
        code = 'WRAPPER_FOOTER_INVALID'
        path = 'stderr.footer'
        remediation = 'Inspect captured BEAR stderr and restore deterministic failure footer.'
    }
}

function Parse-FailureFooter($text, $exitCode) {
    if ($exitCode -eq 0) {
        return [ordered]@{
            valid = $true
            code = $null
            path = $null
            remediation = $null
        }
    }
    $lines = Normalize-Lines $text
    if ($lines.Count -lt 3) {
        return New-InvalidFooter
    }
    $tail = $lines[($lines.Count - 3)..($lines.Count - 1)]
    if ($tail[0] -notmatch '^CODE=(.+)$') {
        return New-InvalidFooter
    }
    $code = $Matches[1]
    if ($tail[1] -notmatch '^PATH=(.+)$') {
        return New-InvalidFooter
    }
    $path = $Matches[1]
    if ($tail[2] -notmatch '^REMEDIATION=(.+)$') {
        return New-InvalidFooter
    }
    $remediation = $Matches[1]
    return [ordered]@{
        valid = $true
        code = $code
        path = $path
        remediation = $remediation
    }
}

function Try-ParseAgentJson($text) {
    if ([string]::IsNullOrWhiteSpace($text)) {
        return [ordered]@{
            json = $null
            valid = $false
        }
    }
    try {
        return [ordered]@{
            json = ($text | ConvertFrom-Json)
            valid = $true
        }
    } catch {
        return [ordered]@{
            json = $null
            valid = $false
        }
    }
}

function Get-CheckClasses($exitCode, $footerValid) {
    if (-not $footerValid) {
        return @('CI_INTERNAL_ERROR')
    }
    switch ($exitCode) {
        70 { return @('CI_INTERNAL_ERROR') }
        64 { return @('CI_VALIDATION_OR_USAGE_ERROR') }
        2 { return @('CI_VALIDATION_OR_USAGE_ERROR') }
        74 { return @('CI_IO_GIT_ERROR') }
        7 { return @('CI_POLICY_BYPASS_ATTEMPT') }
        6 { return @('CI_GOVERNANCE_DRIFT') }
        4 { return @('CI_TEST_FAILURE') }
        3 { return @('CI_GOVERNANCE_DRIFT') }
        0 { return @('CI_NO_STRUCTURAL_CHANGE') }
        default { return @('CI_INTERNAL_ERROR') }
    }
}

function Get-PrClasses($exitCode, $footerValid, $telemetry) {
    if (-not $footerValid) {
        return @('CI_INTERNAL_ERROR')
    }
    $primary = 'CI_NO_STRUCTURAL_CHANGE'
    switch ($exitCode) {
        70 { $primary = 'CI_INTERNAL_ERROR' }
        64 { $primary = 'CI_VALIDATION_OR_USAGE_ERROR' }
        2 { $primary = 'CI_VALIDATION_OR_USAGE_ERROR' }
        74 { $primary = 'CI_IO_GIT_ERROR' }
        7 { $primary = 'CI_POLICY_BYPASS_ATTEMPT' }
        5 { $primary = 'CI_BOUNDARY_EXPANSION' }
        0 { $primary = 'CI_NO_STRUCTURAL_CHANGE' }
        default { $primary = 'CI_INTERNAL_ERROR' }
    }
    $classes = @($primary)
    if ($telemetry.available -and $telemetry.hasDependencyPowerExpansion) {
        $classes += 'CI_DEPENDENCY_POWER_EXPANSION'
    }
    return @($classes | Select-Object -Unique)
}

function Get-PrTelemetry($agentJson) {
    $empty = [ordered]@{
        available = $false
        deltas = @()
        governanceSignals = @()
        boundaryDeltaIds = @()
        hasDependencyPowerExpansion = $false
    }
    $extensions = Get-PropertyValue $agentJson 'extensions'
    $prGovernance = Get-PropertyValue $extensions 'prGovernance'
    if ($null -eq $prGovernance) {
        return $empty
    }
    $deltas = New-OrderedArray (Get-PropertyValue $prGovernance 'deltas')
    $signals = New-OrderedArray (Get-PropertyValue $prGovernance 'governanceSignals')
    $boundaryDeltaIds = @()
    $hasDependencyPowerExpansion = $false
    foreach ($delta in $deltas) {
        $deltaClass = [string](Get-PropertyValue $delta 'class')
        $deltaCategory = [string](Get-PropertyValue $delta 'category')
        $deltaId = [string](Get-PropertyValue $delta 'deltaId')
        if ($deltaClass -eq 'BOUNDARY_EXPANDING') {
            if ([string]::IsNullOrWhiteSpace($deltaId)) {
                return $empty
            }
            $boundaryDeltaIds += $deltaId
            if ($deltaCategory -eq 'ALLOWED_DEPS') {
                $hasDependencyPowerExpansion = $true
            }
        }
    }
    return [ordered]@{
        available = $true
        deltas = @($deltas)
        governanceSignals = @($signals)
        boundaryDeltaIds = @($boundaryDeltaIds | Sort-Object)
        hasDependencyPowerExpansion = $hasDependencyPowerExpansion
    }
}

function Read-AllowFile($path) {
    if (-not (Test-Path $path)) {
        return [ordered]@{
            valid = $true
            entries = @()
        }
    }
    try {
        $json = Get-Content $path -Raw | ConvertFrom-Json
        return [ordered]@{
            valid = $true
            entries = New-OrderedArray (Get-PropertyValue $json 'entries')
        }
    } catch {
        return [ordered]@{
            valid = $false
            entries = @()
        }
    }
}

function Compare-ExactSet($left, $right) {
    $a = @($left | Sort-Object)
    $b = @($right | Sort-Object)
    if ($a.Count -ne $b.Count) {
        return $false
    }
    for ($index = 0; $index -lt $a.Count; $index++) {
        if ($a[$index] -ne $b[$index]) {
            return $false
        }
    }
    return $true
}

function Evaluate-Allow($modeValue, $prResult, $telemetry, $resolvedBaseSha, $allowFilePath) {
    $observed = @($telemetry.boundaryDeltaIds)
    if ($modeValue -ne 'enforce' -or $null -eq $prResult -or $prResult.exitCode -ne 5) {
        return [ordered]@{
            status = 'not-needed'
            reason = $null
            observedDeltaIds = $observed
        }
    }
    if (-not $telemetry.available -or $observed.Count -eq 0) {
        return [ordered]@{
            status = 'unavailable'
            reason = 'PR_GOVERNANCE_UNAVAILABLE'
            observedDeltaIds = $observed
        }
    }
    $allowData = Read-AllowFile $allowFilePath
    if (-not $allowData.valid) {
        return [ordered]@{
            status = 'unavailable'
            reason = 'ALLOW_FILE_INVALID'
            observedDeltaIds = $observed
        }
    }
    if ($allowData.entries.Count -eq 0) {
        return [ordered]@{
            status = 'mismatch'
            reason = 'ALLOW_FILE_MISSING'
            observedDeltaIds = $observed
        }
    }
    foreach ($entry in $allowData.entries) {
        $entryBaseSha = [string](Get-PropertyValue $entry 'baseSha')
        $entryDeltaIds = New-OrderedArray (Get-PropertyValue $entry 'deltaIds')
        if ($entryBaseSha -eq $resolvedBaseSha -and (Compare-ExactSet $entryDeltaIds $observed)) {
            return [ordered]@{
                status = 'matched'
                reason = $null
                observedDeltaIds = $observed
            }
        }
    }
    return [ordered]@{
        status = 'mismatch'
        reason = 'ALLOW_FILE_MISMATCH'
        observedDeltaIds = $observed
    }
}

function Resolve-BaseSha($overrideValue, $repoRootPath) {
    if (-not [string]::IsNullOrWhiteSpace($overrideValue)) {
        return [ordered]@{
            resolved = $true
            value = $overrideValue
        }
    }
    $eventPath = $env:GITHUB_EVENT_PATH
    if ([string]::IsNullOrWhiteSpace($eventPath) -or -not (Test-Path $eventPath)) {
        return [ordered]@{
            resolved = $false
            value = $null
        }
    }
    try {
        $eventJson = Get-Content $eventPath -Raw | ConvertFrom-Json
    } catch {
        return [ordered]@{
            resolved = $false
            value = $null
        }
    }
    $pullRequest = Get-PropertyValue $eventJson 'pull_request'
    if ($null -ne $pullRequest) {
        $base = Get-PropertyValue (Get-PropertyValue $pullRequest 'base') 'sha'
        if (-not [string]::IsNullOrWhiteSpace($base)) {
            return [ordered]@{
                resolved = $true
                value = [string]$base
            }
        }
        return [ordered]@{
            resolved = $false
            value = $null
        }
    }
    $before = [string](Get-PropertyValue $eventJson 'before')
    if (-not [string]::IsNullOrWhiteSpace($before) -and $before -ne '0000000000000000000000000000000000000000') {
        return [ordered]@{
            resolved = $true
            value = $before
        }
    }
    $headFallback = (& git -C $repoRootPath rev-parse 'HEAD~1' 2>$null)
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($headFallback)) {
        return [ordered]@{
            resolved = $true
            value = $headFallback.Trim()
        }
    }
    return [ordered]@{
        resolved = $false
        value = $null
    }
}

function Format-CmdArgument($value) {
    if ($null -eq $value) {
        return '""'
    }
    return '"' + ([string]$value).Replace('"', '""') + '"'
}

function Format-ShArgument($value) {
    if ($null -eq $value) {
        return "''"
    }
    return "'" + ([string]$value).Replace("'", "'\''") + "'"
}

function Invoke-BearCommand($label, $commandText, $commandPath, $commandArgs) {
    $stdoutPath = Join-Path $script:tempDir ($label + '.stdout')
    $stderrPath = Join-Path $script:tempDir ($label + '.stderr')
    if ($env:OS -eq 'Windows_NT') {
        $quotedArgs = @($commandArgs | ForEach-Object { Format-CmdArgument $_ }) -join ' '
        $cmdLine = (Format-CmdArgument $commandPath) + $(if ($quotedArgs.Length -gt 0) { ' ' + $quotedArgs } else { '' })
        & cmd.exe /d /c ($cmdLine + ' 1> ' + (Format-CmdArgument $stdoutPath) + ' 2> ' + (Format-CmdArgument $stderrPath)) | Out-Null
        $exitCode = $LASTEXITCODE
    } else {
        $quotedArgs = @($commandArgs | ForEach-Object { Format-ShArgument $_ }) -join ' '
        $shLine = (Format-ShArgument $commandPath) + $(if ($quotedArgs.Length -gt 0) { ' ' + $quotedArgs } else { '' })
        & /bin/sh -c ($shLine + ' 1> ' + (Format-ShArgument $stdoutPath) + ' 2> ' + (Format-ShArgument $stderrPath)) | Out-Null
        $exitCode = $LASTEXITCODE
    }
    $stdoutText = if (Test-Path $stdoutPath) { Get-Content $stdoutPath -Raw } else { '' }
    $stderrText = if (Test-Path $stderrPath) { Get-Content $stderrPath -Raw } else { '' }
    $stdoutHash = if (Test-Path $stdoutPath) { (Get-FileHash -Algorithm SHA256 $stdoutPath).Hash.ToLowerInvariant() } else { $null }
    $stderrHash = if (Test-Path $stderrPath) { (Get-FileHash -Algorithm SHA256 $stderrPath).Hash.ToLowerInvariant() } else { $null }
    $agent = Try-ParseAgentJson $stdoutText
    $footer = Parse-FailureFooter $stderrText $exitCode
    return [ordered]@{
        label = $label
        command = $commandText
        exitCode = $exitCode
        stdoutText = $stdoutText
        stderrText = $stderrText
        stdoutHash = $stdoutHash
        stderrHash = $stderrHash
        agentJson = $agent.json
        footer = $footer
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir '..\..'))
$bearCommand = Join-Path $repoRoot ($(if ($env:OS -eq 'Windows_NT') { '.bear/tools/bear-cli/bin/bear.bat' } else { '.bear/tools/bear-cli/bin/bear' }))
$allowFilePath = Join-Path $repoRoot '.bear/ci/baseline-allow.json'
$reportPath = Join-Path $repoRoot 'build/bear/ci/bear-ci-report.json'
$null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $reportPath)
$script:tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ('bear-ci-' + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Force -Path $script:tempDir
$pushSucceeded = $false
try {
    Push-Location $repoRoot
    $pushSucceeded = $true
    $commandPrefix = $(if ($env:OS -eq 'Windows_NT') { '.bear/tools/bear-cli/bin/bear.bat' } else { '.bear/tools/bear-cli/bin/bear' })
    $checkCommandText = $commandPrefix + ' check --all --project . --blocks ' + $blocksPath + ' --collect=all --agent'
    $checkResult = Invoke-BearCommand 'check' $checkCommandText $bearCommand @('check', '--all', '--project', '.', '--blocks', $blocksPath, '--collect=all', '--agent')
    $checkClasses = Get-CheckClasses $checkResult.exitCode $checkResult.footer.valid

    $baseResolution = Resolve-BaseSha $baseShaOverride $repoRoot
    $prResult = $null
    $prTelemetry = [ordered]@{
        available = $false
        deltas = @()
        governanceSignals = @()
        boundaryDeltaIds = @()
        hasDependencyPowerExpansion = $false
    }
    $prStatus = 'not-run'
    $prReason = $null
    $prClasses = @()
    $commands = @($checkCommandText)

    if ($checkResult.exitCode -eq 5) {
        $prReason = 'UNEXPECTED_CHECK_EXIT'
    } elseif ($checkResult.exitCode -in @(2, 64, 70, 74)) {
        $prReason = 'CHECK_PRECONDITION_FAILURE'
    } elseif (-not $baseResolution.resolved) {
        $prReason = 'BASE_UNRESOLVED'
    } else {
        $prCommandText = $commandPrefix + ' pr-check --all --project . --base ' + $baseResolution.value + ' --blocks ' + $blocksPath + ' --collect=all --agent'
        $commands += $prCommandText
        $prResult = Invoke-BearCommand 'pr-check' $prCommandText $bearCommand @('pr-check', '--all', '--project', '.', '--base', $baseResolution.value, '--blocks', $blocksPath, '--collect=all', '--agent')
        $prTelemetry = Get-PrTelemetry $prResult.agentJson
        $prClasses = Get-PrClasses $prResult.exitCode $prResult.footer.valid $prTelemetry
        $prStatus = 'ran'
    }

    $allowEvaluation = Evaluate-Allow $mode $prResult $prTelemetry $baseResolution.value $allowFilePath

    $decision = 'pass'
    if ($checkResult.exitCode -in @(2, 5, 64, 70, 74)) {
        $decision = 'fail'
    } elseif (-not $baseResolution.resolved) {
        $decision = 'fail'
    } elseif ($null -eq $prResult) {
        $decision = 'fail'
    } elseif ($mode -eq 'observe') {
        if ($prResult.exitCode -in @(2, 64, 70, 74)) {
            $decision = 'fail'
        }
    } elseif ($checkResult.exitCode -ne 0) {
        $decision = 'fail'
    } elseif ($prResult.exitCode -eq 0) {
        $decision = 'pass'
    } elseif ($prResult.exitCode -eq 5 -and $allowEvaluation.status -eq 'matched') {
        $decision = 'allowed-expansion'
    } else {
        $decision = 'fail'
    }

    $checkReport = [ordered]@{
        status = 'ran'
        exitCode = $checkResult.exitCode
        code = $checkResult.footer.code
        path = $checkResult.footer.path
        remediation = $checkResult.footer.remediation
        classes = @($checkClasses)
    }
    $prReport = if ($prStatus -eq 'ran') {
        [ordered]@{
            status = 'ran'
            reason = $null
            exitCode = $prResult.exitCode
            code = $prResult.footer.code
            path = $prResult.footer.path
            remediation = $prResult.footer.remediation
            classes = @($prClasses)
            deltas = @($prTelemetry.deltas)
            governanceSignals = @($prTelemetry.governanceSignals)
        }
    } else {
        [ordered]@{
            status = 'not-run'
            reason = $prReason
            exitCode = $null
            code = $null
            path = $null
            remediation = $null
            classes = @()
            deltas = @()
            governanceSignals = @()
        }
    }

    $report = [ordered]@{
        schemaVersion = 'bear.ci.governance.v1'
        mode = $mode
        resolvedBaseSha = $(if ($baseResolution.resolved) { $baseResolution.value } else { $null })
        commands = @($commands)
        bearRaw = [ordered]@{
            checkAgentJson = $checkResult.agentJson
            prCheckAgentJson = $(if ($null -ne $prResult) { $prResult.agentJson } else { $null })
            checkStdoutHash = $checkResult.stdoutHash
            checkStderrHash = $checkResult.stderrHash
            prCheckStdoutHash = $(if ($null -ne $prResult) { $prResult.stdoutHash } else { $null })
            prCheckStderrHash = $(if ($null -ne $prResult) { $prResult.stderrHash } else { $null })
        }
        check = $checkReport
        prCheck = $prReport
        allowEvaluation = [ordered]@{
            status = $allowEvaluation.status
            reason = $allowEvaluation.reason
            observedDeltaIds = @($allowEvaluation.observedDeltaIds)
        }
        decision = $decision
    }
    $reportJson = $report | ConvertTo-Json -Depth 12 -Compress
    Set-Content -Path $reportPath -Value $reportJson

    $baseDisplay = if ($baseResolution.resolved) { $baseResolution.value } else { '<unresolved>' }
    $checkCodeDisplay = if ($null -eq $checkResult.footer.code) { '-' } else { $checkResult.footer.code }
    $checkClassesDisplay = if ($checkClasses.Count -eq 0) { '-' } else { ($checkClasses -join ',') }
    Write-Output ('MODE=' + $mode + ' DECISION=' + $decision + ' BASE=' + $baseDisplay)
    Write-Output ('CHECK exit=' + $checkResult.exitCode + ' code=' + $checkCodeDisplay + ' classes=' + $checkClassesDisplay)
    if ($prStatus -eq 'ran') {
        $prCodeDisplay = if ($null -eq $prResult.footer.code) { '-' } else { $prResult.footer.code }
        $prClassesDisplay = if ($prClasses.Count -eq 0) { '-' } else { ($prClasses -join ',') }
        Write-Output ('PR-CHECK exit=' + $prResult.exitCode + ' code=' + $prCodeDisplay + ' classes=' + $prClassesDisplay)
    } else {
        Write-Output ('PR-CHECK NOT_RUN: ' + $prReason)
    }

    if ($decision -eq 'fail') {
        exit 1
    }
    exit 0
} finally {
    if ($pushSucceeded) {
        Pop-Location
    }
    if (Test-Path $script:tempDir) {
        Remove-Item -Recurse -Force $script:tempDir
    }
}


