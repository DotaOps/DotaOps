# DotaOps: pregled trenutnega stanja projekta

Datum pregleda: 2026-06-02

## 1. Povzetek projekta

DotaOps je produkcijsko zasnovana aplikacija za vodenje Dota 2 turnirjev. Repozitorij vsebuje:

- Spring Boot backend API;
- Next.js frontend;
- Supabase/PostgreSQL shemo, ki jo gradi Flyway;
- Supabase RLS in grant pravila za neposredne Supabase dostope;
- OpenDota in Steam integraciji;
- backend teste, vkljucno s PostgreSQL integracijskimi testi;
- GitHub Actions preverjanja za backend in frontend.

Jedro turnirskega toka je ze implementirano: profili, ekipe, rosterji, prijave, odobritve, check-in, skupine, standings, single-elimination bracket, razporejanje tekem, rezultati, napredovanje v bracketu, OpenDota import, normalizacija, analitika, audit in notification outbox.

Frontend ni povsem enakomerno povezan z backendom. Turnirski organizer in javni prikazi so dobro povezani. Nekateri novejsi backend deli se nimajo UI integracije: join requests, in-app notifications, backend Steam logout, rocni OpenDota profile sync ter novi role-based dashboard in analytics endpointi. Frontend dashboard trenutno uporablja dokumentiran empty state, dokler ni povezan z `GET /api/me/dashboard`.

Ta dokument opisuje dejansko stanje repozitorija. Predlogi so loceni v poglavjih 15 in 16.

## 2. Tehnoloski stack

| Podrocje | Trenutna tehnologija | Dokaz |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 4.0.6, Spring MVC, Spring Security | `backend/pom.xml` |
| Dostop do baze | `JdbcTemplate` repository razredi | npr. `TournamentRepository`, `MatchImportRepository`, `AnalyticsRepository` |
| JPA | Odvisnost je prisotna, vendar ni najdenih `@Entity` razredov ali Spring Data repository interface-ov | `backend/pom.xml`, pregled `backend/src/main/java` |
| Migracije | Flyway, 30 migracij | `backend/src/main/resources/db/migration/` |
| Baza | Supabase PostgreSQL | `supabase/config.toml`, `backend/src/main/resources/application.properties` |
| Frontend | Next.js 16.2.6 App Router, React 19.2.5, TypeScript 6 | `frontend/package.json` |
| Frontend auth | Supabase JS in `@supabase/ssr` | `frontend/src/lib/auth.ts`, `frontend/src/lib/supabase/`, `frontend/src/proxy.ts` |
| Zunanje integracije | OpenDota API, Steam OpenID in Steam Web API | `OpenDotaClient`, `SteamAuthService`, `SteamOpenIdClient` |
| Slike | Supabase Storage REST API s server-side service-role kljucem | `SupabaseImageStorageService` |
| Build | Maven Wrapper, npm, Docker multi-stage build | `backend/mvnw.cmd`, `frontend/package.json`, oba `Dockerfile` |
| CI | GitHub Actions za backend in frontend | `.github/workflows/backend-ci.yml`, `.github/workflows/frontend-ci.yml` |

Pomembna arhitekturna posledica: backend je JDBC aplikacija. Nastavitev `spring.jpa.hibernate.ddl-auto=validate` trenutno ne nadomesca Flyway in ne validira JDBC modela na enak nacin kot pri JPA entitetah.

## 3. Struktura repozitorija

```text
DotaOps/
  .github/workflows/          CI za backend in frontend
  backend/                    Spring Boot API, testi in Flyway migracije
  docs/                       projektna dokumentacija
  frontend/                   Next.js App Router aplikacija
  supabase/                   Supabase CLI konfiguracija in post-Flyway hardening
  docker-compose.yml          backend + frontend containerja
  README.md                   glavni lokalni vodič
```

### Backend

```text
backend/src/main/java/si/um/feri/dotaops/backend/
  analytics/      javna analitika in refresh
  audit/          admin audit pregled
  auth/           Supabase JWT in Steam auth
  common/         API envelope, napake, pagination, security helperji
  config/         security, async in HTTP konfiguracija
  dashboard/      role-based uporabniski dashboard agregati
  notification/   durable outbox in in-app obvestila
  opendota/       OpenDota client, hero sync, match import, normalizacija
  profile/        profili in OpenDota bootstrap
  storage/        Supabase image upload
  team/           ekipe, rosterji, invite in join-request tokovi
  tournament/     turnirji, prijave, skupine, standings, bracketi in tekme
  web/            health endpoint
```

Mapi `backend/.../match/` in `backend/.../registration/` sta prazni. Aktivna logika za tekme in prijave je pod `tournament/`.

### Frontend

```text
frontend/src/
  app/            App Router strani
  components/     UI komponente in vecji workspace paneli
  lib/            API adapterji, auth, Supabase helperji, tipi in mock podatki
  proxy.ts        Supabase SSR session refresh
```

### Baza

Flyway migracije so v `backend/src/main/resources/db/migration/`. Mapa `supabase/` ni lastnik sheme; vsebuje Supabase konfiguracijo in rocni `post_flyway_hardening.sql`.

## 4. Backend arhitektura

### Organizacija slojev

Backend uporablja feature-oriented organizacijo:

1. `web` controller sprejme HTTP zahtevo in vrne `ApiResponse<T>`.
2. DTO/request record validira obliko vhodnih podatkov z Jakarta Bean Validation.
3. `service` razred izvede poslovna pravila in avtorizacijo.
4. `repository` razred uporablja parameteriziran `JdbcTemplate` SQL.
5. Flyway migracije dodajo se DB constraints, triggerje, indekse, grante in RLS.

Primer turnirja:

- `TournamentController`
- `TournamentService`
- `TournamentRepository`
- DTO-ji v `backend/.../tournament/dto/`
- tabela `public.tournaments`

Primer ekipe:

- `TeamController`, `TeamRosterController`, `TeamJoinRequestController`
- `TeamService`, `TeamRosterService`, `TeamJoinRequestService`
- repository razredi v `backend/.../team/repository/`
- tabele `public.teams`, `public.team_members`, `public.team_manual_players`, `public.team_invitations`, `public.team_join_requests`

### Validacija

Validacija je vecnivojska:

- request DTO-ji uporabljajo `@Valid`;
- servisi preverjajo poslovna pravila, npr. vlogo captaina, roster size, status prijave, okna za registration/check-in in veljavnost rezultata;
- baza uveljavlja FK-je, unique indekse, check constraints in triggerje.

Primeri:

