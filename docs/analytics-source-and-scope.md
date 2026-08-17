# DotaOps 1.0 Analytics Source and Scope Contract

Ta dokument je edini canonical vir za analytics source, linkage, scope, lifecycle, časovno semantiko in data-quality pravila v DotaOps 1.0. Pripravljen je bil nad stanjem `development` na commitu `44b6f53eb801f43e3888ebc298629d3a7dcc99c4`.

Dokument opisuje ciljno pogodbo. Ne trdi, da jo trenutna Java, frontend ali SQL implementacija že v celoti izpolnjuje. Odstopanja so označena kot **KNOWN GAP** in povezana z roadmap nalogami.

## 1. Namen in obseg

Pogodba določa:

- kateri normalizirani zapisi so veljaven analytics vir;
- razliko med standalone in application-linked OpenDota igro;
- identity, object, visibility, lifecycle in time scope posameznega analytics sklopa;
- pravila za Personal, Hero Mastery, Compare, Team, Tournament, Organizer in Public Analytics;
- pomen filtrov in neveljavnih kombinacij;
- ravnanje z nepopolnimi povezavami, neznanim izidom ter praznimi ali nezadostnimi rezultati;
- pogoje za prihodnji cache, materialized view ali drug read model.

Razmejitev avtoritete:

- [security-and-role-model.md](security-and-role-model.md) je canonical za authentication, authorization, vloge, privacy in trust boundary. Ob konfliktu ima security/privacy pogodba prednost.
- [release-baseline.md](release-baseline.md) je canonical za Git baseline, branch strategijo in deployment traceability.
- [backend-analytics-api.md](backend-analytics-api.md) je inventar trenutnih endpointov in DTO-jev, ne drugi semantic source of truth. Ob konfliktu velja ta dokument.
- [project-current-state-overview.md](project-current-state-overview.md) je časovni audit trenutne implementacije, ne normativna pogodba.

Besede **MORA**, **NE SME**, **DOVOLJENO** in **IZKLJUČENO** so normativne. Implementacija lahko rezultat dodatno omeji zaradi security/privacy pravila, ne sme pa razširiti tukaj dovoljenega analytics dataseta.

## 2. Source of truth

Canonical source za DotaOps 1.0 so:

```text
normalizirane aplikacijske tabele
+
live SQL nad temi tabelami
```

Primarno podatkovno jedro je:

```text
matches
→ match_games
→ match_players
```

z dimenzijami oziroma object linkage na:

```text
profiles
teams
tournaments
heroes
```

`match_imports` je provenance/import-lifecycle vir. Ne sme sam postati performance fact in requester importa ne ustvari team ali tournament scope-a.

Raw provider podatki, na primer `match_games.raw_response`, `match_imports.raw_response`, `match_imports.normalized_payload` in `match_players.raw_player`, niso canonical analytics source. Dovoljeni so samo za:

- provenance in audit normalizacije;
- troubleshooting z namenskim admin/support scope-om;
- ponovljivo ponovno normalizacijo;
- data-quality diagnostiko.

Frontend analytics nikoli ne bere ali razlaga raw OpenDota JSON neposredno. Raw oziroma diagnostični payload ni public analytics.

Trenutni Spring Boot analytics endpointi uporabljajo `AnalyticsRepository` in live `JdbcTemplate` SQL nad normaliziranimi tabelami. `AnalyticsRefreshService` tudi izrecno obravnava materialized-view refresh kot nepotreben za live SQL endpointe. Obstoj `mv_*`, `v_*` ali refresh funkcije zato ne spremeni source-of-truth odločitve.

## 3. Normalized analytics model

### 3.1 Grain

Canonical game grain je ena normalizirana vrstica `match_games`. Canonical participant grain je ena vrstica `match_players` za en unikaten `player_slot` v tej igri.

Nova application linkage pot je:

```text
match_players.match_game_id
→ match_games.id
→ match_games.match_id
→ matches.id
→ matches.tournament_id
→ tournaments.id
```

`match_players.match_id` je legacy neposredna povezava. Med prehodom jo je mogoče brati samo kot kompatibilnostni fallback, če vodi v enak, nedvoumen in lifecycle-veljaven application match. Ne redefinira ciljne pogodbe: nova igra je application-linked šele prek `match_game → match → tournament`.

### 3.2 Stabilna identiteta in deduplikacija

- Provider game identity je `match_games.dota_match_id`; v shemi je unikaten.
- Participant identity znotraj igre uporablja `(match_game_id, player_slot)`; en igralec ne sme biti dvakrat štet v isti igri.
- Pri združitvi standalone in application-linked vira se najprej deduplicira po providerju in `dota_match_id`, nato po internem `match_game_id`.
- Če ista OpenDota igra obstaja kot standalone in pozneje dobi application linkage, application-linked zapis prevlada; igra se ne šteje dvakrat.
- Nasprotujoče si `match_players.match_id` in `match_games.match_id` povezave pomenijo data-quality napako, ne dva veljavna scope-a.

### 3.3 Identity mapping

- `profiles.id` je internal DotaOps profile ID. OpenDota mapping uporablja natančen `profiles.opendota_account_id`; display name, nickname ali približno ujemanje nista dovoljena identity ključa.
- `heroes.id` je internal DotaOps hero ID. `heroes.dota_hero_id` in `match_players.dota_hero_id` hranita external Dota/OpenDota identiteto.
- `teams.id` je internal application team ID. Provider team ID sam po sebi ni DotaOps `team_id`.
- `tournaments.id` obstaja samo v application domeni. Import requester ali poljubna oznaka OpenDota tekme ne ustvarita tournament linkage-a.

