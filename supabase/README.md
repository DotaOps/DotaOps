# DotaOps Supabase

This folder contains Supabase project configuration for DotaOps.

## What Is Included

- `config.toml`: Supabase local/project configuration.
- `post_flyway_hardening.sql`: manual post-Flyway hardening for Flyway's own metadata table.
- Database migrations are owned by the Spring Boot backend in `../backend/src/main/resources/db/migration`.
- Realistic mock seed data is available as a manual local/dev/demo fixture under `../backend/src/main/resources/db/demo`.
  Use `../scripts/seed-demo.ps1 -ConfirmDemoSeed`; for accumulated generated test rows, use `../scripts/seed-demo.ps1 -ConfirmDemoSeed -CleanGeneratedTestData`. Do not run either on production without explicit approval.

The schema covers:

- user profiles and roles
- linked external accounts such as Steam, OpenDota, Discord, and email identities
- teams and team members
- team invitations and active roster history
- tournaments and team registrations
- tournament staff, group-stage structures, roster snapshots, and check-in metadata
- match series, bracket slot sources, individual Dota games/maps, results, and `dota_match_id`
- OpenDota import state: `queued`, `processing`, `ready`, `error`
- import event history, raw payload storage, and normalized per-player match rows
- heroes, per-player stats, public security-invoker analytics views, and service-only materialized analytics views
- notifications/outbox and audit logging for operational changes
- RLS policies for public reads, authenticated captain workflows, tournament staff workflows, and service-role backend jobs

## Steam Identity Flow

Steam identity writes are backend-managed. Clients can read linked identity rows permitted by RLS, but they cannot directly claim or rewrite Steam accounts.

Backend/service code should use:

- `private.upsert_steam_profile(...)` after a Steam login has been validated. It creates or updates the DotaOps profile and links the SteamID64 identity.
- `private.link_steam_account_to_profile(...)` when an existing player profile needs another SteamID64 linked to it.
- `private.unlink_steam_account_from_profile(...)` when the backend needs to remove a linked SteamID64. By default it blocks removing the last Steam login identity from a profile.
- `private.set_primary_external_account(...)` when the backend needs to switch which linked identity is primary.
- `private.create_steam_login_state(...)` and `private.consume_steam_login_state(...)` for short-lived, hashed Steam OpenID state handling.

`public.profiles.steam_id` is kept only as a legacy/public mirror of the primary SteamID64. The source of truth for one or more Steam profiles per player is `public.profile_external_accounts` with `provider = 'steam'`.

## GitHub Integration

Your Supabase dashboard is configured to read this repository with working directory `.` and production branch `main`.
The GitHub integration may stay connected for project sync, but table creation is currently handled by backend Flyway migrations.

## Local CLI Commands

Use these from the repository root:

```bash
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase status
```

Do not run seed/reset commands against production unless the team intentionally wants to recreate data.

After Flyway has created or migrated the schema, run the post-Flyway hardening SQL once for each environment:

```sql
\i supabase/post_flyway_hardening.sql
```

This is intentionally not a Flyway migration because Flyway locks `public.flyway_schema_history` while it is running.

## Required Secrets

Do not commit real keys. Keep them in `.env` or deployment secrets.

Backend/Spring Boot uses:

```bash
SUPABASE_PROJECT_REF=hjszjebirxhdtrbhefbv
SUPABASE_DB_URL=jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.hjszjebirxhdtrbhefbv
SUPABASE_DB_PASSWORD=
SPRING_FLYWAY_ENABLED=true
OPENDOTA_API_BASE_URL=https://api.opendota.com/api
```

Frontend can use only public values:

```bash
NEXT_PUBLIC_SUPABASE_URL=
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=
NEXT_PUBLIC_SUPABASE_ANON_KEY=
```

Never expose server-side secret keys in frontend code.
