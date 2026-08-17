# DotaOps 1.0 Security, Roles and Trust Boundary

> **Status:** CANONICAL za DotaOps 1.0
>
> **Velja od:** 2026-08-17
>
> **Implementacijski baseline pregleda:** veja development, commit 10a3552362e1ceccb82da8ebf0301fc0dc8ad0da

Ta dokument je avtoritativen vir za globalne vloge, object capabilities, privacy klasifikacijo in trust boundary DotaOps 1.0. Če se trenutna koda, migracija, RLS politika, grant, DTO ali starejši dokument razlikuje od te pogodbe, velja ta dokument, odstopanje pa je implementacijska vrzel iz poglavja 15.

Besede **MORA**, **NE SME** in **SAMO** so normativne. V dokumentu ni implicitnih dovoljenj: kar ni izrecno dovoljeno, je privzeto zavrnjeno.

## 1. Namen in obseg

Dokument zamrzne odločitve, ki jih potrebujejo nadaljnje security, API, RLS, storage in analytics naloge:

- globalne vloge DotaOps 1.0;
- Captain kot capability konkretne ekipe;
- obe trenutni authentication poti in skupni application identity;
- object-level authorization za pomembne poslovne objekte;
- odgovornosti Browser/Next.js, Supabase Auth, Spring Boot, PostgreSQL/RLS in Supabase Storage;
- izčrpen seznam dovoljenih neposrednih Supabase poti;
- role/capability in privacy matriko;
- minimal-disclosure pravila za public API;
- authorization mejo analitike.

Dokument ne spreminja produkcijske kode in ne trdi, da so vse zapisane zahteve že implementirane. Podrobna analytics source, filter in lifecycle semantika sodi v nalogo 03; ta dokument zanjo že določa varnostno mejo. Podrobni dovoljeni prehodi posameznih state machineov ostanejo v domenskih API pogodbah, vendar ne morejo razširiti tukaj določenega object scope-a.

## 2. DotaOps 1.0 globalne vloge

DotaOps 1.0 ima natanko tri persistirane globalne profilne vloge:

| Globalna vloga | Namen | Osnovni scope |
|---|---|---|
| **PLAYER** | Igralčev profil, ekipe in osebna analitika | Self scope; member scope; Captain capability, kadar je igralec captain konkretne ekipe |
| **ORGANIZER** | Ustvarjanje in upravljanje turnirjev | Ustvari turnir; zasebne podatke in mutacije dobi samo za turnir, ki ga dejansko upravlja |
| **ADMIN** | Varnostno in operativno upravljanje platforme | Globalni administrativni scope prek eksplicitnih, namensko zaščitenih backend tokov |

**Unauthenticated** je stanje neprijavljenega obiskovalca, ne globalna vloga. Tehnični enum **VISITOR** je lahko fallback v kodi, vendar ni persistirana produktna vloga DotaOps 1.0.

Vloge niso implicitna hierarhija. **ORGANIZER** ne podeduje Player ali Captain pravic. **ADMIN** uporablja eksplicitne admin override-e; dejanja se ne smejo zanašati na naključno dedovanje Spring authorities ali na client-side role gate.

PLAYER in ORGANIZER se lahko v DotaOps 1.0 izbereta ob registraciji. ADMIN se ne sme samodejno dodeliti iz browser inputa, JWT user metadata ali signup payload-a; dodeli se samo prek zaupanja vrednega, auditiranega administrativnega toka. Po provisioningu je avtoritativen vir vloge application profil v PostgreSQL, ne Supabase user metadata in ne frontend stanje.

**REFEREE** in **ANALYST** nista vlogi niti aktivni capability DotaOps 1.0. Morebitne legacy enum vrednosti v tournament_staff ne podelijo 1.0 dovoljenj. GitHub nalogi #13 in #46 ostaneta zaprti oziroma deferred.

## 3. Captain in object capabilities

**CAPTAIN ni globalna profilna vloga.** Je relationship uporabnika do ene konkretne ekipe:

> PLAYER + teams.captain_profile_id = actor.profile_id + aktivna ekipa = Captain capability za to ekipo

Trenutni podatkovni model capability pravilno predstavlja z **teams.captain_profile_id**. V30 je ob migraciji backfillal oziroma reaktiviral team_members članstvo takratnih captain profilov; trajni DB invariant aktivnega članstva še ni zagotovljen. **team_members.member_role** opisuje igralno pozicijo, na primer carry ali support, in ni authorization vloga.

Canonical pravila:

- ekipa ima največ enega trenutnega captain profila;
- Captain capability velja samo za navedeno, nedisbandano ekipo;
- capability ne velja za drugo ekipo in ne podeli tournament ali admin pravic;
- captain mora biti PLAYER in aktiven član svoje ekipe;
- capability preneha ob veljavnem prenosu captainstva, disbandu ekipe ali drugi transakciji, ki razmerje zakonito odstrani;
- prenos captainstva, leave in disband so Spring Boot workflowi, ne neposredne spremembe captain_profile_id ali membership vrstic;
- ADMIN lahko izvede izrecno administrativno intervencijo, vendar s tem ne postane Captain.

Enak vzorec velja za druge object capabilities. Tournament owner in tournament_staff owner/organizer sta relationshipa do konkretnega turnirja, ne novi globalni vlogi.

## 4. Authentication model

Authentication odgovarja na vprašanje: **Kdo je uporabnik?**

### Supabase Auth pot

