# Instagram database migration

Exports the live PostgreSQL schema and data for the Instagram tables plus the complete users table. The archive includes actual deployed columns, data, indexes, constraints, and sequences.

Included: public.users, public.instagram_accounts, public.instagram_automation_settings, public.instagram_media_settings, public.instagram_comment_rules, public.instagram_comments, public.instagram_automation_logs, public.instagram_usage, public.instagram_subscriptions.

General FileServer billing tables and all drive/storage tables are excluded. The entire users table is copied, including password hashes and personal data. Treat the archive as highly sensitive; it also contains encrypted Instagram access tokens.

## 1. Create archive on the Docker host (PowerShell)

```powershell
$Container = "fileserver-postgres"
$DbUser = "fileserver"
$DbName = "fileserver"
$Backup = Join-Path (Get-Location) "instagram-migration.dump"
docker exec $Container pg_dump -U $DbUser -d $DbName -Fc -t public.users -t public.instagram_accounts -t public.instagram_automation_settings -t public.instagram_media_settings -t public.instagram_comment_rules -t public.instagram_comments -t public.instagram_automation_logs -t public.instagram_usage -t public.instagram_subscriptions -f /tmp/instagram-migration.dump
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed; do not continue." }
docker cp "${Container}:/tmp/instagram-migration.dump" $Backup
if (!(Test-Path $Backup)) { throw "Backup file was not created." }
Get-Item $Backup | Format-List Name,Length,FullName
Get-FileHash $Backup -Algorithm SHA256
```

Adjust container/database/user names if yours differ. Use your secure PostgreSQL secret mechanism if a password is required; do not put passwords in command history. Keep the SHA256 output.

## 2. Inspect archive

```powershell
docker cp $Backup "${Container}:/tmp/instagram-migration.dump"
docker exec $Container pg_restore -l /tmp/instagram-migration.dump
```

Confirm all nine tables, table data, indexes, constraints, and owned sequences are listed.

## 3. Restore to the new empty database

Create a clean target database first. Run from a machine with PostgreSQL client tools and network access:

```powershell
$TargetHost = "YOUR_NEW_DB_HOST"
$TargetPort = "5432"
$TargetDb = "instagram_automation"
$TargetUser = "YOUR_NEW_DB_USER"
pg_restore --host=$TargetHost --port=$TargetPort --username=$TargetUser --dbname=$TargetDb --no-owner --no-privileges --exit-on-error $Backup
if ($LASTEXITCODE -ne 0) { throw "Restore failed. Keep source unchanged." }
```

The target user needs create-table/sequence/index/constraint permissions. Restore into an empty database because the archive includes table definitions.

## 4. Verify before deleting anything

Run on source and target; compare all counts:

```sql
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM public.users
UNION ALL SELECT 'instagram_accounts', COUNT(*) FROM public.instagram_accounts
UNION ALL SELECT 'instagram_automation_settings', COUNT(*) FROM public.instagram_automation_settings
UNION ALL SELECT 'instagram_media_settings', COUNT(*) FROM public.instagram_media_settings
UNION ALL SELECT 'instagram_comment_rules', COUNT(*) FROM public.instagram_comment_rules
UNION ALL SELECT 'instagram_comments', COUNT(*) FROM public.instagram_comments
UNION ALL SELECT 'instagram_automation_logs', COUNT(*) FROM public.instagram_automation_logs
UNION ALL SELECT 'instagram_usage', COUNT(*) FROM public.instagram_usage
UNION ALL SELECT 'instagram_subscriptions', COUNT(*) FROM public.instagram_subscriptions
ORDER BY table_name;
```

On target, verify foreign-key integrity (all orphan counts must be zero):

```sql
SELECT 'accounts.user_id' AS relation, COUNT(*) AS orphan_rows FROM public.instagram_accounts a LEFT JOIN public.users u ON u.id=a.user_id WHERE u.id IS NULL
UNION ALL SELECT 'comments.user_id', COUNT(*) FROM public.instagram_comments c LEFT JOIN public.users u ON u.id=c.user_id WHERE u.id IS NULL
UNION ALL SELECT 'comments.account_id', COUNT(*) FROM public.instagram_comments c LEFT JOIN public.instagram_accounts a ON a.id=c.instagram_account_id WHERE a.id IS NULL
UNION ALL SELECT 'logs.user_id', COUNT(*) FROM public.instagram_automation_logs l LEFT JOIN public.users u ON u.id=l.user_id WHERE u.id IS NULL;
```

Also check user_id references in settings, media settings, comment rules, usage, and instagram_subscriptions. Compare user/account IDs. Confirm encrypted token columns were restored without printing token values.

## 5. Cut over application before dropping source tables

This archive does not change the Java datasource. Configure the Instagram automation module to use the new database while the main FileServer continues using its existing database. Preserve users.id values. Test account loading, settings, comment processing, rules, logs, usage counters, and subscription lookups. Stop Instagram workers/webhooks during final cutover. If writes happened after the first dump, take a fresh final dump and repeat restore/verification.

**Do not drop source tables until the new datasource is live and tested.** The current application will still expect the old tables until its Instagram module is configured for the new datasource.

## 6. Drop Instagram tables from old database after verified cutover only

This intentionally keeps public.users because FileServer still depends on it.

```sql
BEGIN;
DROP TABLE IF EXISTS public.instagram_automation_logs;
DROP TABLE IF EXISTS public.instagram_comments;
DROP TABLE IF EXISTS public.instagram_usage;
DROP TABLE IF EXISTS public.instagram_comment_rules;
DROP TABLE IF EXISTS public.instagram_media_settings;
DROP TABLE IF EXISTS public.instagram_automation_settings;
DROP TABLE IF EXISTS public.instagram_accounts;
DROP TABLE IF EXISTS public.instagram_subscriptions;
COMMIT;
```

If any verification/cutover step fails, do not run the DROP statements. The source remains the recovery copy.

## Scope

- Complete users table is copied, not just user IDs.
- Only instagram_subscriptions is moved from the billing domain; plans, subscriptions, payment tables, subscription_plans, and user_subscriptions remain in FileServer.
- Drive files, chunks, friends, and other FileServer tables are not moved.
- Flyway history is not copied. Manage the new database schema separately; do not blindly apply FileServer Flyway history to it.