- `TournamentRegistrationService.registerTeam()` preveri captaina, registration window, podvojeno prijavo in roster velikost.
- `TournamentBracketService.generateBracket()` preveri format in stevilo odobrenih ekip.
- `MatchManagementService.submitResult()` preveri udelezence, rezultat serije in zmagovalca.
- `V26__team_manual_players_images_team_size_join_requests.sql` omeji podprte `teamSize` vrednosti na `1`, `3`, `5`.

### Obravnava napak

`GlobalExceptionHandler` standardizira napake. Pricakovane poslovne napake uporabljajo lastne exception razrede, npr. `BadRequestException`, `ConflictException`, `ResourceNotFoundException`. Nepricakovana napaka vrne sanitizirano sporocilo `Unexpected server error`, ne stack trace-a.

### Varnostni sloj

`SecurityConfig` nastavi stateless Spring Security filter chain. `SupabaseJwtAuthenticationFilter` podpira:

- Supabase bearer JWT;
- fallback Steam session cookie.

`SupabaseJwtVerifier` podpira `HS256`, `RS256` in `ES256`, nato rocno preveri subject UUID, issuer, audience, expiry in `not-before`. `SupabaseAuthorities` vlogo prebere iz DB profila, ne iz uporabniskega JWT metadata.

Service razredi ponovno preverjajo lastnistvo in pravice. To je pomembno, ker so nekateri `/api/organizer/tournaments/**` route-i v `SecurityConfig` namenoma samo `authenticated()`, natancna pravica pa se preveri v servisu z `TournamentRepository.canManage(...)`.

### Glavni backend feature-i

| Feature | Controller | Service | Glavne tabele |
| --- | --- | --- | --- |
| Profili | `ProfileController` | `ProfileService` | `profiles`, `profile_external_accounts` |
| Steam auth | `SteamAuthController` | `SteamAuthService` | `private.steam_login_states`, `profile_external_accounts`, `profiles` |
| Ekipe | `TeamController` | `TeamService` | `teams`, `team_manual_players` |
| Roster in povabila | `TeamRosterController` | `TeamRosterService` | `team_members`, `team_manual_players`, `team_invitations` |
| Join requests | `TeamJoinRequestController` | `TeamJoinRequestService` | `team_join_requests`, `team_members` |
| Turnirji | `TournamentController` | `TournamentService` | `tournaments`, `tournament_staff` |
| Prijave | `TournamentRegistrationController` | `TournamentRegistrationService` | `tournament_registrations`, `tournament_registration_members`, `notification_outbox` |
| Skupine in standings | `TournamentGroupController` | `TournamentGroupService` | `tournament_groups`, `tournament_group_teams`, `v_group_standings` |
| Bracket | `TournamentBracketController` | `TournamentBracketService` | `matches`, `match_slots`, `tournament_registrations` |
| Tekme | `MatchManagementController` | `MatchManagementService`, `MatchAdvancementService` | `matches`, `match_slots`, `match_advancement_audit_logs`, `notification_outbox` |
| Javni agregati | `PublicTournamentController` | `PublicTournamentService` | turnirske tabele in view-i |
| OpenDota import | `MatchImportController` | `MatchImportService`, `OpenDotaMatchNormalizationService` | `match_imports`, `match_import_events`, `match_games`, `match_players` |
| Hero sync | `HeroSyncController` | `HeroSyncService` | `heroes` |
| Dashboard | `DashboardController` | `DashboardService` | profili, ekipe, invitations, registrations, turnirji, match games, imports |
| Analitika | `PublicAnalyticsController`, `AdminAnalyticsController`, `MeAnalyticsController`, `OrganizerAnalyticsController` | `AnalyticsQueryService`, `AnalyticsRefreshService`, `RoleBasedAnalyticsService` | normalizirane match tabele, analytics view-i in materialized view-i |
| Audit | `AdminAuditLogController` | `AdminAuditLogService` | `audit_log` |
| Obvestila | `NotificationController`, `AdminNotificationOutboxController` | `NotificationService`, `NotificationOutboxProcessor` | `notification_outbox` |

## 5. Frontend arhitektura

### Routi

| Stran | Namen | Dostop po `route-access.ts` |
| --- | --- | --- |
| `/` | javna vstopna stran | public |
| `/login`, `/register` | Supabase email/password auth | public |
| `/portal-entry` | prehod po prijavi | transition |
| `/turnirji` | katalog turnirjev | public-content |
| `/turnirji/[slug]` | javni turnirski control-room prikaz | public-content |
| `/dashboard` | role dashboard | auth |
| `/ekipe` | upravljanje ekipe | auth |
| `/analitika` | javna analitika v prijavljenem shellu | auth |
| `/organizator` | organizer workspace | organizer |
| `/profile` | profil, avatar, Steam povezava | auth |
| `/admin/audit` | audit UI | admin |

`AppShell` izvaja client-side UX gating. To ni varnostna meja. Pravo avtorizacijo izvajata backend in Supabase RLS.

`frontend/src/proxy.ts` osvezuje Supabase sejo prek helperja v `frontend/src/lib/supabase/middleware.ts`. Ne izvaja backend role avtorizacije.

### API adapterji

| Adapter | Podrocje |
| --- | --- |
| `frontend/src/lib/api.ts` | skupni API request helperji, bearer token, cookies, server/client base URL |
| `frontend/src/lib/auth.ts` | Supabase auth, backend profil, Steam link, avatar upload, fallback na neposredni Supabase profil |
| `frontend/src/lib/tournament-data.ts` | turnirski katalog in organizer CRUD |
| `frontend/src/lib/tournament-registration-data.ts` | prijave, review in check-in |
| `frontend/src/lib/tournament-group-data.ts` | skupine in standings |
| `frontend/src/lib/tournament-bracket-data.ts` | bracket prikaz in generiranje |
| `frontend/src/lib/tournament-match-data.ts` | match prikaz, schedule, start, cancel, finish, result |
| `frontend/src/lib/match-import-data.ts` | OpenDota import jobs |
| `frontend/src/lib/analytics-data.ts` | javna analitika in admin refresh |
| `frontend/src/lib/team-data.ts` | ekipe, rosterji, manual players in invitations |
| `frontend/src/lib/admin-audit-data.ts` | audit pregled |

Obstajajo tudi starejsi ali delno prekrivajoci adapterji: `data.ts`, `organizer-tournament-data.ts`, `organizer-match-data.ts`, `dashboard-production-data.ts` in `role-dashboard-data.ts`.

### Trenutno implementirani UI tokovi

