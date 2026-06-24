# DotaOps Frontend

Next.js frontend za DotaOps: vodenje Dota 2 turnirjev, javni pregled tekem, profili ekip in osnovna analitika uvozenih match podatkov.

## Zagon

```bash
npm install
npm run dev
```

Privzeto se aplikacija odpre na `http://localhost:3000`.

### Zagon v produkcijskem načinu / Docker

Gradnja produkcijske slike in zagon z Dockerjem:

```bash
docker build -t dotaops-frontend ./frontend
docker run --env-file .env -p 3000:3000 dotaops-frontend
```

Priporočeno: uporabite `docker-compose up --build` iz korena projekta, ki bo zagnal tako backend kot frontend skupaj.

## Konfiguracija API-ja

Ustvari `.env.local` po zgledu `.env.example`:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api
NEXT_PUBLIC_SUPABASE_URL=
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=
NEXT_PUBLIC_SUPABASE_ANON_KEY=
```

Dokler zaledje ni pripravljeno, strani uporabljajo začetne podatke iz `src/lib/mock-data.ts`.

## Struktura

- `src/app` vsebuje App Router strani.
- `src/components` vsebuje skupne UI komponente.
- `src/lib/types.ts` vsebuje pogodbe za frontend.
- `src/lib/api.ts` je pripravljena vstopna točka za Spring Boot API.
- `src/lib/mock-data.ts` vsebuje začetne podatke za razvoj UI-ja.

### Opomba o Supabase

Za Supabase Auth zadostuje `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` ali legacy `NEXT_PUBLIC_SUPABASE_ANON_KEY`. `NEXT_PUBLIC_SUPABASE_URL` mora kazati na aktiven projekt, ki ga lokalni DNS lahko razresi.

Frontend pričakuje dostopne `NEXT_PUBLIC_` env spremenljivke. Če uporabljate Supabase v oblaku, izpolnite `NEXT_PUBLIC_SUPABASE_URL` in ključe v `.env` ali v `docker` `.env`.
