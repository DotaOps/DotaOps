# Local Supabase Development

This project uses a remote Supabase project for local demo QA. Do not fall back
to a local empty database when the task needs demo auth, profiles, heroes, or
analytics data.

## Required Environment Variables

Backend database:

- `SUPABASE_DB_URL`
- `SUPABASE_DB_USER`
- `SUPABASE_DB_PASSWORD`
- `SPRING_FLYWAY_ENABLED`

Backend Supabase auth/storage:

- `NEXT_PUBLIC_SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `SUPABASE_JWT_SECRET`
- `SUPABASE_JWT_ISSUER`
- `SUPABASE_JWT_AUDIENCE`
- `SUPABASE_JWKS_URI`, optional when JWT secret validation is used

Frontend Supabase auth:

- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` or `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `NEXT_PUBLIC_API_URL`
- `NEXT_SERVER_API_URL`, for server-side frontend API calls

Never commit `.env`, `.env.local`, database passwords, JWT secrets, service role
keys, or generated access tokens.

## API/Auth URL vs Database URL

`NEXT_PUBLIC_SUPABASE_URL` is the public Supabase API/Auth endpoint:

```text
https://<project-ref>.supabase.co
```

The backend database URL is separate. For local IPv4-only networks, prefer the
Supabase shared pooler in session mode:

```text
jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
```

For the shared pooler, the database user must include the project reference:

```text
postgres.<project-ref>
```

Use the direct database hostname only when it is reachable from the current
network and the Supabase project supports that path:

```text
db.<project-ref>.supabase.co
```

## Local Startup

From the repository root:

```powershell
Copy-Item .env.example .env
```

Fill `.env` with the existing remote Supabase project values. Then start the
backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

For frontend local dev, create `frontend/.env.local` from
`frontend/.env.example` and copy only the public frontend values:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm run dev
```

For Docker Compose, values come from the root `.env`. Verify interpolation
without printing secrets:

```powershell
docker compose config --services
```

## Safe Diagnostics

Check whether the public Supabase project hostname exists:

```powershell
Resolve-DnsName <project-ref>.supabase.co
```

Check whether the pooler hostname exists:

```powershell
Resolve-DnsName aws-0-<region>.pooler.supabase.com
```

Check whether the pooler port is reachable:

```powershell
Test-NetConnection aws-0-<region>.pooler.supabase.com -Port 5432
```

Check whether the backend received required variables without printing values:

```powershell
'SUPABASE_DB_URL','SUPABASE_DB_USER','SUPABASE_DB_PASSWORD' |
  ForEach-Object { [PSCustomObject]@{ Name = $_; IsSet = [bool][Environment]::GetEnvironmentVariable($_) } }
```

## Troubleshooting

`tenant/user not found` from the pooler usually means the pooler endpoint can be
reached, but the tenant identifier in the username or the selected pooler region
does not match an active Supabase project. Re-copy the session pooler connection
details from the Supabase dashboard for the existing project.

`db.<project-ref>.supabase.co` DNS failure means the direct database endpoint is
not reachable from the current network, or the project reference is not active.
Use the shared pooler for local IPv4 development unless the direct connection is
explicitly supported.

`Supabase Auth Failed to fetch` in the browser usually means
`NEXT_PUBLIC_SUPABASE_URL` is not reachable from the browser, or the frontend was
built without valid public Supabase env values. Confirm that
`<project-ref>.supabase.co` resolves and that `frontend/.env.local` has the same
project as the root `.env`.