- javni katalog turnirjev;
- javni prikaz podrobnosti turnirja;
- javni live polling skupin, standings, bracketov in tekem;
- prijava ekipe in check-in;
- organizer CRUD, publish in archive;
- organizer review prijav;
- organizer upravljanje skupin;
- organizer generiranje bracketa;
- organizer upravljanje urnika in rezultatov;
- organizer OpenDota match import;
- analitika;
- ekipe, rosterji, manual players in invitations;
- profil, avatar upload in Steam link;
- admin audit pregled.

### UI deli, ki se niso povezani

- `TeamJoinRequestController` obstaja, UI pa prikaze `Join request management will be available in a later update.` v `team-management-page.tsx`.
- `NotificationController` obstaja, frontend notification adapter ali prikaz ni najden.
- backend `POST /api/auth/steam/logout` obstaja, `signOutCurrentUser()` pa trenutno izvede samo `supabase.auth.signOut()`.
- backend `POST /api/me/opendota/sync` obstaja, profilni UI pa sporoci, da endpoint se ni na voljo.
- `data.ts` za `/matches` in `/roadmap` uporablja mock fallback; backend list endpoint `/api/matches` in `/api/roadmap` controller nista najdena.
- dashboard komponente vsebujejo empty-state podatke in se ne klicejo novega `GET /api/me/dashboard`.

## 6. Baza podatkov in migracije

### Evolucija sheme

| Migracija | Glavni namen |
| --- | --- |
| `V1__initial_dotaops_schema.sql` | osnovni profili, ekipe, turnirji, prijave, tekme, importi, heroji, player stats, RLS in analytics materialized view-i |
| `V2__professional_tournament_database.sql` | staff, skupine, roster snapshoti, invitations, match slots/games, import eventi, outbox, audit, strožji constraints, view-i |
| `V3__harden_auth_and_steam_identity.sql` | private Steam state, Steam helperji, granti in RLS hardening |
| `V4`-`V8` | Steam helperji, indeksi in registration RLS popravek |
| `V9`-`V12` | OpenDota profile bootstrap, CI nickname unique, audit actor fallback, auth.users profile trigger |
| `V13`-`V14` | registration visibility za team memberje, odlog referee/analyst write pravic |
| `V15` | Supabase Storage bucket |
| `V16` | group standings semantika in validation trigger |
| `V17`-`V19` | single-elimination sloti, match metadata, advancement locki in audit |
| `V20`-`V24` | OpenDota error kode, hero reference sync, normalizacija in public analytics |
| `V25` | povezovanje neuporabljenih Steam placeholder profilov |
| `V26` | team images, manual players, team size in join requests |
| `V27` | uporabni notification outbox servis |
| `V28` | persisted profile role allowlist in team permission hardening: player-only create, captain/admin team RLS |
| `V29` | `audit_log(created_at desc, id desc)` indeks za newest-first admin paginacijo in casovne filtre |
| `V30` | reaktivacija ali backfill manjkajocih captain roster zapisov; owner/captain je tudi aktiven `team_members` participant |

Integracijski test je na cisti PostgreSQL 16 bazi uspesno izvedel vseh 30 migracij.

### Pomembne tabele

| Tabela | Namen in glavni stolpci | Povezave | Backend uporaba |
| --- | --- | --- | --- |
| `auth.users` | Supabase-managed uporabniki | `profiles.auth_user_id` | Supabase auth; lokalno jo bootstrapne CI |
| `profiles` | javni profil, vloga, Steam mirror, OpenDota account in sync casi | `auth.users`, vec poslovnih tabel | `ProfileRepository`, auth authorities |
| `profile_external_accounts` | zunanji racuni; Steam je source of truth za povezave | `profiles` | Steam private SQL helperji |
| `private.steam_login_states` | kratkozivi hashani OpenID state zapisi | opcijsko profil in auth user | `SteamLoginStateRepository` |
| `teams` | ime, slug, captain, slike, regija | `profiles`, `auth.users` | `TeamRepository` |
| `team_members` | aktivni roster in vloga | `teams`, `profiles` | `TeamMemberRepository` |
| `team_manual_players` | rocni roster brez DotaOps profila; display name, nickname, note | `teams` | `TeamManualPlayerRepository` |
| `team_invitations` | povabila po profilu ali emailu | `teams`, `profiles` | `TeamInvitationRepository` |
| `team_join_requests` | prosnje igralcev za vstop v ekipo | `teams`, `profiles` | `TeamJoinRequestRepository` |
| `tournaments` | lifecycle, format, settings, termini, javnost, owner | `profiles`, `auth.users` | `TournamentRepository` |
| `tournament_staff` | owner/organizer/referee/analyst povezave | `tournaments`, `profiles` | manage permission queryji |
| `tournament_registrations` | prijava ekipe, status, seed, review, check-in | `tournaments`, `teams`, `profiles` | `TournamentRegistrationRepository` |
| `tournament_registration_members` | zamrznjen roster snapshot prijave | registrations, profili, team members, manual players | `TournamentRegistrationRepository` |
| `tournament_groups` | skupine in vrstni red | `tournaments` | `TournamentGroupRepository` |
| `tournament_group_teams` | ekipe v skupinah in seed | skupine, ekipe, registrations | `TournamentGroupRepository` |
| `matches` | serija, faza, urnik, status, ekipi, rezultat, zmagovalec | turnirji, skupine, ekipe | `MatchRepository`, bracket repositories |
| `match_slots` | source slota: manual, seed, winner, loser ali bye | matches, registrations | bracket in advancement repositories |
| `match_advancement_audit_logs` | sled samodejnega napredovanja | source/target match | `MatchAdvancementRepository` |
| `match_games` | posamezna uvozena Dota igra/mapa | match, ekipe | `MatchImportRepository`, analytics |
| `match_imports` | lifecycle OpenDota uvoza | match ali match game, requester | `MatchImportRepository` |
| `match_import_events` | zgodovina statusnih prehodov uvoza | match import, actor | `MatchImportRepository` |
| `heroes` | Dota hero reference podatki | match players | `HeroRepository` |
| `match_players` | normalizirana statistika igralca na igro | import, match game, profile, team, hero | import in analytics repositories |
| `notification_outbox` | durable dogodki in in-app read state | prejemnikov profil | notification repositories |
| `audit_log` | DB audit triggerji za pomembne spremembe; raw `previous_row` in `new_row` ostaneta interni | actor profil | `AdminAuditLogRepository`, admin-only DTO projekcija |

### Statusi in enum-i

Glavni enum-i so:

- persisted profile role: `player`, `organizer`, `admin`;
- tournament status: `draft`, `registration`, `published`, `live`, `finished`, `archived`;
- tournament format: `single_elimination`, `groups_playoff`, `round_robin`, `best_of_three_playoff`;
- registration status: `pending`, `approved`, `rejected`, `waitlisted`;
- match status: `scheduled`, `ready`, `live`, `finished`, `cancelled`;
- import status: `queued`, `processing`, `ready`, `error`;
- invitation status: `pending`, `accepted`, `declined`, `cancelled`, `expired`;
- join request status: `pending`, `accepted`, `declined`, `cancelled`;
- delivery status med drugim: `queued`, `processing`, `delivered`, `failed`, `cancelled`.

### Indeksi in DB pravila

Migracije vsebujejo:

- FK-je z eksplicitnimi `on delete` pravili;
- unique kljuce za slug, ime, Steam identiteto, OpenDota hero id, prijavo ekipe in import Dota match id;
- parcialne unique indekse za pending invitations, pending join requests, primary external account in registracijske seede;
- check constraints za casovna okna, roster, series rezultat, match winnerja, dolzine besedil in numeric obsege;
- triggerje za `updated_at`, audit, group assignment, roster starter limit in sinhronizacijo importa;
- indekse za pogoste FK-je, filtre statusa in analytics joins.

PostgreSQL enum zaradi kompatibilnosti stare migracijske zgodovine ohranja legacy labela `visitor` in `captain`, vendar `profiles_role_no_global_captain` po `V28` dovoli samo `player`, `organizer` in `admin`. Java `ProfileRole.VISITOR` je sinteticna anonimna vrednost, ne persisted uporabniska vloga. Legacy `captain` se pri branju mapira v `PLAYER`, novi zapisi pa ga ne morejo shraniti.

Captain oziroma owner je team-level koncept prek `teams.captain_profile_id`. `TeamService.createTeam()` dovoli create samo profilu `PLAYER`; ustvarjalec postane captain in se v isti transakciji doda kot aktiven `team_members` participant z igralno pozicijo `support`. `V30` enako normalizira stare ekipe: reaktivira najnovejsi zgodovinski captain roster zapis ali vstavi novega, ce zapis se ne obstaja. `TeamService`, `TeamRosterService` in `TeamJoinRequestService` organizerju ne dovolijo upravljanja ekip. Ekipo upravlja njen captain ali eksplicitni `ADMIN`, vendar admin nima implicitnega bypassa za transfer ownership.

`TeamRosterCapacityService` centralizira capacity logiko za direct member add, manual player add, invitations in join requests. Steje aktivne `team_members`, rocne igralce in captain fallback samo, ce captain se nima aktivnega roster zapisa. S tem owner steje kot participant in se ne steje dvakrat. Roster write tokovi pred preverjanjem capacityja zaklenejo team vrstico z `FOR UPDATE`, zato dva socasna accepta ne moreta porabiti istega zadnjega mesta.

`GET /api/me/team` zaradi frontend odlocanja poleg starega `captain` vraca se `isTeamOwner`, `currentUserTeamRole`, `canCreateTeam`, `canManageTeam`, `canManageRoster`, `canInvitePlayers`, `canTransferOwnership`, `canViewAnalytics`, `participantsCount`, `capacity`, `slotsFilled`, `slotsRemaining` in `isFull`. Vsak roster member DTO vraca tudi `teamOwner`. Frontend zato ne rabi sklepati team pravic iz globalne profilne vloge.

### RLS, grants in view-i

RLS je vklopljen na javnih poslovnih tabelah in na `private.steam_login_states`. Migracije eksplicitno omejujejo neposredni `anon` in `authenticated` dostop. `service_role` dobi sirse pravice.

Javni view-i uporabljajo `security_invoker = true`, npr.:

- `public_match_import_status`;
- `v_group_standings`;
- `v_player_metrics`;
- `v_team_metrics`;
- `v_hero_metrics`;
- `v_tournament_metrics`.

Raw OpenDota stolpci niso del frontend DTO-jev. Granti za neposredni Supabase dostop so omejeni na izbrane stolpce.

`supabase/post_flyway_hardening.sql` je rocni post-deploy korak za `public.flyway_schema_history`. Ce ni izveden, Flyway metadata tabela ni dodatno utrjena z nameravanimi revoke/grant/RLS pravili.

## 7. Glavni poslovni tokovi

### Ustvarjanje, urejanje, objava in arhiviranje turnirja

1. Organizer frontend poklice `/api/organizer/tournaments`.
2. `TournamentController` sprejme request.
3. `TournamentService.createTournament()` zahteva organizerja ali admina, normalizira nastavitve in preveri datume.
4. `TournamentRepository` vstavi `tournaments` ter owner zapis v `tournament_staff`.
5. Update, publish in archive uporabljajo isti service ter `canManage`.

### Prijava ekipe

1. Captain odda `POST /api/tournaments/{tournamentId}/registrations`.
2. `TournamentRegistrationService.registerTeam()` preveri captaina, odprto okno, duplicate in natancno roster velikost.
3. `TournamentRegistrationRepository` ustvari `tournament_registrations`.
4. Repository zamrzne aktivne `team_members` in `team_manual_players` v `tournament_registration_members`.
5. `NotificationOutboxService` best-effort ustvari in-app outbox zapis za organizatorja.

### Odobritev, zavrnitev in waitlist

1. Organizer poklice review endpoint.
2. `TournamentRegistrationService.reviewRegistration()` preveri `canManage`.
3. Odobritev preveri max teams in seed.
4. Repository posodobi status, reviewerja in cas.
5. Approve/reject best-effort ustvari outbox zapis za captaina.

### Check-in

`TournamentRegistrationService.checkInRegistration()` dovoli check-in captainu ali tournament managerju samo za odobreno prijavo in znotraj konfiguriranega okna.

### Skupine in standings

`TournamentGroupService` ustvari skupino, doda samo odobreno registracijo ter preprecuje podvojeno razporeditev ekipe. Standings bere iz `v_group_standings`, ki uporablja termine Dota 2 iger iz `V16`.

### Bracket

`TournamentBracketService.generateBracket()` podpira single elimination. Uporabi odobrene prijave in seede, pripravi power-of-two bracket, vstavi `matches` in `match_slots`, obdela bye napredovanja ter pri regeneraciji blokira nevarne spremembe ze zagnanih tekem.

### Tekme in napredovanje

`MatchManagementService` podpira schedule, start, cancel, finish in result. `MatchAdvancementService` posodobi odvisne winner/loser slote, blokira spremembe zaklenjenih ali ze uporabljenih downstream slotov ter zapise `match_advancement_audit_logs`.

### OpenDota import in normalizacija

