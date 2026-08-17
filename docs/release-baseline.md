# DotaOps 1.0 Release Baseline and Branch Strategy

Stanje v tem dokumentu je bilo preverjeno 17. avgusta 2026 po `git fetch origin --prune`. Dokument je canonical vir za DotaOps release baseline, strategijo vej in sledljivost deploymentov. Za vloge, privacy model in trust boundary ostaja canonical vir [security-and-role-model.md](security-and-role-model.md).

## 1. Namen

Ta dokument določa:

- začetni, ponovljiv implementation baseline za roadmap DotaOps 1.0;
- trenutno razmerje med `development`, `main` in PR #139;
- preprosto pravilo za integracijo in release;
- minimalen dokaz, ki ga mora pustiti vsak pomemben deployment;
- ločevanje development, demo, staging in production okolij;
- osnovne release gate-e in trenutno znane vrzeli.

Baseline ni production release, release tag ali dokaz trenutnega live stanja. Je sledljiva začetna točka, od katere se izvajajo roadmap naloge 02–52.

## 2. DotaOps 1.0 implementation baseline

```text
DotaOps 1.0 implementation baseline:
7db45f58327fc0fcf5367b0fbf4164069d2a1df0

Short SHA:
7db45f5

Branch:
development

Commit date:
2026-08-17T11:49:44+02:00

Commit message:
docs(security): define roles privacy and trust boundary
```

Ta commit je ob preverjanju hkrati lokalni `development`, `origin/development` in head PR #139. Vključuje zaključeno architecture/trust-boundary nalogo [#140](https://github.com/DotaOps/DotaOps/issues/140), za njim pa na `origin/development` ni bilo novejših commitov. Zato je znana in reproducibilna začetna točka novega roadmapa.

`security-and-role-model.md` ločeno beleži SHA kode, nad katero je bil izveden njegov audit. Ta audit input in tukaj določen implementation baseline, ki že vsebuje rezultat #140, sta različna namena in nista nasprotujoča si release podatka.

## 3. Trenutno stanje vej

Remote stanje, ki je relevantno za release primerjavo:

| Ref | Full SHA | Opomba |
| --- | --- | --- |
| `origin/development` | `7db45f58327fc0fcf5367b0fbf4164069d2a1df0` | trenutni DotaOps 1.0 implementation baseline |
| `origin/main` | `7c1942fde99efe643b8e737064e6425e0e78582a` | trenutni remote release branch head |

`origin/main...origin/development` ima razmerje `0 26`: `origin/main` nima nobenega commita, ki ga `origin/development` ne vsebuje, `origin/development` pa ima 26 dodatnih commitov. `origin/main` je prednik trenutnega `origin/development`.

Lokalno stanje ob preverjanju:

- lokalni `development` je usklajen z `origin/development`;
- lokalni `main` je na `2c8eff9aee49f19b8bf23e845e4067722fb9d2eb` in je 23 commitov za `origin/main`;
- zato se lokalni `main` ne sme uporabljati kot dokaz trenutnega remote `main` stanja, dokler ga uporabnik zavestno ne uskladi v ločenem Git koraku.

Pomembni sklopi, ki so trenutno samo na `development`, so Personal Analytics, Hero Mastery, Player Compare, context weighting in normalizirani analytics vpogledi, analytics filtri in URL state, Cypress smoke/E2E osnova, demo/QA izboljšave, Sonar konfiguracija ter dodatna API, Supabase, security in druga dokumentacija.

## 4. PR #139

