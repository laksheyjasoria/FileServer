param(
    [string]$Container = "fileserver-postgres",
    [string]$DbUser = "fileserver",
    [string]$DbName = "fileserver",
    [string]$BackupDirectory = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$tables = @(
    "users",
    "instagram_accounts",
    "instagram_automation_settings",
    "instagram_media_settings",
    "instagram_comment_rules",
    "instagram_comments",
    "instagram_automation_logs",
    "instagram_usage",
    "instagram_subscriptions"
)
$instagramTables = @(
    "instagram_automation_logs", "instagram_comments", "instagram_usage",
    "instagram_comment_rules", "instagram_media_settings",
    "instagram_automation_settings", "instagram_accounts", "instagram_subscriptions"
)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupPath = Join-Path $BackupDirectory "instagram-migration-$timestamp.dump"
$containerBackup = "/tmp/instagram-migration-$timestamp.dump"
$containerList = "/tmp/instagram-migration-$timestamp.list"

Write-Host "Checking PostgreSQL container and required tables..."
docker inspect $Container *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker container '$Container' was not found." }

$tableNames = ($tables | ForEach-Object { "'$_'" }) -join ","
$tableCheckSql = @"
SELECT string_agg(required.table_name, ', ' ORDER BY required.table_name)
FROM unnest(ARRAY[$tableNames]) AS required(table_name)
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = 'public' AND actual.table_name = required.table_name
WHERE actual.table_name IS NULL;
"@
$missing = $tableCheckSql | docker exec -i $Container psql -X -v ON_ERROR_STOP=1 -U $DbUser -d $DbName -At
if ($LASTEXITCODE -ne 0) { throw "Could not inspect source database. No tables were removed." }
if (-not [string]::IsNullOrWhiteSpace($missing)) {
    throw "Required tables are missing: $missing. No tables were removed."
}

Write-Host "Creating custom-format backup: $backupPath"
$dumpArgs = @(
    "exec", $Container, "pg_dump", "-U", $DbUser, "-d", $DbName, "-Fc",
    "-t", "public.users",
    "-t", "public.instagram_accounts",
    "-t", "public.instagram_automation_settings",
    "-t", "public.instagram_media_settings",
    "-t", "public.instagram_comment_rules",
    "-t", "public.instagram_comments",
    "-t", "public.instagram_automation_logs",
    "-t", "public.instagram_usage",
    "-t", "public.instagram_subscriptions",
    "-f", $containerBackup
)
& docker @dumpArgs
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed. Source tables were NOT removed." }

docker cp ($Container + ":" + $containerBackup) $backupPath
if ($LASTEXITCODE -ne 0 -or !(Test-Path $backupPath)) {
    throw "Could not copy backup to host. Source tables were NOT removed."
}
docker exec $Container pg_restore -l $containerBackup | Set-Content -Encoding UTF8 $containerList
if ($LASTEXITCODE -ne 0 -or !(Test-Path $containerList)) {
    throw "Backup archive validation failed. Source tables were NOT removed."
}
$archiveToc = Get-Content $containerList -Raw
foreach ($table in $tables) {
    if ($archiveToc -notmatch ("TABLE public " + [regex]::Escape($table) + "\s")) {
        throw "Backup is missing table definition '$table'. Source tables were NOT removed."
    }
}
$hash = Get-FileHash $backupPath -Algorithm SHA256
Write-Host "Backup validated."
Write-Host "Backup: $backupPath"
Write-Host "Size: $((Get-Item $backupPath).Length) bytes"
Write-Host "SHA256: $($hash.Hash)"
Write-Warning "The complete users table is included, including emails, password hashes and personal data. Instagram token data is also included."

$instagramNames = ($instagramTables | ForEach-Object { "'$_'" }) -join ","
$referenceCheckSql = @"
SELECT count(*)
FROM pg_constraint con
JOIN pg_class rel ON rel.oid = con.conrelid
JOIN pg_namespace ns ON ns.oid = rel.relnamespace
JOIN pg_class target ON target.oid = con.confrelid
JOIN pg_namespace target_ns ON target_ns.oid = target.relnamespace
WHERE con.contype = 'f'
  AND target_ns.nspname = 'public'
  AND target.relname = ANY (ARRAY[$instagramNames])
  AND NOT (ns.nspname = 'public' AND rel.relname = ANY (ARRAY[$instagramNames]));
"@
$externalRefs = $referenceCheckSql | docker exec -i $Container psql -X -v ON_ERROR_STOP=1 -U $DbUser -d $DbName -At
if ($LASTEXITCODE -ne 0) { throw "Could not check foreign-key dependencies. Backup retained; source tables unchanged." }
if ([int]$externalRefs -gt 0) {
    throw "Found $externalRefs foreign-key constraint(s) from non-Instagram tables. Backup retained; refusing to drop."
}

$confirmation = Read-Host "To DROP ONLY the eight Instagram tables from '$DbName' after this backup, type REMOVE"
if ($confirmation -cne "REMOVE") {
    Write-Host "Cancelled. Backup retained; source database unchanged."
    exit 0
}

$dropSql = @"
BEGIN;
DROP TABLE public.instagram_automation_logs;
DROP TABLE public.instagram_comments;
DROP TABLE public.instagram_usage;
DROP TABLE public.instagram_comment_rules;
DROP TABLE public.instagram_media_settings;
DROP TABLE public.instagram_automation_settings;
DROP TABLE public.instagram_accounts;
DROP TABLE public.instagram_subscriptions;
COMMIT;
"@
$dropSql | docker exec -i $Container psql -X -v ON_ERROR_STOP=1 -U $DbUser -d $DbName
if ($LASTEXITCODE -ne 0) {
    throw "Drop operation failed or was rolled back. Backup remains at $backupPath"
}

Write-Host "Instagram tables removed. public.users was retained."
Write-Host "Keep this backup safe until the new database import and verification are complete."
Get-Item $backupPath | Format-List FullName,Length
Get-FileHash $backupPath -Algorithm SHA256