1. Organizer/admin odda Dota match id.
2. `MatchImportService` normalizira id, preveri in-memory rate limit in idempotenco.
3. Repository ustvari ali ponovno uporabi `match_imports`.
4. `OpenDotaClient` klice `/matches/{id}` z omejenim retryjem za timeout, 429 in provider nedosegljivost. Uposteva `Retry-After`.
5. `OpenDotaMatchNormalizationService` iz raw payload-a pripravi `match_games` in `match_players`.
6. Repository shrani raw in normalizirane podatke ter event history.
7. Uspeh asinhrono zahteva analytics refresh. Napaka refresha ne podre uspesnega importa.

Hero sync je locen admin tok prek `POST /api/admin/heroes/sync`. `HeroSyncService` idempotentno upserta po `dota_hero_id`.

### Steam in OpenDota profile bootstrap

1. `SteamAuthService.beginLogin()` ustvari random state, shrani samo hash v `private.steam_login_states` in uporabi rate limiter.
2. Callback porabi state, validira OpenID obliko in preveri assertion pri Steamu.
3. Private SQL helper poveze ali ustvari profil.
4. Backend izda HttpOnly Steam session cookie.
5. `SteamProfileBootstrapService` asinhrono poskusi dopolniti OpenDota profil.

### Notification outbox

Outbox je trajen DB zapis. Trenutni dogodki so submission, approval, rejection in match schedule. `IN_APP` provider je implementiran kot obstoj vrstice. Email in Discord providerja sta placeholderja. Obdelava queued zapisov se trenutno rocno sprozi prek admin endpointa.

## 8. Backend <-> frontend povezave

| Tok | Frontend | API | Backend | Baza |
| --- | --- | --- | --- | --- |
| Javni seznam turnirjev | `/turnirji`, `tournament-data.ts` | `GET /api/tournaments` | `TournamentController` -> `TournamentService` -> `TournamentRepository` | `tournaments`, `profiles` |
| Bogatejsi javni seznam | adapter je na voljo | `GET /api/public/tournaments` | `PublicTournamentController` -> `PublicTournamentService` -> `PublicTournamentRepository` | turnirji, registrations, groups, matches |
| Podrobnost turnirja | `/turnirji/[slug]` | `GET /api/tournaments/{slug}` | `TournamentService.getPublicTournament()` | `tournaments`, organizer profil, stevci |
| Live groups/standings | `PublicTournamentLivePanels`, `tournament-group-data.ts` | `GET /api/public/tournaments/{id}/groups`, `/standings` | `PublicTournamentController` -> `PublicTournamentService` | groups, group teams, `v_group_standings` |
| Live matches | `tournament-match-data.ts` | `GET /api/public/tournaments/{id}/matches` | `PublicTournamentService.listMatches()` | matches, teams, registrations |
| Live bracket | `tournament-bracket-data.ts` | `GET /api/public/tournaments/{id}/bracket` | `PublicTournamentService.getBracket()` | matches, match_slots |
| Prijava ekipe | `TournamentRegistrationPanel`, `tournament-registration-data.ts` | `POST /api/tournaments/{id}/registrations` | `TournamentRegistrationService.registerTeam()` | registrations, snapshot members, outbox |
| Organizer pregled prijav | organizer workspace | `GET /api/organizer/tournaments/{id}/registrations` | `TournamentRegistrationService.listOrganizerRegistrations()` | registrations, teams, profiles |
| Approve/reject/waitlist | organizer workspace | review POST endpointi | `TournamentRegistrationService.reviewRegistration()` | registrations, outbox |
| Organizer groups | `organizer-group-management-panel.tsx` | organizer group endpointi | `TournamentGroupService` | groups, group teams |
| Generiranje bracketa | organizer bracket panel | `POST /api/organizer/tournaments/{id}/bracket/generate` | `TournamentBracketService.generateBracket()` | matches, match_slots |
| Match rezultat | organizer match panel | `PATCH /api/organizer/matches/{id}/result` | `MatchManagementService.submitResult()` -> `MatchAdvancementService` | matches, slots, advancement audit |
| OpenDota import | `match-import-panel.tsx`, `match-import-data.ts` | `/api/match-imports...` | `MatchImportService` | imports, events, games, players |
| Analitika | `/analitika`, `analytics-data.ts` | `/api/public/analytics...` | `AnalyticsRepository` | normalizirane match tabele |
| Ekipe in invitations | `/ekipe`, `team-data.ts` | `/api/teams...`, `/api/me/team...` | team servisi | teams, members, manual players, invitations |
| Audit | `/admin/audit`, `admin-audit-data.ts` | `GET /api/admin/audit-logs` | `AdminAuditLogService` | audit_log |

## 9. API endpoint pregled

### Javni endpointi

| Podrocje | Endpointi |
| --- | --- |
| Health | `GET /api/health`, actuator health/info |
| Steam vstop | `GET /api/auth/steam/login`, `GET /api/auth/steam/callback` |
| Profili | `GET /api/profiles`, `GET /api/profiles/{id}`, `GET /api/profiles/by-nickname/{nickname}` |
| Ekipe | `GET /api/teams`, `GET /api/teams/{id}`, `GET /api/teams/by-slug/{slug}`, javni member/manual-player listi |
| Osnovni turnirji | `GET /api/tournaments`, `GET /api/tournaments/{slug}` |
| Javni turnirski agregati | `GET /api/public/tournaments`, `/{id}`, `/{id}/teams`, `/groups`, `/standings`, `/matches`, `/bracket`, `/metrics` |
| Skupine | javni group teams in standings |
| Tekme | `GET /api/matches/{id}`, `GET /api/tournaments/{id}/matches` |
| Analitika | `GET /api/public/analytics/players`, `/teams`, `/heroes`, `/tournaments`, `/tournaments/{id}` |

### Prijavljen uporabnik

| Podrocje | Endpointi |
| --- | --- |
| Profil | `GET`, `POST`, `PATCH /api/me/profile`, `POST /api/me/avatar`, `POST /api/me/opendota/sync` |
| Steam | `POST /api/auth/steam/link`, `POST /api/auth/steam/logout` |
| Ekipe | create/update/image endpointi, roster write endpointi, invitations, join requests, `/api/me/team`, `POST /api/teams/{teamId}/transfer-ownership` |
| Prijave | `POST /api/tournaments/{id}/registrations`, check-in, team registration pregled |
| Import pregled | `GET /api/match-imports/{id}`, `/by-match/{id}`, `/{id}/events` |
| Obvestila | `GET /api/me/notifications`, mark-read, read-all |
| Dashboard | `GET /api/me/dashboard` |
| Zascitena player analitika | `GET /api/me/analytics`, `GET /api/me/team/analytics` |

### Organizer in admin