### 3.4 Canonical played-at čas

Primarni čas igre je `match_games.started_at`. Za preverjen legacy application zapis je lahko fallback dejanski `matches.started_at`, če predstavlja začetek te igre oziroma je to izrecno dokumentirano. `created_at`, import request/completion čas in scheduled čas niso gameplay čas in se ne smejo tiho uporabljati za trende ali time-range pripadnost.

Če canonical played-at čas manjka, je zapis lahko v overall self diagnostiki označen kot partial, ne sme pa vstopiti v time-filtered trend, recent-form ali benchmark cohort.

## 4. Standalone vs application-linked games

### 4.1 Application-linked game

Application-linked igra ima veljavno, nedvoumno pot:

```text
match_player
→ match_game
→ match
→ tournament
```

ter po potrebi internal `profile_id`, `team_id` in `hero_id`. Uporabi se lahko v Personal, Hero Mastery, Team, Tournament, Organizer, Public in application-scoped Compare samo, če izpolnjuje dodatna lifecycle, visibility in data-quality pravila tega sklopa.

### 4.2 Standalone profile-linked OpenDota game

Standalone igra je normalizirana igra z:

- `match_game_id` in stabilnim `dota_match_id`;
- `match_games.match_id = null`;
- natančno povezanim `match_players.profile_id` za konkretnega igralca;
- po možnosti povezanim internal `hero_id`;
- brez application match/tournament povezave.

Standalone igra je dovoljena samo v dokumentiranem self Personal Analytics kontekstu. Ne sme vplivati na Team, Tournament, Organizer, Public ali application-scoped Compare. Dejstvo, da je igro uvozil organizer, ji ne dodeli turnirja.

### 4.3 Pregledna scope matrika

| Analytics area | Source | Standalone allowed | Required linkage | Visibility | Lifecycle |
| --- | --- | --- | --- | --- | --- |
| Personal Analytics | normalizirani `match_games` + lastni `match_players` | DA, samo self in brez application-only filtra | natančen `profile_id`; team/tournament linkage le, ko je tak filter zahtevan | self | analytics-ready igra, znan izid za outcome metrike |
| Hero Mastery | Personal source + internal `heroes` | DA, samo self | `profile_id` + `hero_id`; application linkage za team/tournament filter | self | analytics-ready igra; najmanjši sample za interpretacijo |
| Player Compare | isti simetrični application/public dataset za A in B | NE za DotaOps 1.0 comparison dataset | oba `profile_id` + eksplicitno dovoljen shared/application/public scope | authenticated po security object scope-u | samo primerljive, outcome-eligible igre |
| Team Compare | application-linked normalizirane igre | NE | obe ekipi + game/match linkage; tournament linkage, ko je scope turnirski | public-safe, član dovoljenega scope-a, manager ali admin | obe strani uporabljata isti lifecycle in časovni scope |
| Team Analytics | application-linked normalizirane igre | NE | `team_id` + `match_game → match`; tournament za tournament team scope | active member/captain, manager v managed tournamentu ali public aggregate | outcome-eligible game; series metrika zahteva veljaven match rezultat |
| Tournament Analytics | application-linked normalizirane igre | NE | `match_game → match → tournament` | public za public-eligible turnir ali manager/admin | public lifecycle + eligible completed game/result |
| Organizer Analytics | application-linked performance in ločen operational coverage vir | NE | managed `tournament_id`; import mora biti povezan z njegovim match/game objektom | organizer manage capability ali admin | performance in operational lifecycle se ne mešata |
| Public Player Analytics | public-eligible application agregat | NE | `profile_id` + application tournament linkage | public, minimal disclosure | public tournament lifecycle + outcome-eligible igra |
| Public Team Analytics | public-eligible application agregat | NE | internal `team_id` + application linkage | public, minimal disclosure | public tournament lifecycle + outcome-eligible igra |
| Public Hero Analytics | public-eligible application agregat | NE | internal `hero_id` + application linkage | public, agregat | public tournament lifecycle + outcome-eligible igra |
| Public Tournament Analytics | public-eligible application agregat | NE | `match_game → match → tournament` | public, minimal disclosure | `is_public` + dovoljen tournament status + eligible result facts |

## 5. Personal Analytics scope

Personal identity je vedno actor-bound. Backend iz authenticated actorja izpelje canonical `profileId`. Query `profileId` je lahko odsoten ali enak actorju; drug ID ne sme izbrati drugega igralca.

| Personal sklop | Standalone | Application-linked | Dodatni pogoj |
| --- | --- | --- | --- |
| Overall performance | DA | DA | exact profile linkage, stabilna game identity, znan izid |
| Recent form | DA | DA | canonical played-at čas in znan izid |
| Hero performance | DA | DA | internal `hero_id`; unlinked hero je partial, ne drug hero |
| Hero Mastery | DA | DA | internal `hero_id`, isti scope za hero sample in baseline |
| Personal trends | DA | DA | canonical played-at čas; brez import/created-at fallbacka |
| Personal insights | DA | DA | samo iz podatkov, ki jih insight dejansko potrebuje; manjkajoč application context mora biti označen |
| Personal match history | DA | DA | self-only, minimalni varni DTO, brez raw payloadov |
| Team/tournament splits | NE | DA | zanesljiv internal team/tournament linkage |
| Application cohort benchmark | NE | DA | eksplicitno definiran cohort in application linkage |

