$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = Get-ChildItem (Join-Path $root '.tools\jdk') -Directory | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:PATH = (Join-Path $jdk.FullName 'bin') + ';' + $env:PATH
$env:ANDROID_HOME = Join-Path $root '.tools\android-sdk'
$env:GRADLE_USER_HOME = Join-Path $root '.tools\gradle-home'
& (Join-Path $PSScriptRoot 'test-engine.ps1')
1..100 | ForEach-Object { 'y' } | & (Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin\sdkmanager.bat') --licenses | Out-File (Join-Path $root '.tools\sdk-licenses.log')
& (Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin\sdkmanager.bat') 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
Push-Location $root
try {
    & (Join-Path $root '.tools\gradle-8.11.1\bin\gradle.bat') wrapper --gradle-version 8.11.1 --distribution-type bin assembleDebug lintDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
} finally { Pop-Location }
