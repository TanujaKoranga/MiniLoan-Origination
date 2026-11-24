# Compiles every .java file under src\ into out\
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (Test-Path out) { Remove-Item out -Recurse -Force }
New-Item -ItemType Directory out | Out-Null
$files = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName }
javac -d out $files
if ($LASTEXITCODE -ne 0) { Write-Host "Compilation FAILED" -ForegroundColor Red; exit 1 }
Write-Host "Compilation OK -> out\" -ForegroundColor Green