Če je prisoten `teamId` ali `tournamentId`, standalone igre po definiciji ne ustrezajo filtru in so izključene. `heroFilterId` standalone igre lahko filtrira samo, če je internal hero linkage zanesljiv.

Trenutni Java Personal SQL inner-joina `matches` in `tournaments`, zato standalone igre trenutno izloči iz vseh Personal in Hero Mastery rezultatov. To je **KNOWN GAP**, ne sprememba canonical politike.

## 6. Hero Mastery scope

Hero Mastery je self/private analitika enega internal heroja:

- actor določi `profileId`;
- path oziroma selection state določi `selectedHeroId`;
- dataset vsebuje samo normalizirane vrstice tega profila in tega internal heroja;
- standalone in application-linked igre so dovoljene po Personal pravilih;
- `teamId`, `tournamentId` in časovni filter zožijo dataset konjunktivno; team/tournament filter avtomatsko izključi standalone igre;
- hero overall-baseline primerjava uporablja isti časovni, team, tournament, visibility in linkage scope ter se razlikuje samo v hero dimenziji;
- raw metrike se ne spreminjajo zaradi context weighting interpretacije.

Za DotaOps 1.0 je trenutni Hero Mastery prag treh eligible hero iger sprejemljiv minimum za verdict. Pri 1–2 igrah se lahko pokažejo raw statistike in sample size, verdict pa mora biti `INSUFFICIENT_DATA`. Prag je del versionirane metric pogodbe in ga UI ne sme samovoljno spreminjati.

Če `match_players.dota_hero_id` obstaja, internal `hero_id` pa manjka, provider identiteta ostane za repair/provenance. Vrstica ne sme biti pripisana drugemu heroju in ne vstopi v Hero Mastery, hero ranking ali hero benchmark.

## 7. Player Compare scope

Player Compare mora eksplicitno določiti:

- `profileAId` in `profileBId`, ki morata biti različna;
- en skupen, simetričen dataset scope;
- visibility/access scope;
- opcijski team, tournament, time in hero filter;
- sample size za oba igralca in primerljivo presečišče.

Pravila:

1. Samo trenutno skupno aktivno članstvo ne dovoljuje celotne zasebne zgodovine Player B.
2. Protected primerjava uporablja eksplicitno dovoljen shared application scope, na primer konkretno ekipo ali managed tournament, oziroma privacy-approved public scope.
3. Standalone podatki drugega igralca niso dovoljeni brez ločenega opt-in/consent produkta. DotaOps 1.0 takega modela ne predpostavlja.
4. Ker morata imeti A in B isto dataset pravilo, self standalone igre prav tako ne vstopijo v primerjavo z drugim igralcem.
5. Public-safe primerjava uporablja samo application-linked public-eligible agregate in ne razkrije protected match historyja.
6. Organizer primerjava zahteva konkreten managed `tournamentId`; admin uporabi namenski auditiran scope.
7. Vsaka metrika vrne sample size. Trenutna opozorilna minimuma petih iger na igralca in treh iger na igralca za shared hero sta začetna interpretacijska praga, ne dovoljenje za primerjavo neprimerljivih cohortov.

Trenutna možnost protected all-history primerjave zaradi teammate relacije je **KNOWN GAP → task 07 / #146 in task 28 / #167**.

## 8. Team Compare scope

Team Compare je application-scoped in zahteva dva različna internal team ID-ja. Za obe ekipi veljajo isti tournament, time, hero, lifecycle in visibility filtri.

- Zasebna primerjava je dovoljena samo z object scope-om, ki dovoljuje podatke obeh ekip, na primer organizerju znotraj managed tournamenta ali adminu.
- Aktivni član ene ekipe ne dobi avtomatsko private zgodovine druge ekipe; brez širšega dovoljenja je primerjava public-safe.
- Standalone igre in provider team identiteta niso dovoljene.
- Team A in Team B morata ostati dve ločeni seriji v vseh metrikah, tudi pri hero podatkih.
- Sample size, eligible game count in izključitve se poročajo ločeno za A in B.
- Skupen A+B hero agregat ni Team Compare rezultat.

Trenutni hero query združi obe ekipi v isti hero aggregate, ker filtrira `team_id IN (A,B)`, ne grupira pa po teamu. To je **KNOWN GAP → task 28 / #167**.

## 9. Team Analytics scope

Team Analytics zahteva internal application team mapping:

```text
match_player.team_id
+
match_player → match_game → match
```

OpenDota provider team ID ali stran Radiant/Dire brez DotaOps `team_id` ne zadostujeta.

- Protected Team Analytics je dovoljen aktivnemu članu konkretne ekipe; Captain je capability te ekipe, ne globalna pravica do drugih ekip.
- Organizer vidi team podatke samo znotraj konkretnega managed tournamenta.
- Public Team Analytics vsebuje samo minimalne agregate public-eligible tekem.
- `profileId` je lahko roster drilldown samo, če je profil član oziroma registriran participant znotraj dovoljenega team/tournament scope-a.
- `tournamentId` zoži Team Analytics na application tekme te ekipe v tem turnirju.
- Standalone igra brez DotaOps team linkage-a lahko ostane Personal fact, nikoli Team fact.

Game-level team metrika uporablja eligible completed game outcome. Match/series-level metrika dodatno zahteva veljaven zaključen parent match rezultat in ga ne sme zamenjati z game win/loss štetjem.

