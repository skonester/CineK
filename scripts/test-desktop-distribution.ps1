param(
    [Parameter(Mandatory = $true)][string]$ApplicationPath,
    [ValidateSet('Jvm', 'GraalVM')][string]$Runtime = 'Jvm',
    [switch]$AudioOnly,
    [switch]$VerifyQueueEnd
)
$ErrorActionPreference = 'Stop'
if ($AudioOnly -and $VerifyQueueEnd) { throw 'Queue-end verification uses the video fixture.' }
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'desktop-window.ps1')
$exe = (Resolve-Path -LiteralPath $ApplicationPath).Path
$fixtureName = if ($AudioOnly) { 'Playback check.wav' } else { 'Video check.avi' }
$fixture = Join-Path $repo "build\desktop-smoke\$fixtureName"
if (-not (Test-Path -LiteralPath $fixture)) { throw 'Run gradlew.bat desktopSmoke first to create the media fixture.' }
$kind = if ($AudioOnly) { 'audio' } elseif ($VerifyQueueEnd) { 'video queue' } else { 'video' }
$testRoot = Join-Path $repo ('build\distribution-smoke\' + $Runtime + '-' + $kind + '-' + [guid]::NewGuid().ToString('N'))
$configDir = Join-Path $testRoot 'config'
New-Item -ItemType Directory -Path $configDir -Force | Out-Null
$extension = [IO.Path]::GetExtension($fixture)
$media = Join-Path $testRoot ('Caf' + [char]0xE9 + ' ' + [char]0x97F3 + [char]0x697D + ' #1' + $extension)
$second = Join-Path $testRoot ('Second media' + $extension)
Copy-Item -LiteralPath $fixture -Destination $media
Copy-Item -LiteralPath $fixture -Destination $second
$start = New-Object Diagnostics.ProcessStartInfo
$start.FileName = $exe
$strict = if ($Runtime -eq 'GraalVM') { '-XX:MissingRegistrationReportingMode=Exit ' } else { '' }
$start.Arguments = $strict + '"' + $media + '" "' + $second + '"'
$start.WorkingDirectory = $testRoot
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
$start.RedirectStandardError = $true
$start.RedirectStandardOutput = $true
$start.EnvironmentVariables['CINEK_CONFIG_DIR'] = $configDir
if ($Runtime -eq 'Jvm') { $start.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = '-Dcinek.configDir="' + $configDir + '"' }
$player = [Diagnostics.Process]::Start($start)
$stderr = $player.StandardError.ReadToEndAsync()
$stdout = $player.StandardOutput.ReadToEndAsync()
$windowProcess = $null
$windowHandle = [IntPtr]::Zero
try {
    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 200
        $player.Refresh()
        # Nucleus's JVM launcher starts a second CineK.exe; native images own their window directly.
        $childIds = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $($player.Id)" | Select-Object -ExpandProperty ProcessId)
        foreach ($candidate in (Get-Process -Id (@($player.Id) + $childIds) -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $exe })) {
            $windowHandle = [CineKDesktopWindow]::FindMainWindow($candidate.Id)
            if ($windowHandle -ne [IntPtr]::Zero) { $windowProcess = $candidate; break }
        }
        if ($player.HasExited -and -not $windowProcess) { throw "Packaged player exited during startup ($($player.ExitCode)): $($stderr.Result)" }
    } until ($windowProcess -or (Get-Date) -gt $deadline)
    if (-not $windowProcess) { throw 'Packaged player did not open a window.' }
    if ($VerifyQueueEnd) {
        # Two ten-second videos must advance through both EOF callbacks and stop.
        Start-Sleep -Seconds 22
    } elseif ($AudioOnly) {
        # There is no video surface to sample; saved VLC progress below verifies the audio clock.
        Start-Sleep -Seconds 2
    } else {
        Add-Type -AssemblyName System.Drawing

        [CineKDesktopWindow]::SetWindowPos($windowHandle, [IntPtr](-1), 0, 0, 0, 0, 3) | Out-Null
        [CineKDesktopWindow]::SetForegroundWindow($windowHandle) | Out-Null
        $visible = $false
        for ($attempt = 0; $attempt -lt 15 -and -not $visible; $attempt++) {
            Start-Sleep -Milliseconds 400
            if ($player.HasExited) { throw "Packaged player exited during playback ($($player.ExitCode)): $($stderr.Result)" }
            $rect = New-Object CineKDesktopWindow+Rect
            [CineKDesktopWindow]::GetWindowRect($windowHandle, [ref]$rect) | Out-Null
            $bitmap = New-Object Drawing.Bitmap(($rect.Right - $rect.Left), ($rect.Bottom - $rect.Top))
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)
                $colored = 0
                $samples = 0
                for ($x = 100; $x -lt $bitmap.Width - 100; $x += 20) {
                    for ($y = 100; $y -lt $bitmap.Height - 100; $y += 20) {
                        $pixel = $bitmap.GetPixel($x, $y)
                        if ($pixel.G -gt 90 -and [Math]::Abs($pixel.G - $pixel.B) -gt 45) { $colored++ }
                        $samples++
                    }
                }
                $visible = $samples -gt 0 -and ($colored / $samples) -gt 0.15
                $bitmap.Save((Join-Path $testRoot 'playback.png'))
            } finally { $graphics.Dispose(); $bitmap.Dispose() }
        }
        if (-not $visible) { throw 'Packaged player opened but the video pixels were not visible.' }
    }
    [CineKDesktopWindow]::PostMessage($windowHandle, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null
    if (-not $player.WaitForExit(10000)) { throw 'Packaged player did not close normally.' }
    if ($player.ExitCode -ne 0) { throw "Packaged player returned $($player.ExitCode): $($stderr.Result)" }
    if ($stderr.Result -match 'UnsupportedCharsetException|Missing\w*RegistrationError|Failed to initialize libvlc/vlcj|Exception in thread|Error was captured in composition') {
        throw "Packaged player logged a runtime failure: $($stderr.Result)"
    }
    $saved = Get-Content -LiteralPath (Join-Path $configDir 'config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($saved.player.playerList.path -notcontains $media -or $saved.player.playerList.path -notcontains $second) {
        throw 'Packaged player did not import both Unicode/space filenames.'
    }
    $library = Get-Content -LiteralPath (Join-Path $configDir 'library.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($VerifyQueueEnd) {
        $firstHistory = $library.history | Where-Object { $_.path -eq $media }
        $secondHistory = $library.history | Where-Object { $_.path -eq $second }
        if (-not $firstHistory -or -not $secondHistory -or $secondHistory.playedAt - $firstHistory.playedAt -lt 8000 -or
            [long]$firstHistory.position -ne 0 -or [long]$secondHistory.position -ne 0) {
            throw 'Packaged playback did not finish both queued videos with repeat off.'
        }
    } elseif (-not ($library.history | Where-Object { $_.path -eq $media -and $_.position -gt 0 })) {
        throw 'Packaged playback did not advance and save its position.'
    }
    Write-Output "PASS: $Runtime package plays $kind, imports both Unicode/space filenames, saves progress and exits from an unrelated working directory"
} finally {
    if ($windowProcess -and -not $windowProcess.HasExited) { [CineKDesktopWindow]::PostMessage($windowHandle, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null; if (-not $windowProcess.WaitForExit(5000)) { $windowProcess.Kill() } }
    if (-not $player.HasExited) { $player.CloseMainWindow() | Out-Null; if (-not $player.WaitForExit(5000)) { $player.Kill() } }
    [IO.File]::WriteAllText((Join-Path $testRoot 'stderr.log'), $stderr.Result)
    [IO.File]::WriteAllText((Join-Path $testRoot 'stdout.log'), $stdout.Result)
    Write-Output "Evidence: $testRoot"
    $player.Dispose()
}
