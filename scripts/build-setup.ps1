param(
    [string]$IsccPath = $env:INNO_SETUP_COMPILER,
    [ValidateSet('Jvm', 'GraalVM')][string]$Runtime = 'Jvm',
    [string]$DesktopOutputDir
)
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
    $distributionTask = 'createDistributable'
    $distributionFolder = 'app'
    $suffix = ''
    if ($Runtime -eq 'GraalVM') {
        $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
        $msvc = if (Test-Path -LiteralPath $vswhere) {
            & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
        }
        if (-not $msvc) { throw 'GraalVM requires Visual Studio 2022 17.6+ Build Tools with Desktop development with C++ and a Windows SDK. Install these, then run from an x64 Native Tools prompt.' }
        $distributionTask = 'createGraalvmNativeDistributable'
        $distributionFolder = 'graalvm-app'
        $suffix = '-Native'
    }
    $gradleArgs = @('check', $distributionTask)
    $distributionRoot = Join-Path $repo 'build\compose\binaries'
    if ($DesktopOutputDir) {
        $gradleArgs += "-PdesktopOutputDir=$DesktopOutputDir"
        $distributionRoot = if ([IO.Path]::IsPathRooted($DesktopOutputDir)) { $DesktopOutputDir } else { Join-Path $repo $DesktopOutputDir }
    }
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'CineK build or checks failed.' }
    $app = Join-Path $distributionRoot "main\$distributionFolder\CineK"
    Copy-Item -LiteralPath (Join-Path $repo 'icon.ico') -Destination (Join-Path $app 'CineK.ico') -Force
    $requiredFiles = @('CineK.exe', 'CineK.ico')
    $requiredFiles += if ($Runtime -eq 'GraalVM') {
        @('vlc\libvlc.dll', 'vlc\libvlccore.dll', 'bin\skiko-windows-x64.dll', 'jvm.dll')
    } else { @('runtime\bin\server\jvm.dll', 'app\resources\vlc\libvlc.dll', 'app\resources\vlc\libvlccore.dll') }
    foreach ($required in $requiredFiles) {
        if (-not (Test-Path -LiteralPath (Join-Path $app $required))) { throw "Missing packaged file: $required" }
    }
    $gradle = Get-Content -LiteralPath 'build.gradle.kts' -Raw
    $versionMatch = [regex]::Match($gradle, '(?m)^version = "([^"]+)"')
    if (-not $versionMatch.Success) { throw 'Cannot read the CineK version from build.gradle.kts.' }
    $version = $versionMatch.Groups[1].Value
    & $IsccPath /Qp "/DAppVersion=$version" "/DAppSource=$app" "/DOutputSuffix=$suffix" (Join-Path $repo 'installer\CineK.iss')
    if ($LASTEXITCODE -ne 0) { throw 'Inno Setup compilation failed.' }
    $setup = Join-Path $repo "out\setup\CineK-$version$suffix-Setup.exe"
    Get-FileHash -LiteralPath $setup -Algorithm SHA256 | Format-List
    Write-Output "Installer: $setup"
}
finally { Pop-Location }
