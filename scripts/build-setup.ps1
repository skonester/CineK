param([string]$IsccPath = $env:INNO_SETUP_COMPILER)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (-not $IsccPath) {
    $compiler = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($compiler) { $IsccPath = $compiler.Source }
    else { $IsccPath = Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6\ISCC.exe' }
}
if (-not (Test-Path -LiteralPath $IsccPath -PathType Leaf)) {
    throw 'Install Inno Setup 6, or pass -IsccPath with the full path to ISCC.exe.'
}
Push-Location $repo
try {
    & .\gradlew.bat check createDistributable
    if ($LASTEXITCODE -ne 0) { throw 'CineK build or checks failed.' }
    $app = Join-Path $repo 'build\compose\binaries\main\app\CineK'
    Copy-Item -LiteralPath (Join-Path $repo 'icon.ico') -Destination (Join-Path $app 'CineK.ico') -Force
    foreach ($required in @('CineK.exe', 'CineK.ico', 'runtime\bin\server\jvm.dll', 'app\resources\vlc\libvlc.dll', 'app\resources\vlc\libvlccore.dll')) {
        if (-not (Test-Path -LiteralPath (Join-Path $app $required))) { throw "Missing packaged file: $required" }
    }
    $gradle = Get-Content -LiteralPath 'build.gradle.kts' -Raw
    $versionMatch = [regex]::Match($gradle, '(?m)^version = "([^"]+)"')
    if (-not $versionMatch.Success) { throw 'Cannot read the CineK version from build.gradle.kts.' }
    $version = $versionMatch.Groups[1].Value
    & $IsccPath /Qp "/DAppVersion=$version" "/DAppSource=$app" (Join-Path $repo 'installer\CineK.iss')
    if ($LASTEXITCODE -ne 0) { throw 'Inno Setup compilation failed.' }
    $setup = Join-Path $repo "out\setup\CineK-$version-Setup.exe"
    Get-FileHash -LiteralPath $setup -Algorithm SHA256 | Format-List
    Write-Output "Installer: $setup"
}
finally { Pop-Location }
