# Krovni backend vodic

Ta dokument je vstopna tocka za backend dokumentacijo. Namenjen je hitremu
iskanju: kateri dokument odpreti, ko potrebujes API pogodbo, podatkovni model,
demo podatke, testne ukaze ali pregled implementiranih backend funkcionalnosti.

Krovni vodic ne nadomesca izvorne kode, migracij ali generiranih Supabase tipov.
Pred spremembami backend logike vedno preveri tudi relevantne migracije, tipe,
servise, controllerje in teste.

## Hiter zemljevid dokumentov

| Dokument | Kaj vsebuje | Kdaj ga uporabis |
| --- | --- | --- |
| [project-current-state-overview.md](project-current-state-overview.md) | Sirok pregled projekta, stacka, backend arhitekture, baze, API endpointov, glavnih poslovnih tokov, testov, lokalnega zagona in tveganj. | Ko potrebujes celotno sliko backend sistema ali orientacijo pred vecjo spremembo. |
| [backend-analytics-api.md](backend-analytics-api.md) | Backend pogodbo za analytics endpoint-e, filtre, role-based analytics, lookup endpoint-e, primerjave ekip/igralcev, napake, indekse, znane omejitve in teste. | Ko delas na analytics UI/API integraciji, filtiranju, primerjavah ali agregatih iz `matches`, `match_games`, `match_imports` in `match_players`. |
| [backend-my-team-api.md](backend-my-team-api.md) | My Team API pogodbo: aktivna ekipa, capabilities, leave team, disband, roster profile/stats, transfer ownership, DB/audit spremembe in teste. | Ko spreminjas team ownership, roster tokove, pravice clanov ekipe ali My Team frontend integracijo. |
| [backend-storage-upload-api.md](backend-storage-upload-api.md) | Signed upload flow za avatarje, team logotipe in bannerje, Supabase Storage buckete, path pravila, velikosti, MIME tipe, varnost, DB spremembe, audit in teste. | Ko delas na uploadih javnih slik, Storage RLS pravilih ali potrjevanju asset URL-jev. |
| [backend-demo-seed.md](backend-demo-seed.md) | Demo seed dataset, demo login racune, seed/reset/verify ukaze, frontend/API mapping, analytics refresh, varnostna opozorila in omejitve. | Ko potrebujes realisticne lokalne podatke, demo prijave ali preverjanje UI tokov z znanimi ekipami, turnirji in match statistiko. |
| [sonarqube.md](sonarqube.md) | GitHub setup, CI vedenje in lokalni SonarQube scan. | Ko preverjas quality gate, CI analizo ali lokalni static analysis workflow. |

## Iteracijska porocila

Historicen kontekst backend razvoja je v `docs/reports`:

| Dokument | Kaj vsebuje |
| --- | --- |
| [Iteracija_1_DotaOps_pregled.md](reports/Iteracija_1_DotaOps_pregled.md) | Prvi splosni pregled projekta: arhitektura, baza, profili, ekipe, javni/organizer pregledi, Steam/OpenDota foundation in zacetni testi. |
| [Iteracija_2_DotaOps_backend_pregled.docx](reports/Iteracija_2_DotaOps_backend_pregled.docx) | Backend porocilo 2. iteracije. Uporabno za zgodovinski pregled funkcionalnosti, odlocitev in takratnega stanja implementacije. |
| [Iteracija_3_DotaOps_backend_pregled.docx](reports/Iteracija_3_DotaOps_backend_pregled.docx) | Backend porocilo 3. iteracije. Uporabno za nadaljnji razvoj backend tokov in spremembe po drugi iteraciji. |
| [Iteracija_4_DotaOps_backend_pregled.docx](reports/Iteracija_4_DotaOps_backend_pregled.docx) | Backend porocilo 4. iteracije. Uporabno za najnovejsi iteracijski opis backend napredka v porocilni obliki. |

