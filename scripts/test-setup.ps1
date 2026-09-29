param([string]$SetupPath)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (-not $SetupPath) { $SetupPath = Join-Path $repo 'out\setup\CineK-1.0.1-Setup.exe' }
$testRoot = Join-Path $repo 'build\installer-smoke'
$appDir = Join-Path $testRoot 'Installed CineK'
$configDir = Join-Path $testRoot 'config'
$fixture = Join-Path $repo 'build\desktop-smoke\Video check.avi'
if (-not (Test-Path -LiteralPath $fixture)) { throw 'Run gradlew.bat desktopSmoke first to create the video fixture.' }
$ownedKeys = @('Software\CineK\Capabilities', 'Software\Classes\CineK.Video', 'Software\Classes\CineK.Audio', 'Software\Classes\Applications\CineK.exe', 'Software\Microsoft\Windows\CurrentVersion\App Paths\CineK.exe', 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{733B9CED-6AE5-41DD-9A0D-AF74FE07A4E9}_is1')
foreach ($hive in @('HKCU')) {
    foreach ($key in $ownedKeys) {
        if (Test-Path -LiteralPath "${hive}:\$key") { throw 'CineK is already registered. Run the installer smoke test on a clean test account.' }
    }
}
$extensions = @('mp4','avi','mkv','mov','wmv','flv','webm','m4v','mpeg','mpg','3gp','ts','m2ts','mts','mp3','aac','flac','m4a','ogg','opus','wav','wma','aiff')
function ReadValue([string]$Path, [string]$Name) {
    if (Test-Path -LiteralPath $Path) { (Get-Item -LiteralPath $Path).GetValue($Name) }
}
function DefaultState {
    $state = foreach ($ext in $extensions) {
        $choice = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.$ext\UserChoice"
        [ordered]@{ Extension=$ext; User=ReadValue "HKCU:\Software\Classes\.$ext" ''; Machine=ReadValue "HKLM:\Software\Classes\.$ext" ''; Choice=ReadValue $choice 'ProgId'; Hash=ReadValue $choice 'Hash' }
    }
    ConvertTo-Json -InputObject $state -Compress
}
$defaultsBefore = DefaultState
New-Item -ItemType Directory -Path $testRoot,$configDir -Force | Out-Null
$media = Join-Path $testRoot ('Caf' + [char]0xE9 + ' ' + [char]0x97F3 + [char]0x697D + ' #1.avi')
Copy-Item -LiteralPath $fixture -Destination $media -Force
[IO.File]::WriteAllText((Join-Path $configDir 'config.json'), '{"lang":"en","player":{"playerList":[]}}')
[IO.File]::WriteAllText((Join-Path $configDir 'library.json'), '{}')
$exe = Join-Path $appDir 'CineK.exe'
try {
    $installArgs = '/CURRENTUSER /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /NOICONS /TASKS="fileassoc" /DIR="' + $appDir + '" /LOG="' + (Join-Path $testRoot 'install.log') + '"'
    $installer = Start-Process -FilePath $SetupPath -ArgumentList $installArgs -PassThru -WindowStyle Hidden
    if (-not $installer.WaitForExit(60000)) { throw 'Installer timed out.' }
    if ($installer.ExitCode -ne 0) { throw "Installer returned $($installer.ExitCode)." }
    foreach ($relative in @('CineK.exe','CineK.ico','app\resources\vlc\libvlc.dll','runtime\bin\server\jvm.dll','LICENSE.md')) {
        if (-not (Test-Path -LiteralPath (Join-Path $appDir $relative))) { throw "Missing installed file: $relative" }
    }
    $capabilities = Get-Item 'HKCU:\Software\CineK\Capabilities\FileAssociations'
    if ($capabilities.GetValueNames().Count -ne $extensions.Count) { throw 'Incorrect capabilities count.' }
    foreach ($ext in $extensions) {
        $progid = $capabilities.GetValue(".$ext")
        $openWith = Get-Item "HKCU:\Software\Classes\.$ext\OpenWithProgids"
        if ($openWith.GetValueNames() -notcontains $progid) { throw "Missing Open with registration: .$ext" }
    }
    $registered = ReadValue 'HKCU:\Software\RegisteredApplications' 'CineK'
    if ($registered -ne 'Software\CineK\Capabilities') { throw 'Missing Default Apps registration.' }
    $expectedCommand = '"' + $exe + '" "%1"'
    foreach ($kind in @('Video', 'Audio')) {
        foreach ($verb in @('open', 'play')) {
            if ((ReadValue "HKCU:\Software\Classes\CineK.$kind\shell\$verb\command" '') -ne $expectedCommand) { throw 'Incorrect playback command quoting.' }
        }
        $icon = ReadValue "HKCU:\Software\Classes\CineK.$kind\DefaultIcon" ''
        if ($icon -ne ('"' + (Join-Path $appDir 'CineK.ico') + '",0')) { throw 'Incorrect media icon registration.' }
    }
    if ((DefaultState) -cne $defaultsBefore) { throw 'Installer changed an existing default app choice.' }
    Write-Output 'PASS: installation, bundled runtimes, all 23 Open with/Default Apps associations, icons and quoted commands'

    # Launch the exact registered command with the media placeholder substituted.
    $command = ReadValue 'HKCU:\Software\Classes\CineK.Video\shell\open\command' ''
    if ($command -notmatch '^"([^"]+)" "(%1)"$') { throw 'Unexpected registered command format.' }
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $Matches[1]
    $start.Arguments = '"' + $media + '"'
    $start.WorkingDirectory = $testRoot
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardOutput = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = '-Dcinek.configDir="' + $configDir + '"'
    [IO.File]::WriteAllText((Join-Path $testRoot 'launch-command.txt'), $start.FileName + [Environment]::NewLine + $start.Arguments)
    $launcher = [Diagnostics.Process]::Start($start)
    $errorOutput = $launcher.StandardError.ReadToEndAsync()
    $standardOutput = $launcher.StandardOutput.ReadToEndAsync()
    $deadline = (Get-Date).AddSeconds(20)
    $windowProcess = $null
    do {
        Start-Sleep -Milliseconds 200
        $windowProcess = Get-Process CineK -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $exe -and $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    } until ($windowProcess -or (Get-Date) -gt $deadline)
    if (-not $windowProcess) { throw 'Installed player did not open its window.' }
    Add-Type -AssemblyName System.Drawing
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class CineKSetupWindow {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out Rect rect);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
}
"@
    [CineKSetupWindow]::SetWindowPos($windowProcess.MainWindowHandle, [IntPtr](-1), 0, 0, 0, 0, 3) | Out-Null
    [CineKSetupWindow]::SetForegroundWindow($windowProcess.MainWindowHandle) | Out-Null
    $visible = $false
    for ($attempt = 0; $attempt -lt 12 -and -not $visible; $attempt++) {
        Start-Sleep -Milliseconds 400
        $rect = New-Object CineKSetupWindow+Rect
        [CineKSetupWindow]::GetWindowRect($windowProcess.MainWindowHandle, [ref]$rect) | Out-Null
        $bitmap = New-Object Drawing.Bitmap(($rect.Right - $rect.Left), ($rect.Bottom - $rect.Top))
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)
            $colored = 0
            $samples = 0
            for ($x = 100; $x -lt $bitmap.Width - 100; $x += 20) {
                for ($y = 100; $y -lt $bitmap.Height - 100; $y += 20) {
                    $pixel = $bitmap.GetPixel($x,$y)
                    if ($pixel.G -gt 90 -and [Math]::Abs($pixel.G - $pixel.B) -gt 45) { $colored++ }
                    $samples++
                }
            }
            $visible = $samples -gt 0 -and ($colored / $samples) -gt 0.15
            $bitmap.Save((Join-Path $testRoot 'installed-playback.png'))
        }
        finally { $graphics.Dispose(); $bitmap.Dispose() }
    }
    if (-not $visible) { throw 'Installed player opened but the test video was not visible.' }
    $windowProcess.CloseMainWindow() | Out-Null
    if (-not $windowProcess.WaitForExit(10000)) { throw 'Installed player did not close normally.' }
    $saved = Get-Content -LiteralPath (Join-Path $configDir 'config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($saved.player.playerList.path -notcontains $media) { throw 'The selected Unicode filename did not reach the installed app.' }
    Write-Output 'PASS: registered playback command launches the installed player and renders the selected Unicode/space filename'
}
finally {
    Get-Process CineK -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $exe } | ForEach-Object {
        $_.CloseMainWindow() | Out-Null
        if (-not $_.WaitForExit(5000)) { $_.Kill() }
    }
    if ($errorOutput -and $errorOutput.Wait(2000)) { [IO.File]::WriteAllText((Join-Path $testRoot 'player-stderr.log'), $errorOutput.Result) }
    if ($standardOutput -and $standardOutput.Wait(2000)) { [IO.File]::WriteAllText((Join-Path $testRoot 'player-stdout.log'), $standardOutput.Result) }
    $uninstaller = Join-Path $appDir 'unins000.exe'
    if (Test-Path -LiteralPath $uninstaller) {
        $uninstallArgs = '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /LOG="' + (Join-Path $testRoot 'uninstall.log') + '"'
        $uninstall = Start-Process -FilePath $uninstaller -ArgumentList $uninstallArgs -PassThru -WindowStyle Hidden
        if (-not $uninstall.WaitForExit(60000)) { throw 'Uninstaller timed out.' }
        if ($uninstall.ExitCode -ne 0) { throw "Uninstaller returned $($uninstall.ExitCode)." }
    }
}
foreach ($key in $ownedKeys) {
    if (Test-Path -LiteralPath "HKCU:\$key") { throw "Uninstall left a CineK registry key: $key" }
}
foreach ($ext in $extensions) {
    foreach ($progid in @('CineK.Video','CineK.Audio')) {
        if ($null -ne (ReadValue "HKCU:\Software\Classes\.$ext\OpenWithProgids" $progid)) { throw "Uninstall left association: .$ext" }
    }
}
if ($null -ne (ReadValue 'HKCU:\Software\RegisteredApplications' 'CineK')) { throw 'Uninstall left Default Apps registration.' }
if (Test-Path -LiteralPath $exe) { throw 'Uninstall left the installed player executable.' }
if ((DefaultState) -cne $defaultsBefore) { throw 'Uninstall changed an existing default app choice.' }
Write-Output 'PASS: uninstall removes CineK files/registrations and preserves existing default apps'