1. Browser uporablja Supabase Auth za registration, login in session lifecycle.
2. Browser pošlje Supabase access token Spring Boot API-ju kot Bearer JWT.
3. Spring preveri dovoljeni podpisni algoritem, podpis, issuer, audience, časovno veljavnost in UUID subject.
4. Spring po auth user ID-ju naloži DotaOps application profil iz PostgreSQL.
5. Globalna vloga in profile ID iz baze tvorita application actorja.

Supabase user metadata je neavtoritativen onboarding input. Lahko izrazi želeno PLAYER ali ORGANIZER vlogo, ne sme pa sama podeliti ADMIN, Captain ali object capability pravic.

### Steam pot

1. Browser začne Steam OpenID tok na Spring Boot endpointu.
2. Spring ustvari in enkratno porabi login state ter pri Steamu preveri callback.
3. Validirana Steam identiteta se poveže z obstoječim ali novim DotaOps application profilom in zapisom external account.
4. Spring izda podpisan HttpOnly Steam session cookie.
5. Ob requestu Spring preveri cookie in po profile ID-ju naloži isti application profil in isto DB-vlogo kot pri Supabase poti.

Steam-only profil lahko nima Supabase auth_user_id. Object ownership se zato v aplikacijski domeni vedno opira na application **profileId**, kadar je ta na voljo, ne izključno na auth user ID.

Obe poti se zaključita z istim konceptom:

> verified identity → DotaOps application profile → DB global role → object capabilities

Frontend role state, query parametri, skrita navigacija in route shell so samo UX. Ne predstavljajo authentication ali authorization dokaza.

## 5. Authorization model

Authorization odgovarja na vprašanje: **Kaj sme ta actor narediti s tem konkretnim objektom v trenutnem stanju?**

Canonical odločitev je:

> verified authentication + DB global role + object ownership/capability + lifecycle/action rule = authorization

Za vsak protected request veljajo naslednja pravila:

1. Spring iz verificirane seje ustvari actorja in zahteva DotaOps profil, kadar operacija potrebuje profile scope.
2. Vse ID-je, profile ID-je, team ID-je, tournament ID-je, import ID-je in Storage poti iz browserja obravnava kot nepreverjen input.
3. Spring strežniško naloži objekt in njegove relacije ter preveri globalno vlogo in zahtevan object scope.
4. Spring preveri dovoljeni lifecycle prehod, input, invariant in transakcijske pogoje.
5. Šele nato repository izvede namensko poizvedbo ali mutacijo.
6. PostgreSQL constraints, grants in RLS isti scope dodatno omejijo kot defense-in-depth.

Sama globalna vloga ni dovolj:

- PLAYER ureja samo svoj profil;
- PLAYER s Captain capabilityjem upravlja roster samo svoje ekipe;
- ORGANIZER upravlja samo turnir, pri katerem je organizer_profile_id ali ima eksplicitno tournament_staff owner/organizer relacijo;
- ADMIN ima globalni administrativni scope samo prek eksplicitnega backend/admin toka.

Privzeta odločitev je deny. Client-side preverjanje nikoli ne nadomesti backend preverjanja, uspešen RLS check pa nikoli ne nadomesti business state machinea.

## 6. Trust boundary

~~~mermaid
flowchart LR
  subgraph Client["Nezaupanja vredna client meja"]
    Browser["Browser / Next.js"]
  end

  subgraph Identity["Identity ponudniki"]
    SupabaseAuth["Supabase Auth"]
    Steam["Steam OpenID"]
  end

  subgraph Application["Zaupanja vredna aplikacijska meja"]
    Spring["Spring Boot API"]
  end

  subgraph Data["Podatkovna meja"]
    Postgres["PostgreSQL / Supabase DB<br/>constraints + grants + RLS"]
    Storage["Supabase Storage"]
  end

  Browser -->|"registration, login, session, refresh, logout"| SupabaseAuth
  Browser -->|"Steam redirect"| Steam
  Steam -->|"verificiran callback"| Spring
  Browser -->|"Bearer JWT ali podpisan Steam cookie<br/>application/business API"| Spring
  Spring -->|"authentication context, authorization,<br/>validation, workflow, transaction"| Postgres
  Spring -->|"authorize in izdaj/validiraj točen storage intent"| Storage
  Browser -.->|"backend-issued signed URL<br/>ali read-only javni asset"| Storage
~~~

Trust-boundary pravila:

- Browser/Next.js je nezaupanja vreden. Ne sme hraniti service-role ključa ali odločati o končnih dovoljenjih.
- Supabase publishable/anon ključ identificira javnega odjemalca; ni skrivnost in sam po sebi ni authorization.
- Supabase Auth je authority za Supabase auth/session lifecycle. Spring je authority za validacijo Bearer tokena v application API-ju in za Steam OpenID/session pot.
- Spring Boot je edina application/business API meja.
- PostgreSQL in RLS sta zadnja podatkovna zaščita in ne izvajata namesto Springa kompleksnih workflowov.
- Storage sprejme byte stream samo v okviru natančno omejenega, predhodno avtoriziranega toka.
- Server-side DB in service-role credentials nikoli ne prečkajo trusted application meje.

## 7. Spring Boot in Supabase odgovornosti

