# DotaOps

DotaOps je aplikacija za organizacijo Dota 2 turnirjev, prijavo ekip, vodenje tekem in kasnejso analitiko podatkov iz OpenDota. Projekt je razdeljen na Next.js frontend, Spring Boot backend in Supabase Postgres bazo.

## Tehnologije

- Frontend: Next.js 16 App Router, React 19, TypeScript
- Backend: Spring Boot 4, Java 21, Maven Wrapper
- Baza: Supabase Postgres
- Migracije: Flyway v Spring Boot backendu
- Auth/session helperji: `@supabase/ssr`
- Zunanji podatki: OpenDota API

## Struktura projekta

```text
DotaOps/
  backend/      Spring Boot API, varnostna konfiguracija, Flyway migracije
  frontend/     Next.js App Router aplikacija
  supabase/     Supabase lokalna/projektna konfiguracija in navodila
  .env.example  Primer root environment nastavitev
```

Pomembne datoteke:

- `frontend/src/app/` vsebuje strani aplikacije.
- `frontend/src/lib/api.ts` je vstopna tocka za klice na Spring Boot API.
- `frontend/src/lib/supabase/` vsebuje Supabase browser/server/proxy helperje.
- `frontend/src/proxy.ts` osvezuje Supabase auth session cookie-je.
- `backend/src/main/resources/application.properties` bere root `.env`.
- `backend/src/main/resources/db/migration/` vsebuje Flyway migracije za bazo.
- `supabase/config.toml` je Supabase lokalna konfiguracija.

## Zahteve

Namesti:

- Node.js 20 ali novejsi
- npm
- Java 21
- Git

Maven ni treba namescati globalno, ker backend uporablja `mvnw.cmd`.

## Environment Datoteke

Root `.env` se uporablja za backend in skupne nastavitve. Ustvari ga iz primera:

```powershell
cd C:\DataOpsProjekt\DotaOps
Copy-Item .env.example .env
```

V `.env` dopolni vsaj:

```properties
SUPABASE_DB_URL=jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres.<projectRef>
SUPABASE_DB_PASSWORD=YOUR_DATABASE_PASSWORD
SPRING_FLYWAY_ENABLED=true
OPENDOTA_API_BASE_URL=https://api.opendota.com/api
OPENDOTA_API_KEY=
OPENDOTA_CONNECT_TIMEOUT=2s
OPENDOTA_READ_TIMEOUT=5s
OPENDOTA_RETRY_MAX_ATTEMPTS=3
OPENDOTA_RETRY_BACKOFF=250ms

NEXT_PUBLIC_SUPABASE_URL=https://<projectRef>.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

Frontend ima svoj `.env.local`, ker Next.js bere env datoteke iz `frontend/` mape:

```powershell
cd C:\DataOpsProjekt\DotaOps\frontend
Copy-Item .env.example .env.local
```

V `frontend/.env.local` nastavi:

```properties
NEXT_PUBLIC_API_URL=http://localhost:8080/api
NEXT_PUBLIC_SUPABASE_URL=https://hjszjebirxhdtrbhefbv.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY
```

Nikoli ne commitaj `.env`, `.env.local`, database gesel ali server-side secret keyev.

## Supabase Povezava

Za backend uporabljamo Supabase Session Pooler, ker deluje prek IPv4:

```text
host: aws-0-eu-west-1.pooler.supabase.com
port: 5432
database: postgres
user: postgres.hjszjebirxhdtrbhefbv
```

JDBC oblika za Spring Boot:

```properties
SUPABASE_DB_URL=jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
```

Ne uporabljaj direct cloud connection stringa `db.<projectRef>.supabase.co`, razen ce je okolje namenoma na IPv6 ali ima Supabase IPv4 add-on.

## OpenDota Konfiguracija

Backend bere OpenDota nastavitve iz root `.env` oziroma okolja:

```properties
OPENDOTA_API_BASE_URL=https://api.opendota.com/api
OPENDOTA_API_KEY=
OPENDOTA_CONNECT_TIMEOUT=2s
OPENDOTA_READ_TIMEOUT=5s
OPENDOTA_RETRY_MAX_ATTEMPTS=3
OPENDOTA_RETRY_BACKOFF=250ms
```

`OPENDOTA_API_KEY` je opcijski in mora ostati samo na backend/server strani. Ne dodajaj ga v frontend okolje, response DTO-je ali loge.

## Zagon Backenda

Backend se zazene na `http://localhost:8080`.

```powershell
cd C:\DataOpsProjekt\DotaOps\backend
.\mvnw.cmd spring-boot:run
```

Health endpoint:

```text
http://localhost:8080/api/health
```

Actuator health:

```text
http://localhost:8080/actuator/health
```

