# Backend Analytics API

Ta dokument opisuje backend pogodbo za FE-15 analytics. Vsi endpointi vracajo samo
realne podatke iz baze. Ce podatkov ni, backend vrne `null`, `0` ali prazne sezname.

## Filtri

Skupni filtri, kjer so smiselni:

- `tournamentId` ali `tournament_id`
- `teamId` ali `team_id`
- `profileId` ali `profile_id`
- `heroId` ali `hero_id`
- `from`
- `to`
- `limit`

`limit` ima privzeto vrednost `10` in maksimalno vrednost `100`.

`from` in `to` sta ISO datetime vrednosti. Casovni filter uporablja:

```sql
coalesce(
  match_games.started_at,
  match_games.finished_at,
  matches.started_at,
  matches.finished_at,
  matches.scheduled_at,
  match_games.created_at,
  matches.created_at
)
```

Semantika je `from <= timestamp < to`. `from > to` vrne `400 BAD_REQUEST`.
Pri import summary podatkih se za import filtre uporablja
`coalesce(match_imports.completed_at, match_imports.started_at, match_imports.requested_at, match_imports.created_at)`.

## Role-based analytics

### Current player

`GET /api/me/analytics`

Dostop: samo `ROLE_PLAYER`.

Query parametri: skupni filtri. `profileId` je lahko prazen ali enak trenutnemu profilu.
Drug `profileId` vrne `403 FORBIDDEN`.

Response:

- `metrics`: agregati za trenutnega igralca
- `heroPerformance`: hero agregati za trenutnega igralca
- `matchHistory`: trenutno stabilen empty-state seznam

`GET /api/me/analytics/progress` vraca surove match-level vrednosti za trenutnega
igralca. Te vrednosti se ne utezujejo in ostanejo enake podatkom iz normaliziranega
`match_players` vira.

`GET /api/me/analytics/insights` vraca determinicne interpretacije za trenutnega
igralca. Trend insighti lahko uporabijo context-aware utezi za zelo slabe ali stomp
igre, vendar samo pri interpretaciji dolgorocnih povprecij. Surovi progress in match
history podatki se ne spremenijo.

Context weight ima razpon `0.35` do `1.00`:

- `1.00`: normalna igra ali premalo konteksta za varno korekcijo.
- `0.35`: minimalna utez za ekstremno slab/stomp kontekst.
- `classification`: `NORMAL`, `ROUGH_GAME`, `STOMP_LOSS` ali `LOW_CONFIDENCE`.
- `reasons`: determinicni razlogi, npr. `HIGH_DEATHS`, `LOW_KDA`,
  `LOW_OBJECTIVE_PRESSURE`, `TEAM_SCORE_DISADVANTAGE`, `SUPPORT_IMPACT_PROTECTED`,
  `INSUFFICIENT_BASELINE`.

Trenutna formula deluje on-the-fly iz obstojecih polj:

- high deaths: lazja/srednja/mocna kazen pri 7/10/14+ deaths,
- low KDA: kazen pod `1.50`, mocnejsa pod `1.00` in `0.60`,
- low objective pressure: nizka tower damage vrednost skupaj z nizkim damage ali
  assists kontekstom,
- team score disadvantage: uporabi team side in radiant/dire score, kadar sta na voljo,
- support protection: visoki assists ali healing zmanjsajo kazen, da support impact ni
  prevec kaznovan.

`PlayerInsightResponse` ima additivno polje `contextWeight`, kadar je insight nastal
iz utezenega rough-game konteksta ali ko endpoint vrne locen `contextWeight` insight.
Stari response fieldi ostanejo nespremenjeni.

### Current player team

`GET /api/me/team/analytics`

Dostop: samo `ROLE_PLAYER`.

`teamId` je lahko prazen ali enak trenutni aktivni ekipi. `profileId`, ce je poslan,
mora biti trenutni aktivni clan te ekipe. Drugace backend vrne `403 FORBIDDEN`.

Response:

- `team`
- `teamSummary`
- `rosterPerformance`
- `recentTeamMatches`

### Organizer overview

`GET /api/organizer/analytics`

Dostop: `ROLE_ORGANIZER` ali `ROLE_ADMIN`.

Vrne realne count agregate za organizerjeve manageable turnirje. `tournamentId`, `teamId`,
`from` in `to` se uporabijo tam, kjer imajo realen vir. `profileId` in `heroId` se
uporabita pri match/import agregatih, kjer obstajajo `match_players` podatki.

### Organizer tournament analytics

`GET /api/organizer/tournaments/{tournamentId}/analytics`

Dostop: organizer/admin, ki lahko upravlja podani turnir.

Path `tournamentId` je avtoritativen. Ce query `tournamentId` ali `tournament_id`
nasprotuje path vrednosti, backend vrne `400 BAD_REQUEST`.

Response vsebuje:

- `gamesProcessed`
- `matchesWithoutImport`
- `importCoveragePercent`
- `avgDurationSeconds`
- `tournamentSummary`
- `topTeams`
- `heroMetrics`
- `teamComparison`
- `recentImports`

## Lookup endpointi

### Organizer tournaments

`GET /api/organizer/lookups/tournaments?limit=10`

Dostop: `ROLE_ORGANIZER` ali `ROLE_ADMIN`.

Vrne manageable turnirje trenutnega organizerja/admina:

- `tournamentId`
- `title`
- `status`

### My teams

`GET /api/me/lookups/teams?limit=10`

Dostop: `ROLE_PLAYER`.

Vrne trenutne aktivne ekipe igralca. Trenutni poslovni model praviloma pomeni 0 ali 1
aktivno ekipo.

### Team players

`GET /api/teams/{teamId}/lookups/players?limit=10`

Dostop: authenticated user. Service dovoli samo:

- aktivnega clana ekipe,
- organizerja, ce je ekipa povezana z njegovim manageable turnirjem,
- admina.

### Heroes

`GET /api/lookups/heroes?limit=10`

Dostop: public.

Vrne referencne hero podatke iz `public.heroes`.

## Team vs Team comparison

`GET /api/analytics/compare/teams`

Query parametri:

- `teamAId` required
- `teamBId` required
- `tournamentId` ali `tournament_id`
- `profileId` ali `profile_id`
- `heroId` ali `hero_id`
- `from`
- `to`
- `limit`

Dostop:

- admin: protected agregati,
- organizer: zahteva `tournamentId` in `canManage(tournamentId)`,
- player: dovoljena public-safe primerjava, ce je clan ene od primerjanih ekip.

Response vsebuje `teamA`, `teamB`, `teams`, `recentMatches` in `filters.accessScope`.
`heroMetrics` je trenutno prazen seznam, ker ni locenega realnega queryja za multi-team
hero comparison.

## Player vs Player comparison

`GET /api/analytics/compare/players`

Query parametri:

- `profileAId` required
- `profileBId` required
- `tournamentId` ali `tournament_id`
- `teamId` ali `team_id`
- `heroId` ali `hero_id`
- `from`
- `to`
- `limit`

Dostop:

- admin: protected agregati,
- organizer: zahteva `tournamentId` in `canManage(tournamentId)`,
- player: protected agregati samo, ce sta primerjana igralca v isti aktivni ekipi kot
  trenutni igralec; sicer lahko igralec primerja sebe z drugim igralcem samo v
  public-safe scope-u.

KDA se racuna iz normaliziranih `match_players` vrstic:

```sql
(sum(kills) + sum(assists)) / greatest(sum(deaths), 1)
```

Response vsebuje:

- `playerA`
- `playerB`
- `players`
- `profileAHeroPerformance`
- `profileBHeroPerformance`
- `sharedHeroes`
- `recentMatches`
- `filters.accessScope`

`recentMatches` vrne samo tekme/igre, kjer sta oba primerjana igralca prisotna v
normaliziranih match player podatkih.

## Error primeri

- `401 UNAUTHORIZED`: manjkajoc ali neveljaven JWT/Steam session za protected route.
- `403 FORBIDDEN`: uporabnik nima role ali scope pravic.
- `400 BAD_REQUEST`: `from > to`, enaka primerjana igralca/ekipi, manjkajoc
  organizer `tournamentId` pri primerjavi ali neskladen path/query `tournamentId`.

## Migracija in indeksi

`V33__analytics_filter_lookup_indexes.sql` doda indekse za casovne analytics filtre,
import filtre in active roster lookup:

- `matches(scheduled_at|started_at|finished_at)`
- `match_games(started_at|finished_at)`
- `match_imports(requested_at|completed_at)`
- `team_members(profile_id, team_id) where is_active`

## Znane omejitve

- Match history v role-based `/api/me/...` endpointih ostaja empty-state, dokler ni
  locen produktni dogovor za prikaz osebne zgodovine tekem.
- Team comparison `heroMetrics` je prazen seznam, ker trenutni repository nima realnega
  multi-team hero comparison queryja.
- Organizer comparison brez `tournamentId` je zavrnjen, da ne bi endpoint po nesreci
  agregiral podatkov iz turnirjev, ki niso v organizerjevem scope-u.
- Podatki so odvisni od normaliziranih `match_players`, `match_games`, `matches` in
  `match_imports` vrstic. Ce import ni zakljucen ali igralci niso povezani s profili,
  agregati ostanejo prazni/default.

## Testi

Relevantni testi:

- `AnalyticsFiltersTest`
- `AnalyticsRepositoryTest`
- `RoleBasedAnalyticsServiceTest`
- `AnalyticsLookupServiceTest`
- `AnalyticsComparisonServiceTest`
- `PublicAnalyticsControllerTest`
- `SecurityConfigTest`

Zagon:

```bash
cd backend
./mvnw test
```

Na Windows:

```powershell
cd backend
.\mvnw.cmd test
```