| Podrocje | Endpointi |
| --- | --- |
| Turnir | organizer list/detail/create/update/publish/archive |
| Prijave | organizer list, approve, reject, waitlist |
| Skupine | organizer create/list/add/remove/standings |
| Bracket | generate in organizer read |
| Tekme | organizer schedule/start/cancel/finish/result |
| Match import | organizer/admin create in retry |
| Heroji | admin sync |
| Analitika | admin refresh |
| Organizer analitika | `GET /api/organizer/analytics`, `GET /api/organizer/tournaments/{id}/analytics` |
| Audit | admin list |
| Outbox | admin process |

`TournamentGroupController` ohranja tudi starejse ne-`/organizer` write alias poti. Servis se vedno preveri pravice.

### Team ownership transfer

`POST /api/teams/{teamId}/transfer-ownership` sprejme:

```json
{
  "newOwnerProfileId": "uuid"
}
```

Endpoint je `ROLE_PLAYER` protected. Servis dodatno preveri, da je actor trenutni captain, novi owner obstaja, ima persisted vlogo `player` in je aktiven clan iste ekipe. Stari owner ostane clan ekipe, globalne profilne vloge se ne spremenijo. Sprememba `teams.captain_profile_id` sprozi obstojeci `audit_teams` trigger; `DatabaseActorContext` zagotovi actor profil v `audit_log`.

Planned oziroma se neimplementirane My Team funkcije ostajajo: Lock Roster, Leave Team, Disband Team, team-specific Audit Logs in player Profile/Stats pogled.

### Admin audit API

`GET /api/admin/audit-logs` je dostopen samo `ROLE_ADMIN`. Privzeto vrne stran `0`, velikosti `20`, urejeno po `created_at desc, id desc`. Najvecja dovoljena velikost strani je `100`.

Podprti filtri:

- `tableName`: allowlist filter za `teams`, `tournaments`, `tournament_registrations`, `matches`, `match_games`, `match_imports` in `match_players`; sprejme tudi polno obliko `public.<table>`;
- `recordId`: natancen UUID zapisa;
- `actorProfileId`: natancen UUID profila akterja;
- `from` in `to`: ISO datetime meji, obe vkljucujoci;
- `action`: `insert`, `update` ali `delete`;
- `page` in `size`;
- `table` in `actor`: kompatibilna legacy filtra za obstojeci frontend `/admin/audit`.

Primer:

```text
GET /api/admin/audit-logs?tableName=tournament_registrations&actorProfileId=<uuid>&from=2026-05-01T00:00:00Z&to=2026-06-01T00:00:00Z&page=0&size=50
```

DTO vrne actor `profileId`, `nickname`, `displayName` in profilno `role`, ce profil se obstaja. Email in interni `actor_auth_user_id` nista izpostavljena. `previousRow` in `newRow` nista raw trigger payloada: servis vrne samo allowlist dejansko spremenjenih operativnih polj in rekurzivno maskira obcutljive nested kljuce z vrednostjo `[REDACTED]`.

## 10. Testi in kakovost

### Backend testi

Najdenih je 73 backend test source datotek. Suite vsebuje:

- unit teste za servise in helperje;
- MockMvc controller teste;
- security teste;
- PostgreSQL integracijske teste za migracije, RLS, API flow, skupine, bracket, matches, imports, heroje, roster in audit.

Najpomembnejsi regresijski testi za prihodnje spremembe:

- `SecurityConfigTest`;
- `DatabasePolicyIntegrationTest`;
- `MigrationIntegrationTest`;
- `TournamentDatabaseFlowIntegrationTest`;
- `TournamentBracketGenerationIntegrationTest`;
- `MatchManagementIntegrationTest`;
- `MatchImportRepositoryIntegrationTest`;
- `ApiFlowIntegrationTest`;
- service testi za registrations, bracket, advancement, team roster in join requests.

### Frontend testi

Frontend nima `npm test` skripte in avtomatiziranih frontend test datotek ni najdenih. CI izvaja lint, typecheck in build. E2E suite ni najden.

### Izvedeni ukazi

| Ukaz | Rezultat |
| --- | --- |
| `.\mvnw.cmd -B test "-Dspring.profiles.active=test" "-Dtest=*Test,!*IntegrationTest,!SupabaseIntegrationTest"` | uspeh, 409 testov |
| lokalni Docker PostgreSQL 16 + CI bootstrap auth sheme | uspeh, izolirana zacasna baza |
| `.\mvnw.cmd -B "-Dtest=*IntegrationTest" test "-Dspring.profiles.active=integration"` | uspeh, 45 testov, 30 Flyway migracij |
| `.\mvnw.cmd -B package -DskipTests` | uspeh |
| `npm ci` | uspeh; osvezil ignorirani `node_modules` |
| `npm run lint` | uspeh z 5 opozorili v `frontend/src/lib/tournament-data.ts` |
| `npm run typecheck` | uspeh po cistem installu in buildu |
| `npm run build` | uspeh |
| `docker compose config --quiet` | uspeh |
| `npm audit --json` | 2 zmerni tranzitivni ranljivosti |
| `npm audit --omit=dev --json` | 1 zmerna produkcijska tranzitivna ranljivost |

Zacasni PostgreSQL container je bil po testu odstranjen.

### Kakovostne opombe

- `tournament-data.ts` ima 5 lint opozoril za neuporabljene simbole.
- Ni najdenih backend `@Entity` razredov ali `@Scheduled` metod.
- Edini `TODO` zadetek je UI label `Private/TODO` v `organizer-group-management-panel.tsx`.
- `frontend/Dockerfile` uporablja `npm install`, CI pa pravilno `npm ci`.
- Lokalni `node_modules` je bil pred `npm ci` zastarel in ni vseboval deklariranega `gsap`.

## 11. Varnostni model

### Dobro reseno

- JWT verifikacija preverja algoritem, subject, issuer, audience in casovno veljavnost.
- Vloge se nalozijo iz DB profila; klient ne dobi avtorizacije samo iz metadata.
- Steam OpenID state je random, v bazi se shrani hash, state ima TTL in se porabi enkrat.
- Steam cookie je HttpOnly, podpira `Secure`, `SameSite`, domain, path in TTL konfiguracijo.
- Service sloj ponovno preverja lastnistvo in vloge.
- Supabase RLS in column grants omejujejo neposreden browser dostop.
- `V28` omeji team create na `PLAYER`; organizer nima vec RLS bypassa za team update, roster, invitations ali manual players.
- Public analytics filtrira samo `tournaments.is_public = true`.
- Protected analytics loci osebne player/team metrike od organizer agregatov in preveri tournament ownership.
- Public view-i so `security_invoker`.
- Raw OpenDota payload ni v javnih DTO-jih.
- Admin audit DTO ne vraca polnih raw `previous_row` in `new_row`, ampak samo sanitizirano allowlist projekcijo spremenjenih polj; nested tokeni, gesla, secrets, API kljuci, session in webhook vrednosti so redacted.
- Global error handler ne razkriva stack trace-a.
- Service-role storage kljuc ostane v backendu.