| Komponenta | MORA | NE SME |
|---|---|---|
| Browser / Next.js | Upravljati UI, poslati Bearer token ali Steam cookie, zbirati input, obravnavati 401/403, uporabiti izdani signed URL | Odločati o object authorization; zaupati lastnim role/capability booleanom; neposredno mutirati poslovne tabele |
| Supabase Auth | Registration/login/session/refresh/logout; izdaja in preklic Supabase auth tokenov | Biti vir DotaOps object capabilities ali ADMIN pravice iz user metadata |
| Spring Boot | Identity mapping; DB role lookup; application in object authorization; validacija; state transitions; transakcije; audit context; minimalni DTO-ji; issuance in confirm Storage operacij | Prenesti business odločitev na frontend ali RLS; vrniti base-row podatke samo zato, ker jih repository lahko prebere |
| PostgreSQL / RLS | Constraints; referenčna integriteta; najmanjši granti; row/column defense-in-depth; varna pomoč Spring transakcijam | Predstavljati primarni business API; dovoliti obhod Spring workflowa; zamenjati service-level authorization |
| Supabase Storage | Shraniti objekte; uveljaviti bucket/path/operation omejitve; podpreti kratkožive signed operacije | Sprejeti poljubno business pot ali dovoliti browserju zapis DB reference |

**Vse poslovne mutacije aplikacijskih tabel MORAJO iti skozi Spring Boot.** To vključuje profile z business pravili, teams, memberships, invitations, join requests, manual players, tournaments, registrations, staff, groups, bracket, matches, results, imports, heroes, notifications, audit zapise in admin maintenance.

Backend mora uporabljati najmanj privilegirano strežniško DB identiteto. Tudi kadar trenutna JDBC identiteta tehnično obide RLS, mora Spring pred repository klicem uveljaviti isto ali strožjo application pogodbo.

Trenutne migracije RLS vključijo na aplikacijskih tabelah in več javnih analytics viewov uporablja security-invoker. Ta defense-in-depth osnova ostane. Široki granti, preširoke politike in funkcijski ACL-i iz poglavja 15 pa pomenijo, da trenutna RLS površina še ne izpolnjuje te pogodbe.

## 8. Neposredna uporaba Supabase

Naslednji seznam je izčrpen allowlist neposredne uporabe Supabase iz Browser/Next.js v DotaOps 1.0.

### 8.1 Dovoljeno: Supabase Auth

- registration/sign-up;
- login/sign-in;
- getUser, getSession in getClaims za auth/session stanje;
- samodejni ali eksplicitni refresh session;
- logout/sign-out in auth-session lifecycle;
- pridobitev veljavnega access tokena, ki se nato pošlje Spring Boot API-ju.

### 8.2 Dovoljeno: nadzorovan Supabase Storage

- read-only dostop do objekta, ki je izrecno klasificiran kot PUBLIC in objavljen na canonical javni poti;
- download zasebnega objekta samo z ustrezno avtoriziranim, časovno omejenim signed URL-jem;
- upload byte streama samo z backend-issued, kratkoživim signed URL/tokenom za točen bucket, path, object type in operacijo;
- confirm izključno prek Spring Boot, ki ponovno preveri actorja, object ownership, canonical path, obstoj objekta, dejansko velikost, content type in vsebino, preden zapiše DB referenco.

Browser ne sme dobiti service-role ključa. Oznaka public bucket sama po sebi ne odobri write-a; pomeni samo, da je objavljen objekt namerno javen za branje. Vsak write še vedno potrebuje canonical signed tok.

### 8.3 Ni dovoljeno kot canonical frontend pot

- neposreden Supabase Data API SELECT nad application tabelami ali viewi;
- neposreden INSERT, UPDATE, DELETE ali UPSERT nad application tabelami;
- neposredna GraphQL business poizvedba ali mutacija;
- neposreden RPC klic application ali private funkcije;
- neposreden Realtime subscription nad zasebnimi application vrsticami;
- neposreden analytics, import, notification ali audit dostop;
- splošen Storage SDK write, četudi trenutni path RLS tak zapis dopušča;
- zapis avatar_url, logo_url, banner_url ali njihovega patha mimo backend confirma.

Trenutni neposredni frontend profile SELECT fallbacki, profile UPDATE/UPSERT fallbacki in neposreden homepage profile read so **MIGRATION REQUIRED**. Write poti rešuje naloga 04; public/private read in DTO površino naloga 06.

Trenutna kombinacija legacy multipart uploadov, neposrednih Storage RLS write zmožnosti in nepopolnega signed confirma je **MIGRATION REQUIRED — naloga 41**. Signed upload je canonical smer; legacy tok ni dovoljena arhitekturna izjema.

Server-side Spring uporaba Supabase DB, Auth admin ali Storage service credentials ni neposredna client uporaba. Mora ostati na trusted meji, biti najmanj privilegirana in vezana na backend authorization.

## 9. Role in capability matrika

Matrika je normativna. Kjer endpoint še ne obstaja, zamrzne njegov prihodnji authorization scope in ne trdi, da je funkcionalnost že implementirana. **Javno** vedno pomeni minimalni DTO in dovoljen lifecycle, ne celotne DB vrstice.

### 9.1 Profiles in external accounts

| Operacija | Unauthenticated | PLAYER | PLAYER + Captain | ORGANIZER | ADMIN |
|---|---|---|---|---|---|
| Public profile read | DA — public DTO | DA — public DTO | DA — public DTO | DA — public DTO | DA — public DTO |
| Own full profile read | NE | DA — self | DA — self | DA — self | DA — self |
| Own profile edit | NE | DA — self, prek Springa | DA — self, prek Springa | DA — self, prek Springa | DA — self ali eksplicitni admin tok |
| Other private profile | NE | NE | NE | NE | DA — namenski, auditiran admin/support scope |
| Own external accounts | NE | DA — read/link/unlink/sync prek Springa | Enako | DA — self | DA — self |
| Other users' external accounts | NE | NE | NE | NE | Samo namenski, auditiran support/security scope; nikoli credential ali session skrivnosti |
| Global role change | NE | NE | NE | NE | DA — eksplicitni, auditiran user/role admin tok; nikoli iz client metadata |

