# Development-only headless agent launcher. Java 21 required.
# Supply identity and secrets via an approved Windows service environment,
# not inside this script or a source-controlled .env file.
$ErrorActionPreference = "Stop"
foreach ($required in @("STUDIO_EDGE_CLOUD_URL","STUDIO_EDGE_ID","STUDIO_EDGE_TOKEN")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($required))) {
        throw "Missing $required. Configure the approved machine environment securely."
    }
}
$jar=Join-Path $PSScriptRoot "industrial-studio-edge-agent-0.1.0-SNAPSHOT-agent.jar"
if (-not (Test-Path $jar)) { throw "Headless collector JAR not found in the script folder" }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw "Java 21 not found in PATH" }
& java -jar $jar
if ($LASTEXITCODE -ne 0) { throw "Headless collector exited with code $LASTEXITCODE" }
