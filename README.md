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
FRONTEND_AUTH_REDIRECT_URL=http://localhost:3000/profile
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

### Hero reference sync

Dota heroji se ne vnasajo rocno s SQL inserti. Backend jih sinhronizira iz OpenDota endpointa `/heroes` v tabelo `public.heroes`, kjer je stabilni unique kljuc `dota_hero_id`.

Rocni sync sprozi admin uporabnik:

```http
POST /api/admin/heroes/sync
```

Endpoint je pod `/api/admin/**` in zahteva backend admin vlogo. V deploy okolju naj ostane za admin/server tok, ne za javni frontend tok.

Primer response:

```json
{
  "data": {
    "syncedCount": 124,
    "insertedCount": 0,
    "updatedCount": 124,
    "startedAt": "2026-05-21T09:00:00Z",
    "finishedAt": "2026-05-21T09:00:02Z"
  }
}
```

Sync je idempotenten: ponovni zagon ne podvoji herojev, ampak obstojece zapise posodobi po `dota_hero_id`. `image_url` in `icon_url` sta deterministicno izpeljana iz OpenDota `name`, na primer `npc_dota_hero_antimage` uporabi asset `antimage.png`. Priporoceno je sync zagnati po prvem deployu oziroma po migracijah, nato periodicno ali kadar se spremenijo referencni podatki. Match import pri shranjevanju `match_players` uporabi `heroes.dota_hero_id` za nastavitev `match_players.hero_id`; ce hero se ni znan, import ne pade, `dota_hero_id` pa ostane zapisan za diagnostiko.

### Match import lifecycle

Import OpenDota match podatkov sprozis z avtenticiranim organizer/admin uporabnikom:

```http
POST /api/match-imports
Content-Type: application/json

{
  "dotaMatchId": "7894561230"
}
```

Import je idempotenten po `dotaMatchId`: isti OpenDota match id uporablja isti `match_imports` zapis in ne ustvarja podvojenih importov. Sinhroni backend flow zapise statuse `queued`, `processing`, nato `ready` ali `error`. Ce je import ze `ready` ali `processing`, POST vrne obstojeci zapis brez ponovnega uvoza. Ce je import v `error`, isti POST izvede nadzorovan retry na istem `match_imports.id`; na voljo je tudi:

```http
POST /api/match-imports/{id}/retry
```

Frontend lahko spremlja trenutno stanje in zgodovino:

```http
GET /api/match-imports/{id}
GET /api/match-imports/by-match/{dotaMatchId}
GET /api/match-imports/{id}/events
```

Response vsebuje `id`, `dotaMatchId`, `status`, `errorCode`, `errorMessage`, casovne oznake in `events`. `errorCode` je tehnicna kategorija napake OpenDota providerja, `errorMessage` pa je varen prikaz za frontend brez stack trace-a ali skrivnosti. Status `match_games.import_status` se ob povezani `match_game_id` sinhronizira z `match_imports.status`.

### Match import normalization

OpenDota raw match response se shrani samo za server/admin debug tok. Public/frontend response DTO-ji ne vracajo `raw_response`, `normalized_payload` ali `raw_player`.

Ob uspesnem importu backend normalizira podatke v relacijski tabeli:

- `match_games`: `dota_match_id`, `duration_seconds`, `started_at`, `finished_at`, `radiant_win`, `game_mode`, `lobby_type`, rezultat, `winner_side`, `import_status`, `raw_response` in `normalized_payload`.
- `match_players`: `match_game_id`, `player_slot`, `team_side`, `hero_id`, `dota_hero_id`, `dota_account_id`, `profile_id`, `steam_account_id`, K/D/A, GPM, XPM, damage, healing, last hits, denies, net worth, level, `duration_seconds` in `items`.

Analitika naj uporablja `match_games` in `match_players`, ne OpenDota raw JSON-a. `normalized_payload` vsebuje interni povzetek normalizacije, na primer `source`, `version`, `normalizedAt`, `playersNormalized`, `radiantPlayers`, `direPlayers` in `durationSeconds`.

