$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path $PSScriptRoot -Parent
$toolDir = Join-Path $root '.tools'
New-Item -ItemType Directory -Force $toolDir | Out-Null
if (!(Test-Path (Join-Path $toolDir 'jdk-info.json'))) {
    Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows' -OutFile (Join-Path $toolDir 'jdk-info.json')
}
$info = Get-Content (Join-Path $toolDir 'jdk-info.json') -Raw | ConvertFrom-Json
$archive = Join-Path $toolDir 'jdk.zip'
if (!(Test-Path $archive)) { Invoke-WebRequest -Uri $info[0].binary.package.link -OutFile $archive }
if ((Get-FileHash $archive -Algorithm SHA256).Hash.ToLower() -ne $info[0].binary.package.checksum) { throw 'JDK checksum mismatch' }
if (!(Test-Path (Join-Path $toolDir 'jdk'))) { Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $toolDir 'jdk') }
$gradleZip = Join-Path $toolDir 'gradle.zip'
if (!(Test-Path $gradleZip)) { Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -OutFile $gradleZip }
Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256' -OutFile (Join-Path $toolDir 'gradle.sha256')
if ((Get-FileHash $gradleZip -Algorithm SHA256).Hash.ToLower() -ne (Get-Content (Join-Path $toolDir 'gradle.sha256') -Raw).Trim()) { throw 'Gradle checksum mismatch' }
if (!(Test-Path (Join-Path $toolDir 'gradle-8.11.1'))) { Expand-Archive -LiteralPath $gradleZip -DestinationPath $toolDir }
$sdkZip = Join-Path $toolDir 'android-tools.zip'
if (!(Test-Path $sdkZip)) { Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $sdkZip }
if (!(Test-Path (Join-Path $toolDir 'android-unpack'))) { Expand-Archive -LiteralPath $sdkZip -DestinationPath (Join-Path $toolDir 'android-unpack') }
$sdk = Join-Path $toolDir 'android-sdk'
$latest = Join-Path $sdk 'cmdline-tools\latest'
if (!(Test-Path $latest)) { New-Item -ItemType Directory -Force $latest | Out-Null; Copy-Item -Path (Join-Path $toolDir 'android-unpack\cmdline-tools\*') -Destination $latest -Recurse }
Write-Output 'Tool archives downloaded and extracted.'
