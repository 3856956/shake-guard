$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root 'dist'
New-Item -ItemType Directory -Force $output | Out-Null
$archivePath = Join-Path $output 'shake-guard-0.1.1-source.zip'
$paths = @('app\src','app\build.gradle','scripts','tests','gradle','gradlew','gradlew.bat','settings.gradle','build.gradle','gradle.properties','README.md','DESIGN.md','MANUAL_TESTS.md','VALIDATION.md','.gitignore','.github')
$stream = [IO.File]::Open($archivePath, [IO.FileMode]::Create)
$archive = New-Object IO.Compression.ZipArchive($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($relative in $paths) {
        $item = Get-Item -LiteralPath (Join-Path $root $relative)
        $files = if ($item.PSIsContainer) { Get-ChildItem -LiteralPath $item.FullName -File -Recurse -Force } else { @($item) }
        foreach ($file in $files) {
            $entry = $file.FullName.Substring($root.Length + 1).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$entry) | Out-Null
        }
    }
} finally { $archive.Dispose(); $stream.Dispose() }
Get-FileHash (Join-Path $output 'shake-guard-0.1.1-debug.apk'),$archivePath -Algorithm SHA256 |
    ForEach-Object { $_.Hash.ToLower() + '  ' + (Split-Path $_.Path -Leaf) } |
    Set-Content (Join-Path $output 'SHA256SUMS.txt') -Encoding ASCII
