# Backend Demo Seed

## Namen

BE/DB-27 doda ponovljive demo podatke za lokalno, razvojno ali namensko demo bazo. Seed pokrije javni pregled turnirjev, organizer tokove, team management, bracket, standings, match import prikaz in analytics/comparison endpoint-e.

Seed ustvari tudi lokalne Supabase Auth email/password uporabnike za demo organizerja in vseh 30 demo igralcev. Auth uporabniki so povezani z obstojecimi deterministcnimi `public.profiles` zapisi prek `profiles.auth_user_id`, email identiteta pa ostane zapisana tudi v `profile_external_accounts`.

## Kaj seed ustvari

- 1 admin profil: `demo.admin@dotaops.local`
- 1 organizer profil: `demo.organizer@dotaops.local`
- 30 player profilov: `demo.player1@dotaops.local` do `demo.player30@dotaops.local`
- Supabase Auth login za `demo.organizer@dotaops.local` in vse `demo.playerX@dotaops.local` racune
- 6 ekip: Radiant Wolves, Dire Ravens, Ancient Titans, Roshan Hunters, Midlane Mages, Rune Raiders
- aktivne rosterje po 5 igralcev na ekipo
- `DotaOps Demo Cup` kot public/live turnir
- `DotaOps Demo Open Qualifier` kot public/registration turnir
- approved, pending in rejected tournament registrations
- group assignment za approved ekipe
- group-stage standings prek zakljucenih tekem
- playoff bracket: semifinal 1, semifinal 2, final
- 7 match series, od tega 5 finished in 2 scheduled
- 12 `match_games` zapisov z `import_status = ready`
- 12 `match_imports` in import evente
- 120 `match_players` zapisov z razlicnimi K/D/A, GPM, XPM, damage in hero podatki
- celoten OpenDota `/api/heroes` reference katalog za Dota 2 heroje, 127 herojev ob zadnji osvezitvi seeda
- pending team invitation in pending join request za team flow

## Demo login racuni

Skupno demo geslo za lokalno/dev/demo okolje je:

```text
DotaOpsDemo123!
```

Uporabni racuni:

- `demo.organizer@dotaops.local`
- `demo.player1@dotaops.local` do `demo.player30@dotaops.local`

Posebej koristni analytics racuni:

- `demo.player1@dotaops.local` - Aegis Ace, Radiant Wolves
- `demo.player7@dotaops.local` - Risky Mid, Dire Ravens
- `demo.player11@dotaops.local` - Titan Carry, Ancient Titans
- `demo.player16@dotaops.local` - Roshan Core, Roshan Hunters

`demo.admin@dotaops.local` ostane samo seedan profil brez Supabase Auth login uporabnika. Namen demo login seta je organizer/player tok, ne deljen admin dostop.

## En ukaz za seed

Predpogoji:

- migracije so ze aplicirane,
- `psql` je na PATH,
- `DATABASE_URL` kaze na lokalno/dev/demo Postgres bazo.

Windows/PowerShell:

```powershell
cd C:\DataOpsProjekt\DotaOps
$env:DATABASE_URL = "postgresql://USER:PASSWORD@HOST:PORT/postgres"
.\scripts\seed-demo.ps1 -ConfirmDemoSeed
```

Z resetom pred seedanjem:

```powershell
.\scripts\seed-demo.ps1 -ConfirmDemoSeed -ResetFirst
```

Wrapper se ustavi brez `-ConfirmDemoSeed`. Ce zazna production-like okolje prek `DOTAOPS_ENV`, `APP_ENV`, `SPRING_PROFILES_ACTIVE` ali `NODE_ENV`, se ustavi, razen ce je dodatno podan `-AllowProductionTarget`.

## Rocni SQL zagon

Ce wrapper ni primeren:

```powershell
psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/demo/demo-seed.sql
psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/demo/verify-demo-seed.sql
```

## Reset demo podatkov

```powershell
psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/demo/reset-demo-seed.sql
```

Reset brise samo stabilne demo UUID-je za BE/DB-27 turnirje, ekipe in profile ter demo Supabase Auth uporabnike z emaili `demo.organizer@dotaops.local` in `demo.playerX@dotaops.local`. Ne uporablja `TRUNCATE` in ne brise realnih podatkov po splosnih pogojih. Hero reference podatki ostanejo, ker so splosni Dota reference podatki in jih lahko uporablja tudi realen import.

## Verifikacija

```powershell
psql $env:DATABASE_URL -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/demo/verify-demo-seed.sql
```

Verify query preveri:

- demo organizer obstaja,
- demo Supabase Auth uporabniki obstajajo za organizer/player racune,
- demo Auth uporabniki imajo potrjen email in email identiteto,
- demo profili so povezani z ustreznimi Auth uporabniki,
- demo profili in ekipe obstajajo,
- public turnirji obstajajo,
- approved/pending registracije obstajajo,
- playoff bracket obstaja,
- completed matches obstajajo,
- `match_games` in `match_players` obstajajo,
- analytics ima igralce z realnimi statistikami.

## Frontend/API mapping