### Pomembne produkcijske preverbe

1. `csrf()` je izklopljen, Steam cookie pa lahko avtenticira mutacijske zahteve. Privzeti `SameSite=Lax` pomaga, vendar je treba pred produkcijo narediti eksplicitno CSRF oceno in test, posebej ce se uporabi `SameSite=None`.
2. `ClientIpAddressResolver` brez trust-proxy konfiguracije zaupa `X-Forwarded-For` in `X-Real-IP`. Ce je backend neposredno dosegljiv, lahko klient vpliva na IP rate limiting in zapis Steam state konteksta.
3. `RequestRateLimiter` je in-memory `ConcurrentHashMap`. Pri vec instancah so limiti per-instance in se ob restartu izgubijo.
4. Backend DB povezava bo pogosto uporabljala privilegiranega uporabnika. RLS zato ne sme biti edina backend varnostna meja; service authorization mora ostati obvezna.
5. `V12__create_profiles_for_auth_users.sql` dovoljuje self-selected organizer profil prek signup metadata. To je lahko namerna produktna odlocitev, vendar jo je treba potrditi pred produkcijo.
6. Public profile DTO vraca Steam ID, OpenDota account id, bio in sync case. Manual-player public DTO vraca tudi `note`. Preveriti je treba, ali je to nameravana javna izpostavitev.
7. Image upload preverja velikost in deklariran MIME type, ne pa magic bytes ali vsebinskega skeniranja.
8. `supabase/post_flyway_hardening.sql` je rocni korak. Deployment mora zagotoviti, da ni izpuscen.

### Dependency audit

`npm audit` je 2026-06-02 prijavil:

- produkcijski tranzitivni `ws@8.20.0` prek `@supabase/realtime-js`, zmeren advisory `GHSA-58qx-3vcg-4xpx`;
- razvojni tranzitivni `brace-expansion@5.0.5` prek ESLint/TypeScript drevesa, zmeren advisory `GHSA-jxxr-4gwj-5jf2`.

Za oba je audit prijavil razpolozljiv popravek. Lockfile v tem pregledu ni bil spremenjen.

## 12. Lokalni zagon in konfiguracija

### Potrebna orodja

- Java 21;
- Node.js 20 ali novejsi;
- npm;
- Docker po potrebi;
- Supabase/PostgreSQL baza.

### Porti

| Storitev | Port |
| --- | --- |
| Frontend | `3000` |
| Backend | `8080` |
| Lokalni Supabase API | `54321` |
| Lokalni Supabase PostgreSQL | `54322` |
| Lokalni Supabase Studio | `54323` |

### Backend konfiguracija

`backend/src/main/resources/application.properties` bere `../.env` in lokalni `.env`. Pomembne skupine nastavitev:

- datasource: `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`;
- Flyway: `SPRING_FLYWAY_ENABLED`;
- Supabase auth: JWT secret ali JWKS, issuer, audience;
- storage: Supabase URL, service-role key, bucket;
- Steam: OpenID URL, realm, callback, Web API key, session cookie;
- OpenDota: base URL, opcijski API key, timeout in retry;
- CORS: `CORS_ALLOWED_ORIGIN_PATTERNS`.

Backend:

```powershell
cd C:\DataOpsProjekt\DotaOps\backend
.\mvnw.cmd spring-boot:run
```

### Frontend konfiguracija

Tracked primer je `frontend/.env.example`. Frontend uporablja:

- `NEXT_PUBLIC_API_URL`;
- `NEXT_SERVER_API_URL`;
- `NEXT_PUBLIC_SUPABASE_URL`;
- `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`;
- fallback `NEXT_PUBLIC_SUPABASE_ANON_KEY`.

Frontend:

```powershell
cd C:\DataOpsProjekt\DotaOps\frontend
npm ci
npm run dev
```

### Docker Compose

`docker-compose.yml` gradi backend in frontend. Ne vsebuje PostgreSQL containerja. Pricakuje zunanji Supabase/PostgreSQL. Frontend container uporablja `http://backend:8080/api` za server-side API in `http://localhost:8080/api` za browser API.

### Dokumentacijski drift

Root `README.md` omenja root `.env.example`, vendar ta datoteka v trenutnem repozitoriju ni najdena. Tracked je samo `frontend/.env.example`. Nov razvijalec zato trenutno nima popolnega varnega root env template-a.

## 13. Kaj je ze narejeno

- produkcijsko smiselna feature-oriented backend struktura;
- Supabase JWT in Steam OpenID auth;
- profili, ekipe, rosterji, invitations, manual players in join requests;
- turnirski lifecycle in staff model;
- prijave, review, roster snapshot in check-in;
- skupine in standings;
- single-elimination bracket in napredovanje rezultatov;
- match schedule/result lifecycle;
- OpenDota hero sync;
- OpenDota match import z retryjem, eventi in normalizacijo;
- javna analitika;
- role-based dashboard z realnimi stevci in capability DTO-ji;
- protected player/team in organizer analytics endpointi;
- audit triggerji in admin audit API/UI;
- durable notification outbox backend;
- javni turnirski prikazi in organizer UI;
- RLS, grants, DB constraints in integracijski testi;
- CI za backend in frontend;
- Docker build konfiguracija.

## 14. Kaj se manjka ali je nedokoncano

Trenutno iz repozitorija ni razvidno, da bi bilo dokoncanje naslednjih delov implementirano:

- frontend join-request UI in adapter;
- frontend in-app notification UI in adapter;
- frontend klic backend Steam logout endpointa;
- frontend rocni OpenDota profile sync;
- dejanski email in Discord transport;
- samodejni scheduler ali worker za outbox;
- trajen queue za asinhroni profile bootstrap in analytics refresh;
- frontend unit/component/E2E testi;
- popoln root `.env.example`;
- frontend povezava na `GET /api/me/dashboard` in protected analytics endpoint-e;
- varen captain transfer tok; trenutni model ne doloca, ali nekdanji captain po prenosu ostane roster clan;
- match-history seznam za protected player/team analytics; DTO je stabilen, vendar trenutno vrne prazen seznam;
- referee/analyst write tokovi; `V14` jih izrecno odlozi;
- organizer export, close-registration in bulk akcije, ki jih UI oznaci kot nedostopne.

## 15. Potencialna tveganja

### Visja prioriteta

