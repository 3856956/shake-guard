$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path $root '.tools\test-classes'
New-Item -ItemType Directory -Force $output | Out-Null
& javac -encoding UTF-8 -d $output (Join-Path $root 'app\src\main\java\io\shakeguard\app\RuleEngine.java') (Join-Path $root 'app\src\main\java\io\shakeguard\app\EventTiming.java') (Join-Path $root 'tests\RuleEngineTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& java -cp $output io.shakeguard.app.RuleEngineTest
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
