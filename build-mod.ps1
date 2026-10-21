# Builds the Xeno Warriors mod and puts the finished .jar next to this project folder.
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
Write-Host "=== Xeno Warriors Recarnet: building the mod ===" -ForegroundColor Magenta

function Get-JavaMajor($exe) {
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $exe -version 2>&1 | Out-String
        if ($out -match 'version "(\d+)(\.(\d+))?') {
            $major = [int]$Matches[1]
            if ($major -eq 1) { $major = [int]$Matches[3] }
            return $major
        }
    } catch {}
    return 0
}

# 1. Find Java 17 or newer (any Java works to start Gradle; it downloads its own JDK 17 to compile)
$java = $null
$candidates = @()
if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
$onPath = Get-Command java.exe -ErrorAction SilentlyContinue
if ($onPath) { $candidates += $onPath.Source }
$roots = @(
    "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft",
    "$env:ProgramFiles\Zulu", "$env:APPDATA\ModrinthApp\meta\java_versions",
    "$env:APPDATA\com.modrinth.theseus\meta\java_versions", "$env:USERPROFILE\curseforge\minecraft\Install\runtime",
    "$env:USERPROFILE\curseforge\minecraft\Install\java", "$env:APPDATA\.minecraft\runtime",
    "${env:ProgramFiles(x86)}\Minecraft Launcher\runtime",
    "$env:LOCALAPPDATA\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime"
)
foreach ($r in $roots) {
    if (Test-Path $r) {
        $candidates += Get-ChildItem -Path $r -Filter java.exe -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
    }
}
foreach ($c in $candidates) {
    if ($c -and (Test-Path $c) -and ((Get-JavaMajor $c) -ge 17)) { $java = $c; break }
}
if (-not $java) {
    Write-Host ""
    Write-Host "Couldn't find Java 17 or newer on this computer." -ForegroundColor Red
    Write-Host "Install it from https://adoptium.net (Temurin 17), then run BUILD-MOD.bat again."
    Start-Process "https://adoptium.net/temurin/releases/?version=17"
    exit 1
}
$env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
Write-Host "Using Java: $java"

# 2. Build (the first time downloads Gradle, Minecraft and Fabric, so it takes a few minutes)
& .\gradlew.bat build --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "The build failed. Copy the red error text above and send it to Claude." -ForegroundColor Red
    exit 1
}

# 3. Copy the mod file next to this folder and show it
$jar = Get-ChildItem -Path 'build\libs' -Filter 'xenowarriors-*.jar' | Where-Object { $_.Name -notlike '*-sources*' } | Select-Object -First 1
$dest = Join-Path (Split-Path $PSScriptRoot -Parent) $jar.Name
Copy-Item $jar.FullName $dest -Force
Write-Host ""
Write-Host "Done! Your mod file is: $dest" -ForegroundColor Green
Write-Host "Drop it into the mods folder of a Fabric 1.20.1 profile, together with Fabric API for 1.20.1."
Start-Process explorer.exe "/select,`"$dest`""