### 9.2 Teams, roster in membership

| Operacija | Unauthenticated | PLAYER | PLAYER + Captain | ORGANIZER | ADMIN |
|---|---|---|---|---|---|
| View public team | DA — aktivna public projekcija | DA | DA | DA | DA |
| View public active roster | DA — samo javna identiteta/playing role | DA | DA | DA | DA |
| View member-private team data | NE | DA — samo kot aktiven član | DA — lastna ekipa | NE, razen posebej avtoriziran tournament projection | DA — eksplicitni admin scope |
| Create team | NE | DA — če ni v konfliktu z aktivnim team invariantom; creator postane captain | NE za dodatno ekipo, dokler capability traja | NE | DA — namenski admin tok |
| Edit team | NE | NE | DA — samo lastna aktivna ekipa | NE | DA — eksplicitni override |
| Delete/disband team | NE | NE | DA — lastna ekipa in dovoljen workflow | NE | DA — eksplicitni override |
| Own invitations | NE | DA — samo kjer je actor invitee | Enako, ob upoštevanju membership invarianta | NE | DA — operativni scope |
| Accept/decline own invitation | NE | DA — self in dovoljen workflow | Samo če membership invariant to dopušča | NE | DA — izjemni admin tok |
| Own join requests | NE | DA — create/read/cancel self | Samo če membership invariant to dopušča | NE | DA — operativni scope |
| Team invitation management | NE | NE | DA — create/list/cancel samo za lastno ekipo | NE | DA — eksplicitni override |
| Team join-request review | NE | NE | DA — accept/decline samo za lastno ekipo | NE | DA — eksplicitni override |
| Member/playing-role management | NE | NE; član lahko zapusti samo sebe | DA — add/update/deactivate lastne ekipe skozi capacity/invitation pravila | NE | DA — eksplicitni override |
| Manual players | Javno samo sanitizirana identiteta, če je objavljena | Member-private read svoje ekipe | DA — manage lastne ekipe; note ostane private | Sanitiziran public projection; note samo v private registration contextu managed tournamenta | DA — eksplicitni scope |
| Captain transfer | NE | NE | DA — lastna ekipa, na upravičenega aktivnega PLAYER člana, transakcijsko | NE | DA — izjemni auditirani tok |
| Leave team | NE | DA — samo lastno članstvo | Ne neposredno; najprej veljaven transfer ali disband | NE | DA — izjemni tok |

### 9.3 Tournaments, registrations, structure in matches

| Operacija | Unauthenticated | PLAYER | PLAYER + Captain | ORGANIZER | ADMIN |
|---|---|---|---|---|---|
| Public tournament view | DA — is_public in registration/published/live/finished | DA | DA | DA | DA |
| Private/draft tournament view | NE | Samo dovoljen team/registration projection | Enako za lastno registrirano ekipo | DA — samo managed tournament | DA — globalni admin scope |
| Create tournament | NE | NE | NE | DA — postane manager ustvarjenega turnirja | DA — eksplicitni admin tok |
| Edit/publish/archive tournament | NE | NE | NE | DA — samo organizer ownership ali owner/organizer staff capability | DA — globalni override |
| Tournament staff read/manage | NE | NE | NE | DA — samo managed tournament; 1.0 lahko podeli/odvzame le owner/organizer relationship po varnem backend workflowu | DA — globalni, auditirani override |
| Register team | NE | NE | DA — samo lastna ekipa, odprt registration lifecycle in veljaven roster | NE, ne sme impersonirati captain-a | DA — izjemni backend tok |
| Cancel/withdraw registration | NE | NE | DA — samo lastna ekipa in dovoljen pred-lock lifecycle | NE; manager uporablja review/reject workflow | DA — izjemni backend tok |
| Read own-team registration | NE | DA — kot aktiven član, sanitiziran team scope | DA — lastna ekipa, tudi captain-private kontaktni podatki | Samo če upravlja povezani turnir | DA |
| Approve/reject/waitlist | NE | NE | NE | DA — samo managed tournament | DA |
| Check-in | NE | NE | DA — lastna registrirana ekipa | DA — samo managed tournament | DA |
| Public groups/standings/bracket | DA — samo public lifecycle | DA | DA | DA | DA |
| Manage groups/standings/bracket | NE | NE | NE | DA — samo managed tournament | DA |
| Public match schedule/result | DA — samo public tournament lifecycle in minimalni DTO | DA | DA | DA | DA |
| Schedule/start/cancel/result/finish match | NE | NE | NE | DA — samo match povezan z managed tournamentom | DA |

Tournament_staff **owner** in **organizer** sta object capabilities. Za ne-admin upravljanje mora biti actor hkrati globalni ORGANIZER. Referee/analyst vrednosti ne podelijo DotaOps 1.0 dovoljenj.

### 9.4 OpenDota in analytics

