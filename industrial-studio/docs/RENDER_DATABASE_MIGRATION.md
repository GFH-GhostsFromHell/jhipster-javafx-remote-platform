# Preserving the Industrial Control Studio database

**Expiry:** The connected Render account reports the free PostgreSQL 18 database
"industrial-control-studio-demo-db" expires on **December 10, 2026, at 06:03 UTC**.
Verify the date on Render before relying on this document. Free Render databases have
no managed backups, and the database's current empty IP allowlist blocks external access.

## Preferred: upgrade the existing database

The least disruptive long-term option is to change the current database's compute
plan to a paid tier using the Render Dashboard, before it expires. Confirm the displayed
monthly charge and service terms before proceeding. Do not delete the existing instance.
Keep a design backup first, then verify all HMI clients and settings after changing plans.

Database: https://dashboard.render.com/d/dpg-dbpb76142hec73c58sjg-a
Cloud portal: https://dashboard.render.com/web/srv-dbpbhr0m7kps73aesl80

This repository does not have the authority to upgrade the existing database's
compute plan and has not made a purchase.

## Export and restore portable HMI designs

After the design-backup feature is deployed, sign into the administrator portal,
open **System and releases**, and select **Download design backup (.json)**.

The downloaded archive includes:
- Tag mappings, alarm thresholds, units and stored Java source drafts.
- HMI screen names, widgets and cloud assignments.
- Workstation names and assigned screen IDs (for migration planning).

It excludes authentication credentials, pairing codes and device-token hashes.
Store it securely: engineering and source-code data can be confidential.

On a new instance, use **Restore designs** to reimport tag and HMI screen
definitions; confirm the overwrite warning. This browser restore is not atomic
and does not restore workstation credentials. Register PCs again and issue fresh
pairing secrets.

A design JSON export is NOT a full PostgreSQL backup.

## Full PostgreSQL dump and verified migration

1. Install PostgreSQL 18 client tools on a trusted Windows workstation.
2. In Render Database → Info → Networking, temporarily allowlist ONLY your
   workstation's current public IP (/32) for the export. Do not set
   0.0.0.0/0. Remove the allowlist entry immediately after the dump.
   The service's existing private app-to-database connectivity does not
   require enabling public access.
3. Set PGHOST, PGPORT, PGDATABASE, PGUSER, and PGSSLMODE=require or
   PGSSLMODE=verify-full when the CA and hostname are correctly provisioned.
   Use a protected PGPASSFILE file for password handling. Do not embed
   passwords in Git, tickets, shell history or command arguments.
4. Run the repository's PowerShell utility from your trusted workstation:

       ./industrial-studio/deployment/Backup-StudioPostgres.ps1

   It generates a custom-format, timestamped backup in ./studio-backups,
   rejects a tiny or invalid archive, and does not log credentials.
5. Restore the archive to an EMPTY staging PostgreSQL 18 database first, using
   pg_restore --no-owner --no-acl. Do not add --clean to a live database.
6. Verify the schema, saved HMI layouts, assigned workstation records and
   successful read-only client pairing in staging before changing any portal
   database environment variables.
7. After acceptance tests, schedule a controlled cutover and update the Render
   service's database connection settings to the new database. Restart only
   after a verified restore and credential rollout.
8. Verify every workstation, keep the old database intact until migration is
   confirmed, and securely retain the encrypted backup for recovery.

## Limitations and prerequisites

This is a prepared migration workflow, NOT proof that a live dump or restore
has been performed. The connected database explicitly denies external
connections, and the current tools cannot retrieve its data without changing
network access rules. We have left those restrictions unchanged for safety.

Render documentation:
- https://render.com/docs/free
- https://render.com/docs/postgresql-backups
- https://render.com/docs/postgresql-creating-connecting