Ta porocila so bolj narativna kot API pogodbe. Za trenutno tehnicno pogodbo vedno
daj prednost aktualnim Markdown dokumentom in kodi.

## Po tipicnih nalogah

### Novi razvijalec ali hiter onboarding

1. Odpri [project-current-state-overview.md](project-current-state-overview.md).
2. Preberi odseke o backend arhitekturi, bazi, poslovnih tokovih in lokalnem zagonu.
3. Odpri relevantni specializirani backend dokument iz zgornje tabele.
4. Ce potrebujes podatke za rocno preverjanje, uporabi [backend-demo-seed.md](backend-demo-seed.md).

### Frontend integracija z backendom

- Za team ekran: [backend-my-team-api.md](backend-my-team-api.md).
- Za analytics ekrane: [backend-analytics-api.md](backend-analytics-api.md).
- Za avatar/logotip/banner upload: [backend-storage-upload-api.md](backend-storage-upload-api.md).
- Za demo podatke in testne uporabnike: [backend-demo-seed.md](backend-demo-seed.md).
- Za sirsi seznam endpointov: [project-current-state-overview.md](project-current-state-overview.md), odsek `API endpoint pregled`.

### Backend sprememba z bazo ali pravicami

Pred spremembo preveri:

- aktualne migracije v `backend/src/main/resources/db/migration`;
- Supabase RLS pravila in helper funkcije;
- generirane `Database` tipe, ce sprememba vpliva na TypeScript;
- specializirani dokument, ce spreminjas analytics, team, storage ali demo seed;
- teste, navedene v posameznem backend dokumentu.

### Demo, lokalno testiranje in predstavitev

Uporabi [backend-demo-seed.md](backend-demo-seed.md), ker vsebuje:

- demo uporabnike in skupno demo geslo;
- ukaz za seed, reset in verifikacijo;
- opis ustvarjenih turnirjev, ekip, tekem, importov in player statistik;
- mapping med frontend/API tokovi in tabelami, ki jih seed napolni.

### Kakovost, CI in regresije

- Za backend testne ukaze poglej `Testi` odsek v specializiranem dokumentu.
- Za splosni testni pregled poglej [project-current-state-overview.md](project-current-state-overview.md), odsek `Testi in kakovost`.
- Za SonarQube in CI quality gate poglej [sonarqube.md](sonarqube.md).

## Backend domene in glavni viri

| Domena | Primarni dokumenti |
| --- | --- |
| Arhitektura, sloji, error handling, security | [project-current-state-overview.md](project-current-state-overview.md) |
| Supabase/PostgreSQL shema, migracije, RLS | [project-current-state-overview.md](project-current-state-overview.md) in konkretne migracije |
| Teams, roster, ownership | [backend-my-team-api.md](backend-my-team-api.md) |
| Storage uploadi in javni asseti | [backend-storage-upload-api.md](backend-storage-upload-api.md) |
| Analytics, lookupi, primerjave | [backend-analytics-api.md](backend-analytics-api.md) |
| Demo podatki in realisticen lokalni dataset | [backend-demo-seed.md](backend-demo-seed.md) |
| Kakovost kode in SonarQube | [sonarqube.md](sonarqube.md) |
| Zgodovinski razvoj po iteracijah | [docs/reports](reports/) |

## Pravila za posodabljanje dokumentacije

- Ko dodas nov backend feature dokument, dodaj povezavo v ta vodic.
- Ko spremenis API response, request body, pravice ali statusne prehode, popravi
  specializirani backend dokument.
- Ko sprememba vpliva na vec domen, popravi tudi
  [project-current-state-overview.md](project-current-state-overview.md).
- Ko se spremenijo demo podatki, login racuni ali verify queryji, popravi
  [backend-demo-seed.md](backend-demo-seed.md).
- Dokumentacija naj ostane opis trenutnega stanja, ne zeljena prihodnja slika.