[PR #139](https://github.com/DotaOps/DotaOps/pull/139) je odprt integration PR `development` → `main` z naslovom **Integracija development v main: osebna analitika, Hero Mastery, Compare in E2E**.

Snapshot 17. avgusta 2026:

- base: `main` @ `7c1942fde99efe643b8e737064e6425e0e78582a`;
- head: `development` @ `7db45f58327fc0fcf5367b0fbf4164069d2a1df0`;
- 26 commitov in 126 spremenjenih datotek;
- diff: +14.370 / -1.117;
- GitHub stanje: `OPEN`, `MERGEABLE`, vendar `UNSTABLE`; PR ni mergean;
- automatic closing issue references: ni jih.

Dejanski obseg vključuje:

- Personal Analytics backend endpoint-e in frontend orkestracijo;
- Hero Mastery backend model/API in frontend prikaze;
- Player Compare lookup/autocomplete, primerjalne metrike, shared-hero in enriched match-history poglede;
- context weighting, Context Insights in normalizirane analytics metrike;
- analytics filtre in URL state;
- Cypress konfiguracijo ter public, auth, protected-route in organizer smoke/E2E scenarije;
- demo seed/reset/verify ter generated/integration-test cleanup podporo;
- backend teste, Postgres integration/migration-smoke podporo ter demo/QA izboljšave;
- Sonar workflow/config in povezano API, backend, Supabase, Sonar ter security dokumentacijo.

PR ne spreminja Flyway migracij. Demo SQL in migration-smoke/test podpora se zato ne smeta opisovati kot nova produkcijska migracija.

Stanje glavnih preverjanj za head SHA `7db45f58327fc0fcf5367b0fbf4164069d2a1df0`:

- **Backend test, build and migration smoke:** uspešno; GitHub PR run je izvedel backend teste, package build in Postgres integration teste.
- **Frontend checks:** uspešno; izvedeni so bili install, lint, typecheck in production build. Cypress/E2E v tem workflowu ni bil izveden.
- **GitHub Actions SonarQube analysis:** job je prikazan kot uspešen, vendar je bil dejanski scan preskočen, ker `SONAR_TOKEN` in `SONAR_HOST_URL` nista bila konfigurirana. To ni uspešen quality scan.
- **SonarCloud Code Analysis:** neuspešno; Quality Gate je padel zaradi enega Security Hotspota in varnostne ocene nove kode `C`, zahtevana pa je najmanj `A`.

**Odločitev A — PR #139 ostane odprt release kandidat.** `origin/main` je čisti prednik `origin/development`, GitHub ne zazna merge konflikta in ni konkretnega tehničnega razloga za split ali supersede. PR se ne mergea, dokler niso razrešeni SonarCloud blocker, dejansko izvajanje zahtevanega quality scana in ostali relevantni release gate-i.

Roadmap kontekst: [#140](https://github.com/DotaOps/DotaOps/issues/140) je zaključil canonical architecture/trust-boundary dokument, [#141](https://github.com/DotaOps/DotaOps/issues/141) določa ta release baseline, [#142](https://github.com/DotaOps/DotaOps/issues/142) pa bo v naslednjem koraku zamrznil analytics source/scope. Security in correctness naloge morajo biti obravnavane pred dejanskim production releasom.

## 5. Branch strategy

Canonical tok je:

```text
feature/task work
→ development
→ release validation in relevantni release gate-i
→ reviewed development-to-main integration
→ main
→ production deployment, vezan na točen commit ali release tag
```

- `development` je integracijska veja za roadmap development. Preverjene roadmap spremembe se commitajo in pushajo nanjo.
- `main` predstavlja preverjeno release stanje. Ne posodablja se za vsak posamezen development task.
- Integracija `development` → `main` se izvede šele, ko so izpolnjeni relevantni release gate-i in je znan točen head SHA kandidata.
- Production izvira iz preverjenega `main` commita ali iz eksplicitnega release taga, ki kaže na tak commit.
- Ime veje samo po sebi ni deployment dokaz. Zapis »deployed development« ali »deployed main« ni dovolj.

## 6. Deployment traceability

Vsak pomemben deployment mora pustiti trajen zapis z najmanj naslednjimi polji:

```text
environment: <demo|staging|production>
component: <frontend|backend|database|drugo>
branch-or-tag: <branch ali release tag>
commit: <full 40-character Git SHA>
deployment timestamp: <ISO-8601 z timezone ali UTC>
status: <started|succeeded|failed|rolled-back>
workflow/deployment record: <GitHub Actions run ali deployment URL>
endpoint: <deployed URL, kjer je primerno>
```

Za večkomponentni release mora biti razvidno, ali frontend, backend in baza izvirajo iz istega release kandidata. Za bazo se poleg Git SHA zabeleži tudi apliciran migration/schema baseline. Ponovitev ali rollback deploymenta mora ohraniti povezavo do prvotnega in nadomestnega SHA.

Prednostni dokaz je GitHub Environment/Deployment zapis, ustvarjen iz GitHub Actions runa. Dokler tega mehanizma ni, je odsotnost dokaza **KNOWN GAP**, ne dovoljenje za ugibanje commita.

## 7. Trenutni live deployment

README javni frontend označuje kot production deployment na [https://dotaops-frontend.vercel.app/](https://dotaops-frontend.vercel.app/). Ob preverjanju 17. avgusta 2026 je URL vrnil HTTP `200` in Vercel/Next.js odziv.

Vendar:

- GitHub Deployments API za repozitorij ne vrne nobenega deployment zapisa;
- repozitorij nima konfiguriranega GitHub Environment zapisa;
- ni GitHub deployment workflowa, releasea ali taga, ki bi javni URL povezal s SHA;
- javni odziv ne izpostavi Git commit SHA;
- zapis v `supabase/README.md`, da je Supabase production branch `main`, ne dokazuje SHA trenutno servirane frontend, backend ali database različice.

```text
Live deployment commit: NEPREVERLJIV
Live deployment branch/tag: NEPREVERLJIV
Enak trenutnemu development: NEPREVERLJIVO
Enak trenutnemu main: NEPREVERLJIVO
```

Dosegljivost URL-ja je verificirana; identiteta kode in usklajenost komponent nista.

## 8. Environment separation

| Okolje | Namen in pravilo |
| --- | --- |
| Development | Integracija roadmap sprememb. Lahko uporablja lokalne ali izolirane generated/test podatke; ne predstavlja javnega production stanja. |
| Demo | Ponovljiv prikaz s čistim, sintetičnim demo datasetom. Ne vsebuje integracijskih testnih artefaktov ali production osebnih podatkov. |
| Staging | Izolirano, production-like preverjanje konkretnega release SHA z ločenimi secrets, storageom in podatki. Ne piše v production podatke. |
| Production | Javno, nadzorovano okolje iz preverjenega `main` commita/release taga. Ne sprejema demo ali integration-test seeda in mora imeti deployment, backup/restore ter rollback evidence. |

Okolja morajo imeti ločene konfiguracije, credentials/secrets, podatkovne zbirke oziroma sheme in storage meje. Prehod med okolji pomeni promocijo preverjenega artefakta/commita, ne kopiranja testnih podatkov v production.

## 9. Demo/test data policy

Generated/integration-test zapisi ne sodijo v javni demo ali production dataset. Prejšnji audit je v javnih podatkih zaznal takšne artefakte; v nalogi #141 niso bili brisani in gostovana baza ni bila spremenjena.

Ob ponovnem read-only preverjanju 17. avgusta 2026 je javni `/turnirji` prikazal na primer `Settings Audit Cup 9159c37b1858`, `Group Standings d615a944f4c4`, `Bracket Generation f3a7036f54a9` in `Match Management 255aa7c577cd`. Vzorci imen in 12-hex končnice se ujemajo z generatorji integracijskih testov ter dokumentiranim cleanup obsegom. To je evidence o trenutni vrzeli, ne dovoljenje za samodejno brisanje gostovanih podatkov.

Pravila:

- integration testi uporabljajo lokalno ali namensko izolirano testno okolje in po izvedbi očistijo lastne podatke;
- demo uporablja samo dokumentiran sintetični dataset in ponovljiv reset/verify postopek;
- staging uporablja ločen, nadzorovan testni dataset brez production osebnih podatkov;
- production ne uporablja demo seeda, testnih računov ali generated/integration-test artefaktov;
- pred Demo, Beta ali Production releasom se izvede evidence-backed pregled in odobreno čiščenje oziroma izolacija podatkov;
- cleanup gostovanih podatkov se ne izvaja brez točno določenega targeta, backup/restore načrta in izrecne odobritve.

Repozitorij že vsebuje lokalne demo/reset pripomočke, opisane v [backend-demo-seed.md](backend-demo-seed.md), vendar njihov obstoj ni dokaz, da je javno okolje čisto. Operativna ločitev in čiščenje sta del taska 47 / [#30](https://github.com/DotaOps/DotaOps/issues/30) ter release nalog 49 / [#185](https://github.com/DotaOps/DotaOps/issues/185), 50 / [#186](https://github.com/DotaOps/DotaOps/issues/186) in 51 / [#187](https://github.com/DotaOps/DotaOps/issues/187).

## 10. Release gates

Pred integracijo v `main` se za točen kandidat SHA preveri najmanj:

- obvezni GitHub checks so zaključeni in uspešni; navidezno zelen preskočen scan se ne šteje kot izveden check;
- backend testi/build in Postgres migration/integration smoke so uspešni;
- frontend lint, typecheck in production build so uspešni;
- relevantni Cypress/E2E in release scenariji so dejansko izvedeni, ne samo prisotni v repozitoriju;
- Flyway/migration pot je preverjena na čisti bazi in znan je schema baseline;
- security/privacy/trust-boundary in analytics correctness blockerji so odpravljeni ali eksplicitno sprejeti z lastnikom in rokom;
- SonarCloud Quality Gate oziroma dogovorjeni nadomestni quality gate je uspešen;
- opravljena sta code review in pregled odprtih known blockerjev;
- demo/staging/production data policy je preverjena za ciljno okolje;
- release kandidat ima release opombe in dokazljiv deployment/rollback plan.

Pred production deploymentom so dodatno potrebni staging dokaz za isti SHA, odobritev releasa, backup/restore pripravljenost ter production deployment record. Infrastrukturne podrobnosti bodo določene v tasku 47 / [#30](https://github.com/DotaOps/DotaOps/issues/30).

## 11. Known gaps

| Known gap | Posledica | Roadmap povezava |
| --- | --- | --- |
| Javni deployment nima preverljivega SHA, GitHub Environment ali Deployment zapisa. | Live stanja ni mogoče dokazljivo primerjati z `main` ali `development`. | 47 / [#30](https://github.com/DotaOps/DotaOps/issues/30) |
| Staging, CD, production runtime evidence ter backup/restore niso vzpostavljeni. | Varna promocija in rollback release kandidata nista dokazljiva. | 47 / [#30](https://github.com/DotaOps/DotaOps/issues/30) |
| Generated/integration-test artefakti so bili opaženi v javnih podatkih; čiščenje ni bilo izvedeno v #141. | Javni demo/production dataset ni potrjeno čist. | 47 / [#30](https://github.com/DotaOps/DotaOps/issues/30), 49 / [#185](https://github.com/DotaOps/DotaOps/issues/185), 50 / [#186](https://github.com/DotaOps/DotaOps/issues/186), 51 / [#187](https://github.com/DotaOps/DotaOps/issues/187) |
| SonarCloud Quality Gate za PR #139 je neuspešen; ločeni SonarQube workflow scan je preskočen. | PR je `UNSTABLE` in ni pripravljen za merge/release. | [#141](https://github.com/DotaOps/DotaOps/issues/141); CI/CD konfiguracija v 47 / [#30](https://github.com/DotaOps/DotaOps/issues/30) |
| Cypress scenariji obstajajo, vendar jih trenutni frontend CI ne izvaja. | E2E release gate trenutno nima avtomatskega dokaza. | 45 / [#28](https://github.com/DotaOps/DotaOps/issues/28) |
| Workflow README in konceptualni deployment diagram ne opisujeta zanesljivo trenutnih CI/deployment dokazov. | Operativna dokumentacija lahko ustvari napačen vtis o CD in okolju. | 48 / [#32](https://github.com/DotaOps/DotaOps/issues/32) |
| Analytics source/scope še ni canonical zamrznjen. | Analytics correctness še ni potrjen release input. | 03 / [#142](https://github.com/DotaOps/DotaOps/issues/142) |
| Demo, Beta in Production release še nimajo končnega evidence paketa. | DotaOps 1.0 še ni production release. | 49 / [#185](https://github.com/DotaOps/DotaOps/issues/185), 50 / [#186](https://github.com/DotaOps/DotaOps/issues/186), 51 / [#187](https://github.com/DotaOps/DotaOps/issues/187) |
