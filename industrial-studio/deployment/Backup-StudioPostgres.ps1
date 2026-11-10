# Local, credential-safe PostgreSQL archive helper. NOT an automatic cloud backup.
# Requires pg_dump / pg_restore 18 and a protected PGPASSFILE, plus PGHOST/PGPORT/PGDATABASE/PGUSER.
[CmdletBinding()]
param([string]$OutputDirectory = "./studio-backups")
$ErrorActionPreference="Stop"
foreach($item in @("PGHOST","PGPORT","PGDATABASE","PGUSER")){
    if([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($item))){
        throw "Missing $item; supply private connection information outside Git."
    }
}
if([string]::IsNullOrWhiteSpace($env:PGSSLMODE)){ $env:PGSSLMODE="require" }
if($env:PGSSLMODE -notin @("require","verify-ca","verify-full")){
    throw "PGSSLMODE must protect the connection: require or stronger."
}
foreach($tool in @("pg_dump","pg_restore")){
    if(-not (Get-Command $tool -ErrorAction SilentlyContinue)){ throw "Missing PostgreSQL client tool: $tool" }
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp=(Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
$file=Join-Path (Resolve-Path $OutputDirectory).Path "industrial-studio-$stamp.dump"
try {
    & pg_dump --format=custom --no-owner --no-acl --file=$file
    if($LASTEXITCODE -ne 0){ throw "pg_dump failed with exit code $LASTEXITCODE" }
    if((Get-Item $file).Length -lt 128){ throw "Backup is unexpectedly small" }
    & pg_restore --list $file | Out-Null
    if($LASTEXITCODE -ne 0){ throw "Backup archive verification failed" }
    Write-Host "Backup created: $file"
    Write-Host "Store this backup encrypted and TEST RESTORE it before relying on it."
}catch{
    Remove-Item -LiteralPath $file -ErrorAction SilentlyContinue
    throw
}
