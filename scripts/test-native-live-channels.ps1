param(
    [Parameter(Mandatory = $true)][string]$ApplicationPath,
    [string]$PlaylistUrl = 'https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_us.m3u'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path $repo ('build\native-live-smoke\' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$start = New-Object Diagnostics.ProcessStartInfo
$start.FileName = (Resolve-Path -LiteralPath $ApplicationPath).Path
# Match Nucleus's packaged java.home setting when testing TLS trust-store resolution.
$exeDir = Split-Path -Parent $start.FileName
$start.Arguments = '-Djava.home="' + $exeDir + '" -XX:MissingRegistrationReportingMode=Exit --verify-live-playlist "' + $PlaylistUrl + '"'
$start.WorkingDirectory = $testRoot
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
$start.RedirectStandardError = $true
$start.RedirectStandardOutput = $true
$start.EnvironmentVariables['CINEK_CONFIG_DIR'] = Join-Path $testRoot 'config'
$process = [Diagnostics.Process]::Start($start)
$stdout = $process.StandardOutput.ReadToEndAsync()
$stderr = $process.StandardError.ReadToEndAsync()
try {
    if (-not $process.WaitForExit(45000)) { $process.Kill(); throw 'Native playlist download timed out.' }
    [IO.File]::WriteAllText((Join-Path $testRoot 'stdout.log'), $stdout.Result)
    [IO.File]::WriteAllText((Join-Path $testRoot 'stderr.log'), $stderr.Result)
    if ($process.ExitCode -ne 0 -or $stdout.Result -notmatch 'PASS: downloaded and parsed [1-9][0-9]* live channels') {
        throw "Native playlist check failed ($($process.ExitCode)): $($stderr.Result) $($stdout.Result)"
    }
    if ($stderr.Result -match 'UnsupportedCharsetException|Missing\w*RegistrationError|not enabled') {
        throw "Native playlist check logged a runtime failure: $($stderr.Result)"
    }
    Write-Output $stdout.Result.Trim()
    Write-Output "Evidence: $testRoot"
} finally {
    if (-not $process.HasExited) { $process.Kill() }
    $process.Dispose()
}