```text
Public tournament list
- endpoint: GET /api/tournaments
- potrebuje: public tournament s statusom registration/published/live/finished
- seed: DotaOps Demo Cup, DotaOps Demo Open Qualifier
- tabele: tournaments, tournament_registrations

Tournament detail
- endpoint: GET /api/tournaments/{slug}
- potrebuje: title, slug, status, organizer, dates, max teams, registration counts
- seed: oba demo turnirja
- tabele: tournaments, profiles, tournament_registrations

Public groups and standings
- endpoint: GET /api/public/tournaments/{id}/groups, GET /api/public/tournaments/{id}/standings
- potrebuje: tournament_groups, tournament_group_teams, finished group matches
- seed: Demo Group A, 4 approved teams, 3 finished group matches
- tabele: tournament_groups, tournament_group_teams, matches

Public matches/results
- endpoint: GET /api/public/tournaments/{id}/matches
- potrebuje: scheduled/finished matches, teams, scores, winner
- seed: group matches, semifinals, final
- tabele: matches, teams

Public bracket
- endpoint: GET /api/public/tournaments/{id}/bracket?stageName=Playoffs
- potrebuje: Playoffs matches and match_slots
- seed: semifinal 1, semifinal 2, final
- tabele: matches, match_slots, teams

Organizer tournaments and registrations
- endpoint: GET /api/organizer/tournaments, GET /api/organizer/tournaments/{id}/registrations
- potrebuje: organizer-owned tournaments, pending/approved/rejected registrations
- seed: demo organizer owns both tournaments; cup has approved/pending/rejected states
- tabele: tournaments, tournament_staff, tournament_registrations, tournament_registration_members

Organizer dashboard
- endpoint: GET /api/me/dashboard, GET /api/organizer/analytics
- potrebuje: tournament counts, pending registrations, processed match games, import jobs
- seed: 2 tournaments, pending registrations, ready match_games, ready match_imports
- tabele: tournaments, tournament_registrations, matches, match_games, match_imports

My Team / roster
- endpoint: GET /api/me/team, GET /api/teams, GET /api/teams/{id}/members/{profileId}/profile
- potrebuje: active team, captain_profile_id, active team_members, roster profile stats
- seed: 6 active teams, captain is active roster member, match_players stats for 4 teams
- tabele: teams, team_members, profiles, match_players, heroes

Team invitations and join requests
- endpoint: GET /api/me/team-invitations, GET /api/teams/{id}/join-requests
- potrebuje: pending invitation/request rows
- seed: one pending invitation and one pending join request
- tabele: team_invitations, team_join_requests

Public analytics
- endpoint: GET /api/public/analytics/players|teams|heroes|tournaments
- potrebuje: public tournament, match_games, match_players, heroes
- seed: 12 ready games and 120 player rows
- tabele: tournaments, matches, match_games, match_players, heroes

Role-based analytics
- endpoint: GET /api/me/analytics, GET /api/me/team/analytics, GET /api/organizer/tournaments/{id}/analytics
- potrebuje: scoped profile/team/tournament analytics data
- seed: players and teams with varied wins, losses, KDA and hero picks
- tabele: profiles, teams, team_members, tournaments, matches, match_games, match_players

Lookup dropdowns
- endpoint: GET /api/organizer/lookups/tournaments, GET /api/me/lookups/teams, GET /api/teams/{id}/lookups/players, GET /api/lookups/heroes
- potrebuje: manageable tournaments, active teams, active team players, heroes
- seed: demo organizer tournaments, team memberships, celoten OpenDota hero reference katalog
- tabele: tournaments, tournament_staff, teams, team_members, profiles, heroes

Team vs team comparison
- endpoint: GET /api/analytics/compare/teams
- potrebuje: two teams with shared tournament match_players rows
- seed: Radiant Wolves vs Ancient Titans/Dire Ravens/Roshan Hunters data
- tabele: teams, tournaments, matches, match_games, match_players

Player vs player comparison
- endpoint: GET /api/analytics/compare/players
- potrebuje: two profiles with match_players stats and overlapping heroes
- seed: slot-based hero assignment gives shared Invoker/core hero data; player-01 has high KDA, player-07 high deaths
- tabele: profiles, teams, heroes, match_players
```

## Analytics refresh

`demo-seed.sql` and `reset-demo-seed.sql` call `private.refresh_dotaops_analytics()` at the end. Current backend analytics repositories mostly query normalized base tables, but refreshing keeps materialized analytics structures consistent for older/admin flows.

## Varnostno opozorilo

- Ne poganjaj na produkciji brez eksplicitne potrditve.
- Ne vsebuje realnih emailov, osebnih podatkov ali skrivnosti.
- Skupno demo geslo je namenjeno samo lokalnemu/dev/demo okolju.
- Ne vsebuje Supabase service role keyjev, JWT skrivnosti ali connection stringov.
- Wrapper ne bere ali zapisuje `.env`; connection string mora biti podan iz okolja ali parametra.

## Omejitve

- Seed neposredno pise v `auth.users` in `auth.identities`, zato je namenjen Supabase Postgres okoljem z aplicirano Auth shemo.
- Storage datoteke za avatarje/logotipe/bannerje niso nalozene; URL-ji so placeholderji na `example.invalid`.
- Seed uporablja sinteticne OpenDota/match ID-je in ne predstavlja realnih tekem.
- Reset ne cisti audit log zgodovine, ki jo sprozijo audit triggerji med seedanjem; to je namerno, ker audit trail ostane append-only.