| Operacija | Unauthenticated | PLAYER | PLAYER + Captain | ORGANIZER | ADMIN |
|---|---|---|---|---|---|
| Hero catalog read | DA — public reference fields | DA | DA | DA | DA |
| Personal/profile-anchored import create | NE | DA — samo za svoj profile context, kadar endpoint to podpira | Enako | NE | DA |
| Tournament/match import create | NE | NE | NE samo zaradi Captain capabilityja | DA — samo povezan managed tournament/match | DA |
| Import status/events | NE, razen posebej sanitiziran public match status | DA — samo self-requested/profile-anchored import | Enako; team scope sam ne razširi zgodovine | DA — samo managed tournament/match import | DA |
| Import retry | NE | DA — samo svoj import in enak ali strožji scope kot create/read | Enako | DA — samo managed tournament/match import | DA |
| Raw import diagnostics/payload | NE | NE; dobi le sanitiziran status/error code | NE | NE; dobi le potrebni managed status | DA — operativni, auditiran scope |
| Personal Analytics | NE | DA — self | DA — self | NE | Samo namenski, auditiran admin/support scope |
| Hero Mastery | NE | DA — self/private | DA — self/private | NE | Samo namenski, auditiran admin/support scope |
| Player Compare | Samo izrecno public podatki | DA — self + druga stran samo v neodvisno dovoljenem public/object kontekstu | Enako; teammate relation ne odpre all-history | Samo public ali managed-tournament context | DA — namenski auditiran scope |
| Protected Team Analytics | NE | DA — active member svoje ekipe | DA — lastna ekipa | Samo podatki znotraj managed tournament contexta | DA |
| Public Team Analytics | DA — agregati iz public-eligible tekem | DA | DA | DA | DA |
| Tournament Analytics | DA — public endpoint/public lifecycle | DA — public | DA — public oziroma dovoljen registered-team projection | DA — public ali private samo za managed tournament | DA |
| Organizer Analytics | NE | NE | NE | DA — samo zbirka managed tournaments | DA — globalni admin scope |
| Public Analytics | DA — samo public lifecycle in privacy-approved source | DA | DA | DA | DA |
| Analytics refresh/maintenance | NE | NE | NE | NE | DA — namenski backend/admin endpoint |

### 9.5 Notifications, Storage, audit in administration

| Operacija | Unauthenticated | PLAYER | PLAYER + Captain | ORGANIZER | ADMIN |
|---|---|---|---|---|---|
| Own notifications | NE | DA — samo recipient self; read/mark-read prek Springa | Enako | DA — samo own notifications | DA — own notifications |
| Notification processing/diagnostics | NE | NE | NE | NE | DA — outbox, retry/error in provider diagnostics |
| Public profile/team/tournament media read | DA — samo objavljeni public asset | DA | DA | DA | DA |
| Own profile media upload/confirm | NE | DA — self signed flow | DA — self signed flow | DA — self signed flow | DA — self ali namenski admin tok |
| Team media upload/confirm | NE | NE | DA — lastna ekipa, signed flow | NE | DA — namenski admin tok |
| Tournament media upload/confirm | NE | NE | NE | DA — samo managed tournament, signed flow | DA |
| Private media download | NE | Samo objekt, za katerega ima read scope, s signed URL | Enako | Samo managed object | DA — namenski operativni scope |
| Read audit | NE | NE | NE | NE | DA — sanitiziran, paginiran admin endpoint |
| Raw/operational audit access | NE | NE | NE | NE | DA — strogo omejen, auditiran operativni scope |
| Hero sync | NE | NE | NE | NE | DA |
| Operational maintenance | NE | NE | NE | NE | DA — namenski endpoint/job |
| User/role administration | NE | NE | NE | NE | DA — explicitno, auditirano, brez client metadata |

## 10. Object-level authorization

| Objekt | Canonical read odločitev | Canonical mutation odločitev |
|---|---|---|
| **Profile** | Public minimal DTO; self full DTO; drugi private samo admin/support | Self za dovoljena polja; role/security spremembe samo admin |
| **Team** | Public active projection; private data za aktivnega člana; admin | PLAYER + actor.profileId = captain_profile_id za lastno aktivno ekipo; admin override |
| **Tournament** | Public samo po public lifecycle; manager vidi managed object | ORGANIZER + organizer ownership ali tournament_staff owner/organizer relation; admin |
| **Registration** | Public samo sanitiziran approved projection; team/member/captain in manager dobijo ustrezen private scope | Captain lastne ekipe create/cancel/check-in; tournament manager review; admin |
| **Match** | Public minimal schedule/result samo prek public tournament lifecycle; manager private | Podeduje manage capability iz povezanega tournament_id; admin |
| **MatchImport** | Requester/self profile ali manager povezanega match/tournamenta; public le izrecno sanitiziran status | Enak ali strožji scope kot read/create; poljubni import ID nikoli ni dovoljenje |
| **Notification** | recipient_profile_id = actor.profileId; admin diagnostics | Recipient samo dovoljeni read marker prek Springa; processing/admin samo admin |
| **Storage object** | Public asset ali signed read po object scope-u | Profile owner, team captain ali tournament manager za canonical path; admin; vedno backend issuance/confirm |
| **Protected Analytics** | Self, active member, managed tournament ali namenski admin scope glede na tip | Analytics maintenance samo admin; filtri ne smejo razširiti actorjevega osnovnega scope-a |

Povezani ID se vedno razreši strežniško. Na primer match import mora strežniško določiti requesterja, profile, match, match_game in tournament; browser ne sme zamenjati ID-ja in s tem pridobiti širšega dostopa.

Authorization ni samo row ownership. Vključuje tudi dovoljeno akcijo in stanje. Captain, ki sme videti registration svoje ekipe, zato ne sme neposredno nastaviti statusa approved; organizer, ki sme pregledati registration, ne sme impersonirati captain cancellation toka.

## 11. Privacy in klasifikacija podatkov

### 11.1 Klasifikacijske ravni