## 10. Tournament Analytics scope

Tournament Analytics vedno zahteva:

```text
match_game
→ match
→ tournament
```

Standalone Dota match ne dobi tournament scope-a samo zato, ker ga je uvozil organizer ali ker se njegov datum ujema z dogodkom.

- Public Tournament Analytics zahteva public tournament eligibility iz poglavja 16.
- Protected Tournament Analytics zahteva manage capability konkretnega turnirja.
- `tournamentId` v pathu je avtoritativen; nasprotujoč query parameter je validation error.
- Team, profile, hero in časovni filtri lahko samo zožijo isti turnirski dataset.
- Registration/published turnir brez eligible rezultatov je veljaven public objekt z analytics stanjem `EMPTY`, ne private objekt in ne lažen zero-performance rezultat.
- Scheduled/live/cancelled matchi so lahko del operativnega tournament schedule-a, vendar niso avtomatsko performance facts.

## 11. Organizer Analytics scope

Organizer Analytics ne pomeni globalnega organizer dostopa. Actor vidi samo turnirje, za katere ima dokazano manage capability.

Ločiti je treba dve vrsti podatkov:

1. **Performance analytics:** application-linked, outcome-eligible igre z enakimi metric pravili kot Tournament Analytics.
2. **Operational analytics:** import coverage, missing results, queued/failed importi in data-quality opozorila za managed application objekte.

Operational statusi se ne smejo mešati v performance denominatorje in ne postanejo public analytics. Standalone import, ki ga je zahteval organizer, ni del njegovega turnirja brez eksplicitnega application match/game linkage-a.

`tournamentId` mora biti zahtevan za tournament drilldown. Globalni organizer overview je agregat samo čez zbirko managed turnirjev; vsak filter mora dokumentirati, na katere count-e dejansko vpliva. Filter, ki ga endpoint ne podpira, se zavrne ali ga pogodba sploh ne sprejme, ne sme se tiho ignorirati.

## 12. Public Analytics scope

Canonical enačba je:

```text
public analytics
=
application-linked
+
public tournament lifecycle
+
analytics-eligible result/data-quality
+
minimal disclosure
```

Ni dovolj:

```text
tournaments.is_public = true
```

Public Analytics nikoli ne razkrije:

- raw OpenDota response ali raw player payload;
- normalized debug payload ali import event payload;
- import requesterja, napake ali interno operational diagnostiko;
- private profile podatkov ali external-account podrobnosti;
- private team/tournament podatkov;
- protected player match historyja;
- standalone osebne zgodovine.

Public Player Analytics je public-eligible aggregate/leaderboard, ne javni osebni history. Public Team, Hero in Tournament Analytics uporabljajo samo application-linked podatke istega eligible tournament parenta. Child fact ne more biti bolj javen od tournament parenta.

Trenutni Java SQL in V24 `v_*` viewi preverjajo predvsem `t.is_public`, ne pa canonical statusa. To je **KNOWN GAP → task 06 / #145, task 07 / #146 in task 32 / #171**.

## 13. Canonical AnalyticsScope

Logični pogodbeni model je:

```text
AnalyticsScope

profileId
teamId
tournamentId
heroFilterId
fromInclusive
toExclusive
visibility
limit/cursor
```

`selectedHeroId` ni del dataset scope-a. Je UI/path selection iz poglavja 14.

| Polje | Pomen | Optionalnost in omejitve |
| --- | --- | --- |
| `profileId` | internal DotaOps profil | Self endpoint ga izpelje iz actorja; optional filter pri public listi; eksplicitni A/B ID pri Compare |
| `teamId` | internal DotaOps ekipa | obvezen za Team detail; optional Personal/application filter; ne sprejme provider team ID-ja |
| `tournamentId` | internal DotaOps turnir | obvezen za Tournament/Organizer drilldown; optional konjunktivni filter drugje |
| `heroFilterId` | internal hero dataset filter | optional samo pri filter-aware sklopih; unsupported endpoint ga zavrne |
| `fromInclusive` | spodnja meja gameplay časa | optional; manjka pomeni brez spodnje meje |
| `toExclusive` | zgornja meja gameplay časa | optional; manjka pomeni brez zgornje meje |
| `visibility` | `SELF`, `TEAM_MEMBER`, `MANAGED_TOURNAMENT`, `PUBLIC` ali namenski `ADMIN` | vedno server-derived; klient je ne sme določiti |
| `limit/cursor` | omejitev ali nadaljevanje konkretne zbirke rezultatov | optional in endpoint-specific; nikoli ne spreminja agregacijskega dataseta brez eksplicitne metric definicije |

Vsi podani filtri se kombinirajo konjunktivno in lahko samo zožijo actorjev osnovni visibility/object scope.

Neveljavne kombinacije vključujejo:

- Personal `profileId`, ki ni actorjev profil;
- Team Analytics brez dovoljenega `teamId`;
- Organizer/Tournament detail brez path `tournamentId` ali z nasprotujočim query ID-jem;
- `teamId` + `tournamentId`, če ekipa nima veljavne application povezave s turnirjem;
- `fromInclusive >= toExclusive`;
- isti A/B profil ali ista A/B ekipa pri Compare;
- filter na dimenzijo, ki je endpoint ne podpira;
- client-supplied visibility ali object-ownership trditev.