Import je idempotenten po `dota_match_id`; playerji so idempotentni po `match_game_id + player_slot`. Ponovni import iste igre posodobi obstojece match/player zapise in ne ustvari podvojenih player slotov. Manjkajoc `account_id` ali manjkajoc lokalni profil igralca importa ne prekine; `dota_account_id`, `profile_id` in `steam_account_id` ostanejo `null`, kjer podatka ni mogoce povezati.

Pred match importom je priporocljivo zagnati hero sync. Ce `heroes.dota_hero_id` obstaja, se `match_players.hero_id` nastavi na interni hero zapis. Ce hero se ni sinhroniziran, import ne pade in `match_players.dota_hero_id` ostane zapisan za poznejso diagnostiko.

### Analytics

Analitika uporablja normalizirane tabele `match_games`, `match_players`, `heroes`, `teams`, `profiles`, `matches` in `tournaments`. OpenDota `raw_response`, `normalized_payload` in `raw_player` niso del javnega analytics API-ja.

Admin uporabnik lahko rocno sprozi refresh:

```http
POST /api/admin/analytics/refresh
```

Endpoint je pod `/api/admin/**` in zahteva backend admin vlogo. V realnem okolju mora biti dostopen samo zaupanja vrednim admin uporabnikom oziroma server operacijam.

Backend pri tem poklice DB funkcijo `private.refresh_dotaops_analytics()`. Response vsebuje `status`, `reason`, `requestedAt`, `completedAt`, `durationMs` in `message`. Po uspesnem match importu backend sprozi asinhroni refresh request z razlogom `match import ready: <dotaMatchId>`; ce ta refresh pade, import ostane `ready`, napaka pa se zabelezi v backend log.

Javni analytics endpointi so:

```http
GET /api/public/analytics/players
GET /api/public/analytics/teams
GET /api/public/analytics/heroes
GET /api/public/analytics/tournaments
GET /api/public/analytics/tournaments/{tournamentId}
```

Collection endpointi podpirajo filtre `tournamentId`, `teamId`, `profileId`, `heroId` in `limit`; podprta je tudi snake_case oblika `tournament_id`, `team_id`, `profile_id`, `hero_id`. Primeri:

```http
GET /api/public/analytics/players?tournamentId=<uuid>&profileId=<uuid>
GET /api/public/analytics/teams?tournament_id=<uuid>&team_id=<uuid>
GET /api/public/analytics/heroes?heroId=<uuid>
```

API vraca osnovne agregate, kot so `gamesPlayed`, `wins`, `losses`, `winRate`, KDA, skupni in povprecni kills/deaths/assists, GPM, XPM in hero damage, kjer so metrike smiselne. Tournament metrics vrne tudi `teamsCount`, `playersCount`, `heroesPickedCount`, `avgDurationSeconds`, `avgKillsPerGame`, `avgKda` in osnovni `mostPickedHeroes`.

Vsi javni analytics queryji filtrirajo samo turnirje z `tournaments.is_public = true`. Ce filter kaze na privaten ali neobjavljen turnir, public endpoint ne vrne njegovih podatkov.

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

Production deployment (hosted): https://dotaops-frontend.vercel.app/

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
- `NEXT_PUBLIC_API_URL` uporablja brskalnik, zato pri lokalnem Docker zagonu ostane `http://localhost:8080/api`.
- Server-side renderiranje znotraj frontend containerja uporablja `COMPOSE_NEXT_SERVER_API_URL`, ki je privzeto `http://backend:8080/api`. Nastavite jo le, ce backend tece izven Compose omrezja.
- `docker-compose` uporablja vrednosti iz vaše root `.env`; poskrbite, da so `SUPABASE_DB_URL`, `SUPABASE_DB_USER` in `SUPABASE_DB_PASSWORD` pravilno nastavljene za vašo Supabase bazo (ali uporabite lokalno Postgres, če želite).
- Če uporabljate Supabase v oblaku, bo aplikacija zahtevala ustrezne `NEXT_PUBLIC_SUPABASE_*` vrednosti za frontend (nastavite v root `.env` ali v `frontend/.env.local`).
- Volumni `./backend/src` in `./frontend/src` so nastavljeni kot volumi v `docker-compose` za razvojno izkušnjo (če želite hot-reload). Če želite čisto produkcijsko sliko, odstranite volumenne mape iz `docker-compose.yml`.