| Razred | Pomen |
|---|---|
| **PUBLIC** | Podatek sme dobiti neprijavljen uporabnik prek namenskega minimalnega DTO-ja in dovoljenega lifecycle-a |
| **AUTHENTICATED / PRIVATE** | Podatek zahteva prijavo in dodatni self/object razlog; nikoli ne pomeni vsi authenticated |
| **OWNER / MEMBER / MANAGER** | Podatek zahteva dokazano lastništvo, aktivno članstvo, Captain ali tournament manage capability |
| **ADMIN ONLY** | Varnostni, operativni, diagnostični ali globalno občutljiv podatek prek namenskega admin toka |

### 11.2 Podatkovna matrika

| Domena | PUBLIC | AUTHENTICATED / OWNER / MEMBER / MANAGER | ADMIN ONLY oziroma interno |
|---|---|---|---|
| Profiles | id, nickname, displayName, avatarUrl, bio, countryCode | Own full profile in editabilna polja | auth_user_id, zasebni email, role-change audit, security metadata, sync failure operativa |
| Gaming identifiers | V generičnem public profile DTO-ju niso javni | steam_id64, OpenDota account ID in provider link so owner-private | Provider raw metadata, povezovalna diagnostika, anti-abuse podatki |
| External accounts | Ni public vrstice | Owner: provider, varni display handle/link state in sync status, če jih UI potrebuje | Credential/session/token/secret, raw provider payload, notranje napake; support dostop auditiran |
| Teams | Aktivna ekipa: id, name, tag, slug, region, logo/banner, description, javni captain display | Active member data, invitations, join requests, membership history, interne nastavitve | created_by auth ID, storage path operativa, security/audit podatki |
| Team roster | Samo aktivni sanitizirani člani: public profile identity in playing role | Inactive/left history; invitation email/message; join-request message/resolution | Abuse/operational metadata |
| Manual players | Display name/nickname samo, kadar je udeleženec namenoma v public roster/tournament projekciji | **note je vedno private** za captain-a in relevantnega tournament managerja | Operativni/audit podatki |
| Tournaments | is_public = true in status registration, published, live ali finished; minimalna vsebina, pravila, urnik, format, javne povezave | Draft/private/archive, polne settings, staff, manager workflow | Internal metadata, audit in security operativa |
| Registrations | Samo approved team identity, seed po objavi javnega bracketa ter sanitiziran roster snapshot | Captain/team member summary; manager full application; check-in status, contact_email, application message, metadata, reviewer podatki in notes niso public | Raw audit, abuse in operational metadata |
| Groups/bracket/standings | Struktura in rezultati public-eligible turnirja | Draft structure in manager controls | Lock/concurrency/audit operativa |
| Matches/match games | Public schedule, teams, status, score, winner in varni normalizirani statistični podatki | Manager notes, cancellation reason, private setup, import linkage/status | raw_summary/raw_response, provider payload, notranji error in diagnostics |
| Match imports/events | Le izrecno sanitiziran public match status/error code, če endpoint to potrebuje | Requester ali manager: status, timestamps in varno sporočilo | requested_by kadar ni potreben, raw_response, normalized payload, event payload, provider error, retry diagnostics |
| Heroes | id, display/localized name, public image/reference fields | Ni posebnega private user scope-a | Sync source, maintenance in error operativa |
| Notifications | Ni public | Recipient vidi samo lastni subject/message, status in read marker | Outbox payload, channel delivery, attempts, last_error, processing diagnostics |
| Audit | Ni public | Ni običajnega user/organizer dostopa | actor IDs, previous/new row, request/operation metadata; vedno sanitizirano in auditirano |
| Analytics | Agregati iz privacy-approved public tournament lifecycle podatkov | Personal/Hero self; protected Team member; private Tournament manager | Refresh, raw source diagnostika, global operational analytics |
| Storage | Objavljene avatar/team/tournament slike na public poti | Private download po dovoljenem object read scope-u; upload intent samo za profile ownerja, team Captain-a ali tournament managerja | Signed token, service key, notranji path/metadata, orphan cleanup |
| Auth/session | Ni public | Uporabnik lahko vidi le potrebno lastno session stanje | Gesla, refresh/access tokeni, Steam session secret/JTI, service keys, IP/security podatki |

Odločitev za 1.0 je konkretna: numeric SteamID64 in OpenDota account ID nista del generičnega public Profile DTO-ja. Morebitna prihodnja opt-in javna gaming identiteta zahteva namenski sanitiziran DTO in spremembo te pogodbe; trenutni whole-row ali široki ProfileResponse ni taka odločitev.

Public roster nikoli ne vključuje neaktivne membership zgodovine, invitee emailov, join-request sporočil ali manual-player notes. Public registration nikoli ne vključuje contact_email, application message, metadata ali internih reviewer polj.

## 12. Public API disclosure pravila

Canonical pravilo je **minimal disclosure**.

1. Public API uporablja namenski DTO/projection z allowlisto polj; ne vrača celotne DB vrstice.
2. Public read ne uporablja SELECT * in se ne zanaša na to, da občutljiv stolpec trenutno nima vrednosti.
3. Public tournament, structure, match in analytics podatki zahtevajo hkrati **is_public = true** in lifecycle status **registration, published, live ali finished**.
4. Draft, private in archived turnir ni public samo zato, ker je flag napačno nastavljen ali ker je povezana vrstica dosegljiva.
5. Soft-deleted/disbanded ekipe in neaktivna članstva niso public.
6. Child objekt ne more biti bolj javen od parent objekta. Registration, group, match, match_game, import status in analytics podedujejo public eligibility turnirja.
7. Raw JSON, metadata, notes, contact, auth, storage path, audit in diagnostics polja niso public.
8. Public analytics sme iz private source-a objaviti samo dovoljen agregat, ki ne razkrije prepovedanega individualnega ali operativnega podatka.
9. Public response je paginiran in podvržen abuse/rate omejitvam; odsotnost prijave ni odsotnost zaščite.
10. Supabase Data API base table ni public application API pogodba. Public application API je Spring Boot endpoint z minimalnim DTO-jem.