Endpoint mora neveljavno kombinacijo vrniti kot `400` oziroma nedovoljen object scope kot `403`. Tiho ignoriranje parametra ni canonical vedenje.

## 14. selectedHeroId vs heroFilterId

Koncepta sta ločena in se ne smeta ponovno združiti v dvoumni `heroId`:

| Parameter | Pomen | Vpliv |
| --- | --- | --- |
| `selectedHeroId` | kateri hero je odprt v Hero Mastery UI | UI selection, deep link in hero ID v Mastery endpoint pathu |
| `heroFilterId` | na katerega heroja je omejen filter-aware analytics dataset | agregati, progress, trendi, Compare in drugi sklopi, ki ta filter izrecno sprejmejo |

Izbira heroja za Mastery ne filtrira avtomatsko celotnega dashboarda. Globalni `heroFilterId` prav tako ne odpre avtomatsko Mastery prikaza. Hero Mastery path že določa svoj hero dataset, zato endpoint ne sprejme drugačnega `heroFilterId`; enaka vrednost je redundantna in se izpusti, različna pa je neveljavna kombinacija.

Trenutna kompatibilnost je:

```text
legacy/current URL heroId = selected Hero Mastery hero
current Advanced Filters heroId = in-memory aggregate filter, ki ni shranjen v URL

target URL/state selectedHeroId = UI/Mastery selection
target URL/state heroFilterId = dataset restriction
```

Migracija URL-ja, back/forward obnašanja in endpoint parametrov ni del te naloge. To je **KNOWN GAP → task 22 / #161**.

## 15. Time-range semantics

Canonical interval je polodprt:

```text
[fromInclusive, toExclusive)
```

API vrednosti so ISO-8601 UTC instanti; priporočena serializacija je z `Z`. Tudi deljiv URL hrani URL-encoded UTC vrednosti v query parametrih `from` in `to`, ne browser-local časa brez offseta. Frontend lahko uporabniku ponudi lokalni vnos, vendar ga mora pred zapisom v URL in API request deterministično pretvoriti v UTC; ob ponovnem odpiranju ga lahko pretvori nazaj za prikaz.

| Vhod | Canonical pomen |
| --- | --- |
| brez `from` | odprt interval brez spodnje meje |
| brez `to` | odprt interval brez zgornje meje |
| brez obeh | brez časovne omejitve |
| `from < to` | veljaven polodprt interval |
| `from > to` | `400` validation error |
| `from == to` | `400` validation error, ne tih `EMPTY` rezultat |
| vrednost brez veljavnega časa/offseta | `400` validation error |

Filter se uporablja nad canonical gameplay časom `playedAt` iz poglavja 3.4. Zapis brez `playedAt` je iz time-filtered datasetov izključen in mora biti v coverage diagnostiki označen kot manjkajoč čas; ne sme se tiho razvrstiti po `created_at`, import času ali scheduled času.

Trenutni SQL že uporablja `>= from` in `< to`, vendar backend dovoljuje enaki meji, frontend pa v URL hrani lokalni čas brez cone ter ne preveri vrstnega reda. Trenutni SQL uporablja tudi širši timestamp fallback. To je **KNOWN GAP → task 22 / #161**.

## 16. Lifecycle and visibility semantics

Lifecycle in visibility sta ločeni osi. Lifecycle pove, ali zapis lahko predstavlja analytics fact; visibility pove, ali ga actor v izbranem object scope-u sme videti. `visibility` vedno izpelje backend po [security-and-role-model.md](security-and-role-model.md); query parameter je ne sme razširiti.

### 16.1 Analytics-ready game

Za core performance metrike je igra analytics-ready, ko ima:

- stabilno, deduplicirano game identity;
- uspešno zaključeno normalizacijo oziroma enakovredno preverjen legacy zapis;
- vse povezave, ki jih konkretni analytics sklop zahteva;
- znan in notranje konsistenten izid za uporabljeno grain raven;
- canonical gameplay čas, kadar metrika uporablja časovno okno.

`match_imports.status = ready` oziroma `match_games.import_status = ready` potrdi zaključek import/normalization toka, ni pa sam po sebi dokaz pravilnega object linkage-a, popolnega rosterja ali znanega izida. Enako `match_games.status = finished` brez konsistentnega outcome-a ni dovolj.

### 16.2 Personal in application lifecycle

- Self Personal lahko uporabi analytics-ready standalone ali application-linked igro.
- Application-scoped metrika dodatno zahteva veljaven `match_game → match` in po potrebi tournament/team linkage.
- Zaključena posamezna igra z znanim izidom se lahko šteje tudi, ko je več-igralna serija oziroma parent match še `live`.
- Match/series win, score ali series-level zaključek zahteva terminalen parent match in konsistenten winner/result.
- `scheduled`, nedokončana `live`, `cancelled` ali outcome-neznana igra ni win/loss performance fact. Lahko je operational coverage zapis v dovoljenem organizer scope-u.
- Legacy neposreden `match_player → match` fallback je outcome-eligible samo, če je parent match zaključen, rezultat znan in povezava nedvoumna.

### 16.3 Public lifecycle

Public tournament parent je viden samo, ko velja:

```text
tournaments.is_public = true
AND tournaments.status IN (registration, published, live, finished)
```

To je ista lifecycle pogodba, ki jo uporablja public tournament repository. `draft` in `archived` turnirja nista public analytics parenta. Znotraj sicer javnega turnirja se v performance analytics vključijo samo analytics-ready rezultati; scheduled/live/cancelled zapisi so lahko javni schedule, niso pa zaradi tega performance facts.

