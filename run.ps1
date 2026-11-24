# Starts the application (run build.ps1 first). Needs lib\mysql-connector-j-*.jar
Set-Location $PSScriptRoot
$jar = Get-ChildItem lib -Filter "mysql-connector-j*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) { Write-Host "Put mysql-connector-j-*.jar inside the lib folder first." -ForegroundColor Red; exit 1 }
java "-Dconfig.file=config\config.properties" -cp "out;$($jar.FullName)" com.miniloan.console.Main