Globalna vloga, Steam/OpenDota ID in sync timestamps niso avtomatsko javni samo zato, ker so trenutno v istem ProfileResponse. Manual-player note ni javna samo zato, ker je trenutno del public-shaped team ali tournament response-a.

## 13. Storage in external account pravila

### 13.1 Canonical Storage lifecycle

1. Browser Springu pošlje namen: object type, file name, deklarirani MIME in size.
2. Spring preveri authentication, globalno vlogo in profile/team/tournament object capability.
3. Spring določi bucket in canonical path; browser ne izbira poljubnega patha ali owner ID-ja.
4. Spring izda kratkoživ signed upload za točen objekt in dovoljene headers.
5. Browser pošlje samo byte stream na izdani Storage URL.
6. Browser pokliče Spring confirm.
7. Spring ponovno avtorizira actorja ter preveri obstoj objekta, Storage owner/path, dejanski size, content type, dovoljeno končnico in magic bytes oziroma varno re-encoding politiko.
8. Spring šele nato transakcijsko zapiše path/URL v application tabelo.
9. Neconfirmirani/orphan objekti imajo idempotenten cleanup, ki ne izbriše referenciranih objektov.

Profilni avatar upravlja owner profila. Team logo/banner upravlja samo PLAYER s Captain capabilityjem te ekipe. Tournament media upravlja samo ORGANIZER z manage capabilityjem tega turnirja. ADMIN uporablja namenski backend tok.

Public avatar/logo/banner je po uspešnem confirmu lahko read-only PUBLIC asset. Zasebni media uporablja kratkoživ signed download. Poljuben external URL, URL drugega actorja ali neposreden zapis application URL/patha je prepovedan.

Legacy multipart in Storage path RLS, ki dovoljuje write brez backend-issued intenta, nista canonical toka. **MIGRATION REQUIRED — naloga 41.**

### 13.2 External accounts

- Link, unlink, primary-account sprememba in provider sync so poslovne mutacije prek Springa.
- Spring validira provider callback/identity in strežniško določi owner profile; client-supplied profile ID ni dokaz.
- Provider account mora spoštovati uniqueness in ownership invariant.
- Owner dobi samo polja, ki jih UI potrebuje za prikaz povezave in sync statusa.
- Credential, access/refresh token, session secret, hashed state, raw provider payload in notranja napaka se nikoli ne vrnejo public ali owner DTO-ju.
- ADMIN support dostop je namenski, minimalen in auditiran.
- Neposreden frontend SELECT ali write nad profile_external_accounts in private identity helper RPC ni dovoljen.

## 14. Analytics authorization principi

To poglavje določa varnostno mejo. Ne določa podrobne source/filter/lifecycle semantike naloge 03.

- **Personal Analytics:** samo self/private profile scope.
- **Hero Mastery:** samo self/private scope, dokler ta pogodba ne uvede ločenega opt-in public player analytics produkta.
- **Protected Team Analytics:** samo aktivni član konkretne ekipe; Captain ne dobi podatkov druge ekipe. Public Team Analytics vsebuje le agregate public-eligible tekem.
- **Tournament Analytics:** public endpoint samo za public-eligible lifecycle; manager endpoint samo za managed tournament; ADMIN globalno prek namenskega toka.
- **Organizer Analytics:** samo turnirji, za katere ima ORGANIZER dokazano manage capability.
- **Compare:** vsaka stran primerjave mora biti neodvisno dovoljena v istem public, self, team ali tournament kontekstu. Trenutno skupno članstvo ne sme avtomatsko odpreti private all-history drugega igralca.
- **Public Analytics:** samo podatki, dovoljeni z istim tournament lifecycle in privacy contractom kot public API. is_public brez statusa ni dovolj.
- Request filter, profileId, teamId ali tournamentId lahko scope samo zoži. Nikoli ga ne sme razširiti preko actorjeve osnovne authorization odločitve.
- Raw OpenDota payload, import event payload in operativna analytics diagnostika niso public analytics.

## 15. Known gaps

Spodnje vrzeli so potrjena neskladja trenutne implementacije s canonical modelom. Niso dovoljene izjeme.