Trenutni analytics SQL preverja predvsem `t.is_public = true` in nima enotnega import/game/match/outcome predikata. To je **KNOWN GAP → task 06 / #145, task 07 / #146 ter task 32 / #171**.

## 17. Data-quality rules

### 17.1 Minimalna upravičenost

Vsaka uporabljena participant vrstica mora imeti stabilen `match_game_id`, veljaven `player_slot` in povezave, ki jih zahteva metrika. Za outcome metrike mora biti `is_winner` znan in konsistenten z game oziroma team outcome-om.

Canonical core invariant je:

```text
gamesPlayed = wins + losses
```

Outcome-neznana vrstica se zato ne šteje v `gamesPlayed`, `wins`, `losses` ali win-rate denominator. Lahko se ločeno poroča kot `excludedUnknownOutcomeCount` ali drug ekspliciten coverage podatek.

### 17.2 Linkage in completeness

| Težava | Dovoljeno ravnanje |
| --- | --- |
| manjka `profile_id` | izključeno iz Personal, Public Player in Player Compare; druga agregacija ga uporabi le, če profila ne potrebuje in so njene druge povezave veljavne |
| manjka internal `hero_id`, provider `dota_hero_id` obstaja | ohrani provenance; izključeno iz Hero Mastery, hero metrike in hero benchmarka; lahko je partial v varni non-hero metriki |
| manjka internal `team_id` | dovoljeno v self Personal; izključeno iz Team Analytics in team strani Compare |
| manjka `match_game → match → tournament` | dovoljeno samo kot self Personal standalone; izključeno iz vseh application/public scope-ov |
| `is_winner = NULL` ali nekonsistenten outcome | izključeno iz core outcome/performance metrik; dovoljeno samo kot eksplicitna partial/operational diagnostika |
| cancelled ali unfinished igra | izključeno iz core rezultatskih metrik; lahko operational coverage |
| manjka canonical gameplay čas | izključeno iz time-filtered/recent/trend datasetov; lahko partial overall fact |
| manj kot pričakovano število participant vrstic | individualno popolna self vrstica se lahko pokaže kot partial raw fact; izključena je iz cohort-, side-, team-, tournament-, public- in benchmark metrik, ki zahtevajo popolno igro |
| podvojena ali nasprotujoča game/linkage identiteta | izključeno do deterministic dedup/repair; nikoli dvojno štetje |

Pričakovano število participantov je določeno z game mode-om, za običajen Dota 5v5 praviloma deset. Uspešen HTTP/import status brez preverjene participant popolnosti ni dokaz popolne igre.

Vsak agregat, ki izloča source vrstice, mora biti sposoben poročati najmanj eligible sample size in relevantne exclusion/coverage razloge. Manjkajoče vrednosti se ne pretvorijo tiho v nič, placeholder ID ali drugo kategorijo.

Trenutni normalizer ne uveljavlja pričakovanih desetih participantov, trenutni SQL pa `count(*)` lahko šteje tudi `is_winner = NULL`. To je **KNOWN GAP → task 24–35 po lastniku metrike, posebej task 31 / #170 za normalizacijo**.

## 18. Empty, partial and insufficient-data semantics

API in frontend uporabljata različna stanja, ne zamenljivih praznih tabel ali ničel:

| Stanje | Pomen | Canonical odziv/prikaz |
| --- | --- | --- |
| `EMPTY` | object/scope je veljaven in dovoljen, vendar vsebuje 0 eligible iger | uspešen odziv, praviloma `200`, z `eligibleSampleSize = 0`; sporočilo »No analytics data for the selected filters.« |
| `INSUFFICIENT_DATA` | eligible igre obstajajo, vendar sample ne doseže versioniranega praga za zanesljivo interpretacijo | pokaži raw metrike, sample size in prag; skrij ali jasno zadrži verdict, benchmark oziroma insight |
| `PARTIAL` | varen del podatkov obstaja, del pa manjka zaradi linkage, completeness ali coverage težave | vrni varne metrike skupaj z eksplicitnimi `coverage`/`exclusions` razlogi; ne predstavljaj manjkajočega kot nič |
| `ERROR` / `UNAVAILABLE` | request, odvisnost ali sibling endpoint ni uspel | error state z možnostjo retry; nikoli `EMPTY` ali zero metric |

`400` pomeni neveljaven filter/interval, `401` neavtenticiran request, `403` nedovoljen scope in `404` neobstoječ oziroma actorju neviden objekt. Obstoječ public tournament brez eligible analytics vrstic je `200 + EMPTY`, ne `404`.

Napaka enega pod-endpointa ne sme uspešnih sibling rezultatov spremeniti v prazen dataset. Composite response mora povedati, kateri sklop je unavailable ali partial. Hero Mastery že ima delno precedenco za ločen `INSUFFICIENT_DATA`; večina trenutnega dashboarda, `Promise.all` orkestracije in mapperjev te pogodbe še ne izvaja. To je **KNOWN GAP → task 23 / #162 in produktni taski 24–35**.

## 19. Compare rules

Za vsak Compare veljajo skupna pravila:

1. A in B sta različna, eksplicitna internal objekta.
2. Backend izpelje visibility in preveri object scope za obe strani.
3. Dataset, lifecycle, časovne meje, filtri in metric definicije so simetrični.
4. Standalone osebna zgodovina drugega igralca je izključena brez prihodnjega eksplicitnega consent modela.
5. Vsaka stran poroča svoj eligible sample, coverage in exclusion razloge.
6. Primerjava pod pragom je `INSUFFICIENT_DATA`; večji sample ene strani ne prikrije premajhnega sampla druge.
7. Match history in hero metrike morajo razkriti uporabljeni cohort, ne širše protected zgodovine.
8. Team A in Team B ostaneta ločena v vsaki seriji; A+B agregat ni nadomestilo za dve strani.

Specifična Player in Team pravila iz poglavij 7 in 8 imajo prednost pred generičnimi pravili. Trenutna teammate all-history pot in združeni Team Compare hero agregat sta **KNOWN GAP → task 07 / #146 in task 28 / #167**.

## 20. Insight and benchmark principles

### 20.1 Insights

Context Insights so:

- deterministični in ponovljivi za isti source, contract version in `AnalyticsScope`;
- razložljivi z navedenimi input metrikami in sprožilnim pravilom;
- filter-aware in izračunani iz istega dataseta kot prikazane metrike;
- opremljeni z eligible sample size, minimum sample pragom in omejitvami;
- brez primerjave scope-ov z različnimi linkage/visibility pravili, razen če je razlika eksplicitno del razlage.

Context weighting lahko spremeni prioriteto oziroma interpretacijo insighta, ne sme pa prepisati raw statistike. Napredna confidence kalibracija pripada **tasku 35 / #174**.

### 20.2 Benchmarks

Vsak benchmark mora pred izračunom definirati cohort in denominator, na primer:

```text
isti turnir ali eksplicitno tekmovanje
+ ista oziroma primerljiva vloga
+ isto časovno obdobje
+ ista lifecycle/data-quality pravila
+ minimalni sample
```

Trditev, kot je »Top 10 %«, brez imenovanega cohorta, denominatorja, obdobja in sample minimuma ni dovoljena. Self standalone podatki se ne primerjajo z application cohortom, razen če sta oba vira namensko definirana kot isti primerljivi benchmark dataset. Implementacija pripada **tasku 26 / #165**.

## 21. Performance/read-model policy

Privzeta izvedbena pot za DotaOps 1.0 ostaja live SQL nad normaliziranimi tabelami. Obstoječi `mv_player_metrics`, `mv_team_metrics`, `mv_hero_metrics`, `mv_tournament_metrics`, `v_*` viewi in `private.refresh_dotaops_analytics()` so legacy/compatibility objekti; njihov obstoj jih ne naredi canonical source-a.

Materialized view, cache, preaggregation ali specializiran read model se uvede samo po meritvah, ki najmanj vključujejo:

- query count in dokaz konkretnega N+1;
- execution plan in, kjer je izvedljivo, `EXPLAIN (ANALYZE, BUFFERS)`;
- end-to-end latency ter p95 ali primerljiv latency budget;
- connection-pool pritisk in sočasnost;
- realen ali reprezentativen volumen in distribucijo podatkov;
- freshness zahtevo ter strošek invalidacije/refresh-a.

Nov read model mora ohraniti celoten `AnalyticsScope`, lifecycle, data-quality in visibility contract. Cache key mora vključiti vse scope/filter/visibility dimenzije ter contract/schema version; cache hit ne sme razširiti authorization scope-a. Dokumentirati je treba freshness, invalidation, fallback in merljiv razlog za uvedbo.

Trenutni Java API uporablja live SQL. Konkretni tournament hero N+1 in širši query budget se merita ter rešujeta v **tasku 29 / #168**; ta dokument ne predpisuje optimizacijske tehnologije.

## 22. Known gaps