1. Outbox nima samodejnega workerja. `POST /api/admin/notifications/outbox/process` je rocni sprozilec.
2. Outbox `processing` zapis nima vidne recovery logike za crash med obdelavo. Tak zapis lahko ostane obvisel.
3. `NotificationOutboxWriter` uporablja `REQUIRES_NEW`. To izolira napako, vendar ni klasicna atomarna transactional-outbox vez z outer poslovno transakcijo; ob kasnejsem rollbacku outer transakcije lahko ostane obvestilo.
4. Email in Discord providerja sta no-op placeholderja. Ce bi se zaceli ustvarjati taki zapisi, bi jih trenutni processor oznacil kot delivered brez dejanske dostave.
5. CSRF model za cookie-auth mutacije zahteva produkcijsko odlocitev in test.
6. In-memory rate limiter ni primeren kot edina zascita pri multi-instance deploymentu.
7. Trust-proxy meja za forwarded IP headere ni eksplicitna.

### Srednja prioriteta

1. Asinhroni executorji za profile bootstrap in analytics refresh niso trajni. Delo se lahko izgubi ob restartu.
2. Frontend mock fallback v `fetchApi()` lahko skrije backend izpad ali contract regresijo.
3. Frontend ima starejse in novejse prekrivajoce adapterje, kar povecuje moznost nekonsistentnega razvoja.
4. Root onboarding dokumentacija omenja manjkajoci env template.
5. Public DTO-ji in RLS izpostavljajo profilne podatke ter manual-player note; pregledati je treba zasebnost.
6. Storage upload zaupa MIME headerju.
7. `npm audit` ima eno produkcijsko in eno razvojno zmerno tranzitivno ranljivost.
8. Pravilo ene aktivne ekipe je utrjeno v servisih za create, invite accept, direct member add in join request. `V30` normalizira captain roster zapise, capacity write tokovi pa uporabljajo team row lock. Se vedno ni atomarnega cross-table DB constrainta za eno aktivno ekipo cez `teams.captain_profile_id` in `team_members`.

### Nizja prioriteta

1. JPA odvisnost in `ddl-auto=validate` sta prisotna, aplikacija pa uporablja JDBC brez entitet. To je zavajajoce za nove razvijalce.
2. Prazni backend mapi `match/` in `registration/` lahko ustvarita napacno predstavo o lastnistvu kode.
3. `frontend/Dockerfile` naj zaradi ponovljivosti uporablja `npm ci`.
4. Lint ima 5 opozoril v `tournament-data.ts`.

## 16. Priporocila za nadaljnji razvoj

1. Pred naslednjo vecjo funkcionalnostjo zapisati produkcijski security checklist: CSRF za Steam cookie, `Secure=true`, CORS origin patterns, issuer/JWKS, trust proxy in servisni DB uporabnik.
2. Outbox procesiranje prestaviti v samodejni worker ali scheduler. Dodati lease/recovery za zastarele `processing` zapise in odlociti, ali mora enqueue uporabljati isto transakcijo kot poslovni zapis.
3. In-memory rate limiter zamenjati ali dopolniti z Redisom, gateway limiterjem ali drugo deljeno shrambo.
4. Za profile bootstrap in analytics refresh dolociti trajnostne zahteve. Ce izguba joba ni sprejemljiva, uporabiti durable job mehanizem.
5. Povezati frontend z join requests, notifications, Steam logout in OpenDota sync endpointi.
6. Zmanjsati uporabo mock fallbacka na eksplicitni development/demo mode, da produkcijski izpad ni prikrit.
7. Dodati root `.env.example` brez skrivnosti in uskladiti README-je.
8. Odpraviti `npm audit` zadetke z nadzorovanim dependency update-om in ponovnim CI zagonom.
9. Dodati frontend component teste za auth gating, registration in organizer tokove ter vsaj en E2E happy path.
10. Pregledati public privacy contract za `ProfileResponse` in `TeamManualPlayerResponse`.
11. Uskladiti JDBC usmeritev: odstraniti zavajajoce JPA nastavitve ali dokumentirati, zakaj ostajajo.
12. V Docker frontend buildu uporabiti `npm ci`.
13. Dolociti, ali transfer ownership potrebuje dodatna pravila ob aktivnih turnirskih prijavah; trenutni model ohrani snapshot registracije in spremeni samo trenutno ekipo.
14. Po spremljanju produkcijskih podatkov odlociti, ali pravilo ene aktivne ekipe potrebuje DB trigger ali drug atomaren mehanizem cez `teams.captain_profile_id` in `team_members`.

## 17. Hitri vodic za novega razvijalca

1. Preberi root `README.md`, ta dokument in `supabase/README.md`.
2. Ustvari varen root `.env` ročno, ker root `.env.example` trenutno manjka. Ne uporabljaj produkcijskih skrivnosti za testiranje.
3. Ustvari `frontend/.env.local` iz `frontend/.env.example`.
4. Poskrbi, da je PostgreSQL/Supabase dosegljiv. Flyway je lastnik sheme.
5. Zazeni backend:

```powershell
cd C:\DataOpsProjekt\DotaOps\backend
.\mvnw.cmd spring-boot:run
```

6. Zazeni frontend:

```powershell
cd C:\DataOpsProjekt\DotaOps\frontend
npm ci
npm run dev
```

7. Preveri `http://localhost:8080/api/health` in `http://localhost:3000`.
8. Pred backend spremembo zazeni unit/MockMvc suite in PostgreSQL integracijske teste na lokalni throwaway bazi.
9. Pred frontend spremembo zazeni:

```powershell
npm run lint
npm run typecheck
npm run build
```

10. Po deployu izvedi `supabase/post_flyway_hardening.sql`.
11. Ne commitaj `.env`, `.env.local`, DB gesel, JWT secretov, Steam keyev ali Supabase service-role kljuca.

## Pregledane referencne datoteke

Pregled je vkljuceval repository-wide inventuro in poglobljen pregled glavnih izvedbenih poti. Najpomembnejse reference:

- `README.md`, `backend/README.md`, `frontend/README.md`, `supabase/README.md`;
- `docker-compose.yml`, oba `Dockerfile`, oba CI workflowa;
- `backend/pom.xml`, Spring properties in test properties;
- vseh 30 Flyway migracij in `supabase/post_flyway_hardening.sql`;
- `SecurityConfig`, JWT filter/verifier, Steam auth, rate limiter, DB actor context;
- controllerje, servise in repository razrede za profile, ekipe, turnirje, tekme, OpenDota, analytics, audit in notifications;
- frontend App Router strani, `AppShell`, `route-access.ts`, `api.ts`, auth in feature adapterje;
- backend test inventuro, PostgreSQL integracijske teste in frontend npm skripte.