| ID | Trenutna vrzel | Posledica | Rešuje |
|---|---|---|---|
| G-01 | Repo Supabase konfiguracija izpostavlja public schema Data API-ju, authenticated pa ima široke DML grante nad poslovnimi tabelami | V okolju s to Data API konfiguracijo lahko browser obide Spring validation, state machine in transakcijske invariante | **04 / #143** |
| G-02 | Frontend ima neposredne profiles SELECT fallbacke ter UPDATE/UPSERT fallback; homepage bere profile mimo Springa | Direct table pot ni na allowlisti, write pa obide business API | **04 / #143** za write; **06 / #145** za read/privacy |
| G-03 | Globalni organizer RLS helper se obnaša kot organizer-or-admin in lahko odpre tuje profile, external accounts, notifications, heroes, imports ter vse turnirje | Self-selected ORGANIZER dobi cross-user/cross-tournament DB scope | **05 / #144** |
| G-04 | Nekateri organizer backend route-i zahtevajo le authentication, manage lookup pa ne zahteva tudi globalnega ORGANIZER; staff relation lahko zato sama zadostuje. ADMIN ne deduje ROLE_PLAYER, zato so canonical admin override-i na PLAYER-only matcherjih trenutno nekonsistentni | Authorization ne uveljavi enotno zamrznjene kombinacije global role + capability in eksplicitnega admin scope-a | **05 / #144** in **10 / #149** |
| G-05 | Captain RLS helper ne zahteva hkrati globalnega PLAYER in aktivnega membershipa; write-i so team-row scoped, ne pa column/state/workflow scoped; registration insert lahko obide pending/review pravila | Captain lahko prek Data API obide zamrznjeni role + capability pogoj ter transfer, roster, capacity, disband ali registration workflow | **04 / #143** in **05 / #144** |
| G-06 | Frontend tipi/fallback še poznajo legacy globalni captain in lahko za prikaz izpeljejo role iz user metadata | Terminološki drift; client metadata bi lahko bila pomotoma obravnavana kot authority | **05 / #144** |
| G-07 | Public RLS vrača whole-row profile/team/tournament/registration podatke; ProfileResponse vključuje gaming ID-je, role in sync čase; manual-player note je public-shaped; neaktivna članstva ostanejo vidna | Kršitev minimal disclosure in field classification | **06 / #145** |
| G-08 | DB public tournament/read helperji in public analytics viewi uporabljajo predvsem is_public, ne canonical lifecycle; backend public analytics prav tako ne preveri vedno statusa | Draft ali drug nedovoljen lifecycle lahko uhaja prek Data API/analytics poti | **06 / #145** in **07 / #146** |
| G-09 | V34 authenticated vrne USAGE na private schema, starejše funkcije pa nimajo sistematičnega REVOKE EXECUTE FROM PUBLIC; SECURITY DEFINER analytics refresh je posebej občutljiv | Potrjena DB-role least-privilege vrzel; private ni izpostavljena PostgREST schema, zato to ni trditev o potrjenem remote RPC exploitu | **06 / #145** |
| G-10 | Import status/events lahko bere vsak authenticated; create/retry ni zanesljivo vezan na requesterja ali managed match/tournament | IDOR in cross-organizer import scope | **07 / #146** |
| G-11 | Compare dovoljuje protected podatke za trenutna teammate-a brez omejitve na konkretni team/tournament context | Private all-history drugega playerja se lahko razkrije zaradi samega članstva | **07 / #146** |
| G-12 | Steam cookie ima production-nevarne defaulte/fallback secret, ni server-side revocation; frontend logout ne pokliče tudi Steam logouta; CSRF je izklopljen | Seja lahko ostane veljavna, cookie mutacije in production konfiguracija niso fail-closed | **08 / #147** |
| G-13 | Proxy IP headerjem se zaupa brez trusted-proxy meje, limiter pa je in-memory/per-instance | Spoofing ali več instanc lahko obide abuse omejitve | **08 / #147** |
| G-14 | Legacy multipart in public bucket tokovi sobivajo s signed tokom; Storage RLS dovoljuje direct path write; confirm ne preveri dejanskega objekta/MIME/magic bytes; ni celovitega cleanup-a | Lažen DB link, spoofan ali orphan objekt ter bypass backend-issued intenta | **41 / #180 — MIGRATION REQUIRED** |
| G-15 | post_flyway_hardening.sql utrdi samo flyway_schema_history in se izvaja ročno | Ne zapre aplikacijskih grantov, RLS ali function ACL vrzeli | **04 / #143**, **05 / #144**, **06 / #145** |

## 16. Follow-up roadmap tasks

Ta dokument ne pušča odprte vloge, privacy razreda ali trust-boundary vprašanja, ki bi blokiralo naloge 03–17. Nadaljnje naloge implementirajo zamrznjeno pogodbo:

| Roadmap | Odgovornost po tej pogodbi |
|---|---|
| **03 / #142 — Zamrzni analytics source in scope pogodbo** | Določi source/filter/lifecycle podrobnosti znotraj authorization meja poglavja 14 |
| **04 / #143 — Zapri neposredne Supabase poslovne write poti** | Odvzame client DML, odstrani frontend write fallbacke in vzpostavi backend-only mutations |
| **05 / #144 — Utrdi Organizer in Captain RLS pravila** | Uveljavi ORGANIZER + tournament capability ter PLAYER + team Captain capability brez cross-object pravic |
| **06 / #145 — Utrdi public privacy in DB funkcijske grante** | Minimalni public DTO/viewi, lifecycle filtri, skrita občutljiva polja in least-privilege private function ACL |
| **07 / #146 — Utrdi import in analytics object scope** | Requester/manager import scope, varni retry/events in Compare brez teammate all-history |
| **08 / #147 — Utrdi Steam session, logout, CSRF in rate limiting** | Fail-closed cookie/session lifecycle, enoten logout, CSRF/origin, trusted proxy in distributed limiter |
| **10 / #149 — Dodaj security regression teste** | Pozitivni in negativni testi za frozen role/capability/privacy pogodbo |
| **15 / #154 — Konsolidiraj canonical tournament in match API pogodbo** | Konkretni endpoint/state prehodi, vključno z registration cancellation, brez razširitve tukaj določenega scope-a |
| **41 / #180 — Konsolidiraj in utrdi storage upload pipeline** | En signed upload/confirm/download lifecycle, content verification in orphan cleanup |

Nobena follow-up naloga ne sme ponovno uvesti CAPTAIN kot globalne vloge, dodati REFEREE/ANALYST v DotaOps 1.0, spremeniti RLS v nadomestilo za Spring authorization ali razširiti neposredni Supabase allowlist brez izrecne spremembe tega canonical dokumenta.