| Trenutno odstopanje | Vpliv | Roadmap lastnik |
| --- | --- | --- |
| Standalone import ni povezan s planiranim application match/game objektom | standalone ne more varno dobiti team/tournament scope-a | task 18 / [#157](https://github.com/DotaOps/DotaOps/issues/157) |
| Hero katalog/bootstrap in internal linkage nista production-complete | hero vrstice ostajajo unlinked, hero metrike so nepopolne | task 19 / [#158](https://github.com/DotaOps/DotaOps/issues/158) |
| Trenutni Personal in Hero SQL zaradi inner joinov izločita vse standalone igre | canonical self dataset ni implementiran; snapshot/recent/mastery ostanejo ožji | tasks 24, 25, 27 / [#163](https://github.com/DotaOps/DotaOps/issues/163), [#164](https://github.com/DotaOps/DotaOps/issues/164), [#166](https://github.com/DotaOps/DotaOps/issues/166) |
| `profileId`, team/tournament filter, `heroId`, URL state, equal time bounds in generični `limit` imajo neenotno semantiko | napačen ali neponovljiv scope, 403 in neveljavni tihi empty rezultati | task 22 / [#161](https://github.com/DotaOps/DotaOps/issues/161) |
| Main dashboard eager nalaga sibling endpointe in nima splošne stale-response zaščite | starejši request lahko prepiše novega; ena napaka skrije veljavne podatke | task 23 / [#162](https://github.com/DotaOps/DotaOps/issues/162) |
| Public analytics uporablja predvsem `is_public`, ne celotnega public tournament lifecycle-a | draft/archived ali neeligible podatki lahko preidejo v public aggregate | tasks 06, 07, 32 / [#145](https://github.com/DotaOps/DotaOps/issues/145), [#146](https://github.com/DotaOps/DotaOps/issues/146), [#171](https://github.com/DotaOps/DotaOps/issues/171) |
| Outcome-null, unfinished/cancelled in incomplete normalizacija niso enotno izključeni | `gamesPlayed != wins + losses`, lažni denominatorji in zavajajoči zero/empty rezultati | tasks 07, 24–35 / [#146](https://github.com/DotaOps/DotaOps/issues/146), [#163](https://github.com/DotaOps/DotaOps/issues/163)–[#174](https://github.com/DotaOps/DotaOps/issues/174) |
| Teammate relacija lahko odpre protected all-history Player Compare | preširok privacy/object scope | tasks 07, 28 / [#146](https://github.com/DotaOps/DotaOps/issues/146), [#167](https://github.com/DotaOps/DotaOps/issues/167) |
| Team Compare združi hero metrike ekip A in B | rezultat ni dejanska primerjava | task 28 / [#167](https://github.com/DotaOps/DotaOps/issues/167) |
| En `limit` pomeni recent window, hero rows, leaderboard size in druge stvari | API client ne more nedvoumno izraziti namena; pagination ni stabilna | tasks 22, 24–34 / [#161](https://github.com/DotaOps/DotaOps/issues/161), [#163](https://github.com/DotaOps/DotaOps/issues/163)–[#173](https://github.com/DotaOps/DotaOps/issues/173) |
| Tournament analytics izvaja N+1 za hero metrike; legacy MV refresh še obstaja brez API potrebe | connection-pool/latency tveganje brez meritvene odločitve | task 29 / [#168](https://github.com/DotaOps/DotaOps/issues/168) |
| Team, Tournament, Organizer in Match analytics še nimajo celotne ciljne metric/status pogodbe v API-ju in UI-ju | področni scope, partial state in drilldown ostajajo neenotni | tasks 30–34 / [#169](https://github.com/DotaOps/DotaOps/issues/169)–[#173](https://github.com/DotaOps/DotaOps/issues/173) |
| Benchmarks in confidence-aware insighti nimajo versioniranega cohort/confidence modela | interpretacije niso primerljive ali dovolj kalibrirane | tasks 26, 35 / [#165](https://github.com/DotaOps/DotaOps/issues/165), [#174](https://github.com/DotaOps/DotaOps/issues/174) |
| Stari analytics API dokument vsebuje drift glede trenutnih response-ov | implementation inventory lahko zamenjamo za semantic contract | task 48 / [#32](https://github.com/DotaOps/DotaOps/issues/32) |

Known gap ne spremeni pogodbe in ne dovoljuje lokalnega workarounda, ki bi razširil source ali visibility. Implementacijska naloga mora navesti, kateri del tega contracta izpolni in kako ga validira.

## 23. Follow-up roadmap tasks

| Task | Issue | Namen glede na to pogodbo |
| ---: | ---: | --- |
| 18 | [#157](https://github.com/DotaOps/DotaOps/issues/157) | poveže real OpenDota import s planirano DotaOps tekmo |
| 19 | [#158](https://github.com/DotaOps/DotaOps/issues/158) | uvede popoln hero bootstrap in preverjanje kataloga |
| 22 | [#161](https://github.com/DotaOps/DotaOps/issues/161) | uskladi endpoint-specifične filtre, URL, authorization, hero parametra in časovno validacijo |
| 23 | [#162](https://github.com/DotaOps/DotaOps/issues/162) | prepreči stale analytics response ter uvede lazy/partial nalaganje |
| 24 | [#163](https://github.com/DotaOps/DotaOps/issues/163) | uvede enoten Personal Analytics snapshot nad canonical self scope-om |
| 25 | [#164](https://github.com/DotaOps/DotaOps/issues/164) | doda Recent Form in osnovne split analize |
| 26 | [#165](https://github.com/DotaOps/DotaOps/issues/165) | implementira definirane benchmark cohorte in percentile |
| 27 | [#166](https://github.com/DotaOps/DotaOps/issues/166) | nadgradi Hero Analytics na V2 po hero linkage in quality pravilih |
| 28 | [#167](https://github.com/DotaOps/DotaOps/issues/167) | nadgradi Player/Team Compare, privacy scope in ločene A/B metrike |
| 29 | [#168](https://github.com/DotaOps/DotaOps/issues/168) | uvede query budget, meritve in odpravi potrjene N+1 |
| 30 | [#169](https://github.com/DotaOps/DotaOps/issues/169) | implementira Team Analytics V1 |
| 31 | [#170](https://github.com/DotaOps/DotaOps/issues/170) | normalizira advanced analytics podatke in quality metadata |
| 32 | [#171](https://github.com/DotaOps/DotaOps/issues/171) | implementira Tournament Analytics V1 ter public lifecycle |
| 33 | [#172](https://github.com/DotaOps/DotaOps/issues/172) | implementira Organizer Analytics V1 in operational coverage |
| 34 | [#173](https://github.com/DotaOps/DotaOps/issues/173) | implementira Match Analytics V1 |
| 35 | [#174](https://github.com/DotaOps/DotaOps/issues/174) | implementira confidence-aware Insight Feed |

Varnostno utrjevanje public/privacy in object scope-a ostaja v tasku 06 / [#145](https://github.com/DotaOps/DotaOps/issues/145) ter tasku 07 / [#146](https://github.com/DotaOps/DotaOps/issues/146). Celotna konsolidacija dokumentacije ostaja v tasku 48 / [#32](https://github.com/DotaOps/DotaOps/issues/32).