Ko se backend zazene, Flyway migracije iz `backend/src/main/resources/db/migration/` ustvarijo shemo v Supabase bazi, ce se se niso izvedle.

## Zagon Frontenda

Namesti pakete:

```powershell
cd C:\DataOpsProjekt\DotaOps\frontend
npm install
```

Zazeni razvojni server:

```powershell
npm run dev
```

Frontend je privzeto na:

```text
http://localhost:3000
```

Ce backend ni zagnan ali API se nima podatkov, frontend uporablja fallback/mock podatke iz `frontend/src/lib/mock-data.ts`.

## Preverjanje Projekta

Frontend:

```powershell
cd C:\DataOpsProjekt\DotaOps\frontend
npm run lint
npm run typecheck
npm run build
```

Backend:

```powershell
cd C:\DataOpsProjekt\DotaOps\backend
.\mvnw.cmd test
```

## Baza In Migracije

Trenutni dogovor projekta:

- Backend je lastnik strukture baze.
- Flyway migracije so v `backend/src/main/resources/db/migration/`.
- Supabase mapa ne vsebuje seed/test podatkov.
- Testnih podatkov trenutno ne dodajamo v bazo.

Ce spreminjas shemo, dodaj novo Flyway migracijo v backend. Ne spreminjaj roke direktno v produkcijski Supabase bazi, razen ce ekipa izrecno doloci, da se sprememba nato prenese v migracijo.

## Supabase Mapa

`supabase/` vsebuje konfiguracijo in navodila za Supabase projekt:

- `config.toml` za lokalni Supabase setup
- `README.md` z dodatnimi Supabase navodili

Supabase GitHub integration je lahko povezan na repository, ampak ustvarjanje tabel trenutno vodi backend prek Flyway.

## GitHub

Repository:

```text
https://github.com/FilipKn/DotaOps
```

Pred pushom preveri:

```powershell
git status
```

Ne commitaj lokalnih artefaktov:

- `.env`
- `frontend/.env.local`
- `node_modules/`
- `.next/`
- `target/`
- `*.log`

Te datoteke so namenoma v `.gitignore`.

## Obicajen Razvojni Tok

1. Potegni zadnje spremembe iz GitHuba.
2. Preveri `.env` in `frontend/.env.local`.
3. Zazeni backend z `.\mvnw.cmd spring-boot:run`.
4. Zazeni frontend z `npm run dev`.
5. Odpri `http://localhost:3000`.
6. Pred commitom zazeni lint, typecheck, build in backend teste.

## Pogoste Tezave

Ce frontend ne vidi Supabase nastavitev, preveri `frontend/.env.local` in po spremembi ponovno zazeni `npm run dev`.

Ce backend ne pride do baze, preveri `SUPABASE_DB_PASSWORD`, session pooler URL in `SUPABASE_DB_USER`.

Ce je port `3000` zaseden, ustavi star Next.js proces ali zazeni frontend na drugem portu:

```powershell
npm run dev -- --port 3001
```

Ce je port `8080` zaseden, nastavi `SERVER_PORT` v root `.env`.

## Zagon z Dockerjem

Za hiter lokalni zagon celotnega okolja (backend + frontend) uporabite Docker in `docker-compose`.

1. Predpogoj: namestite `Docker` in `docker-compose` ter v korenu projekta napolnite `.env` (kopirajte iz `.env.example`). V `.env` nastavite Supabase povezavo in potrebne ključe.

2. Gradnja in zagon (iz korena projekta):

```bash
docker-compose up --build
```

To bo zgradilo slike za `backend` in `frontend` ter ju zagnalo na vratih:

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`

3. Če želite samo ponovno zgraditi backend ali frontend:

```bash
# rebuild only backend
docker-compose build backend
# rebuild only frontend
docker-compose build frontend
```

4. Zagon v ozadju:

```bash
docker-compose up -d --build
```

5. Ustavitev in odstranitev containerjev:

```bash
docker-compose down
```

Opombe:
- `docker-compose` uporablja vrednosti iz vaše root `.env`; poskrbite, da so `SUPABASE_DB_URL`, `SUPABASE_DB_USER` in `SUPABASE_DB_PASSWORD` pravilno nastavljene za vašo Supabase bazo (ali uporabite lokalno Postgres, če želite).
- Če uporabljate Supabase v oblaku, bo aplikacija zahtevala ustrezne `NEXT_PUBLIC_SUPABASE_*` vrednosti za frontend (nastavite v root `.env` ali v `frontend/.env.local`).
- Volumni `./backend/src` in `./frontend/src` so nastavljeni kot volumi v `docker-compose` za razvojno izkušnjo (če želite hot-reload). Če želite čisto produkcijsko sliko, odstranite volumenne mape iz `docker-compose.yml`.
