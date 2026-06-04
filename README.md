# MediTrack

Internt verktyg för läkemedelshantering och beställningsflöden på svenska vårdenheter. Bygger ut den manuella e-post-och-Excel-processen till ett spårbart system med statusflöde, varningar för låga lager och en datamodell som speglar verkliga relationer.

Byggt som case för Medovia under perioden 27 maj – 4 juni 2026.

## Snabbstart

Förutsättningar:
- Java 21
- Node.js 20+
- Docker Desktop (för MySQL-instansen)
- Maven 3.9+ (eller använd den medföljande `./mvnw`)

### 0. Klona projektet

```bash
git clone https://github.com/fredrik-arvidsson/meditrack.git
cd meditrack
```

### 1. Starta databasen

```bash
docker compose up -d
```

Detta startar en MySQL 8.4-container på port `3307`. Containern heter `meditrack-mysql` och persisterar data i en namngiven Docker-volym.

### 2. Starta backend

```bash
./mvnw spring-boot:run
```

Spring Boot startar på port `8080`. Vid första uppstarten kör Flyway migrationerna (`V1__init.sql`) och seed-skriptet (`R__seed.sql`), så databasen är direkt fylld med två vårdenheter, tre användare (en per roll), sju läkemedel, sju lagerposter och två beställningar.

#### Valfritt: aktivera AI-agenten

Påfyllningsagenten (se avsnitt 6) använder Googles Gemini. Den fungerar **utan** nyckel — då faller den tillbaka på en deterministisk regel. Vill du köra mot riktig Gemini, sätt två miljövariabler innan du startar backend:

```bash
export GEMINI_API_KEY="din-nyckel"
export GEMINI_MODEL="gemini-2.5-flash"
./mvnw spring-boot:run
```

Nyckel skapas gratis på [aistudio.google.com/apikey](https://aistudio.google.com/apikey). Saknas nyckeln startar och fungerar appen ändå — agenten märker bara sina förslag som "Regelbaserad fallback" istället för "Gemini".

### 3. Starta frontend

I ett separat terminalfönster:

```bash
cd frontend
npm install
npm run dev
```

Vite startar på port `5173`. Öppna [http://localhost:5173](http://localhost:5173) i webbläsaren — appen möter dig med en inloggningssida.

### Demo-inloggningar

Tre seed-användare, en per roll. Alla har lösenordet `demo1234`:

| E-post | Roll |
|--------|------|
| `anna.lindberg@meditrack.demo` | ADMIN |
| `erik.svensson@meditrack.demo` | PHARMACIST |
| `sara.johansson@meditrack.demo` | NURSE |

Rollen styr vad du får göra — bara en apotekare (PHARMACIST) kan bekräfta en beställning (se Verifiera).

### 4. Verifiera

Logga in (t.ex. `sara.johansson@meditrack.demo` / `demo1234`). Frontend ska visa sju läkemedel i en tabell (Morfin är märkt som narkotika). Sök på "para" eller välj en form i filtret ovanför tabellen för att smalna av listan. Klicka på "Lager" — sju lagerposter, två markerade under tröskel (Paracetamol och Salbutamol). Klicka på "Beställningar": ORD-DEMO0001 är **Skickad**, ORD-DEMO0002 är **Levererad**.

Öppna "Visa detaljer" på ORD-DEMO0001. Som **NURSE** (Sara) ser du *ingen* Bekräfta-knapp — det är rolldöljningen i UI:t. Ladda om sidan för att logga ut (credentials ligger i minnet, så en omladdning återgår till inloggning), och logga in som `erik.svensson@meditrack.demo` (PHARMACIST). Öppna samma order: nu finns Bekräfta. Klicka "Bekräfta", sedan "Leverera", och se status, tidslinje och lagersaldo uppdateras live (leveransen ökar saldot för Amoxicillin och Natriumklorid).

Backend genomdriver rollen oavsett vad UI:t visar: ett anrop mot bekräfta-endpointen som NURSE avvisas med 403, även om knappen skulle tvingas fram. Och separation of duties gäller på personnivå — den som skickat en beställning kan inte bekräfta den ens som apotekare.

Vill du se AI-agenten: gå till "Lager" och klicka "Föreslå påfyllning (AI)". Ett DRAFT-utkast skapas med föreslagna kvantiteter för de läkemedel som ligger under tröskel — för granskning, inte automatisk beställning.

### Stoppa allt

- Backend och frontend: `Ctrl+C` i respektive terminal
- Databas: `docker compose down` (data bevaras) eller `docker compose down -v` (data raderas)

### Återställ demo-data

Demo-flödet muterar databasen (statusövergångar, lagersaldon, AI-utkast). För att nollställa till utgångsläget:

```bash
docker compose down -v && docker compose up -d
```

`-v` raderar volymen så att Flyway kör om `R__seed.sql` från rent tillstånd vid nästa backend-start.

## 3. Arkitektur

MediTrack är en lagerindelad modulär monolit: en Spring Boot-backend som exponerar ett REST-API, och en fristående React-SPA som konsumerar det. Backend följer ett klassiskt skiktmönster där varje lager bara känner till lagret under sig — request flödar uppifrån och ner, entiteter lämnar aldrig service-lagret (allt över repository talar DTO:er).

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (Vite + React + TS)        :5173                │
│  login · medications · stock · orders · order-detail      │
│  apiFetch (Basic auth) → useFetch → JSON                  │
└───────────────────────────┬─────────────────────────────┘
                            │  HTTP / JSON  (CORS, Basic auth)
                            ▼
┌─────────────────────────────────────────────────────────┐
│  Backend (Spring Boot 3.4.5, Java 21)   :8080            │
│                                                           │
│   controller   tunna REST-endpoints, DTO in/ut           │
│       │        @PreAuthorize (roll) + global ApiError     │
│       ▼                                                   │
│   service      affärslogik, @Transactional,               │
│       │        state machine, lås, separation of duties   │
│       ▼                                                   │
│   repository   Spring Data JPA, tenant-scopade queries    │
│       │                                                   │
│       ▼                                                   │
│   entity       JPA-mappning, Auditable, @Version          │
│                                                           │
│   ai           reorder-agent (Gemini + regel-fallback)    │
│   security     Spring Security, UserDetailsService, RBAC  │
│   tvärgående:  dto · enums · exception · config           │
└───────────────────────────┬─────────────────────────────┘
                            │  JDBC (HikariCP)
                            ▼
┌─────────────────────────────────────────────────────────┐
│  MySQL 8.4 (Docker)    :3307                              │
│  Flyway: V1__init.sql (schema) · R__seed.sql (demo-data)  │
└─────────────────────────────────────────────────────────┘
```

Paketen speglar lagren rakt av: `controller` tar emot HTTP och delegerar direkt till `service`, som äger all affärslogik och är det enda lagret som rör `repository`. `entity` är JPA-mappningen; `dto` är API-kontraktet (records, frikopplat från entiteterna). `ai` är ett avgränsat hörn för påfyllningsagenten; `security` håller Spring Security-konfig, `UserDetailsService` och rollgenomdrivning. Tvärgående paket — `enums`, `exception` (global felöversättning), `config` (JPA-auditing m.m.) — stödjer alla lager utan att höra hemma i något särskilt.

Migrationer ägs av Flyway, inte Hibernate: `ddl-auto: validate` betyder att Hibernate jämför entiteterna mot schemat vid uppstart och kraschar vid avvikelse, men aldrig ändrar databasen själv. Schemat har en sanningskälla.

### Varför modulär monolit, inte microservices

Ett medvetet val, inte en eftergift. Microservices löser organisatoriska och skalningsproblem MediTrack inte har: oberoende deploys, skalning per tjänst, teamautonomi över tjänstegränser. Priset — nätverksanrop mellan tjänster, distribuerade transaktioner, eventuell konsistens, separat infrastruktur per tjänst — skulle dränera veckan på plumbing istället för domänen.

Det avgörande argumentet är concurrency-lösningen: den atomiska leveransen som uppdaterar order, lagersaldo och lagerrörelse i *en* transaktion med pessimistisk låsning (`SELECT ... FOR UPDATE`) bygger på att allt lever i samma databas. Delas order och lager i separata tjänster försvinner den garantin och ersätts av distribuerad samordning (saga + kompensering). Monoliten är inte en kompromiss här — den är det som gör den korrekta lösningen möjlig.

"Modulär" bär framtiden: lagerindelningen och tenant-isoleringen är organiserade så att en modul kan brytas ut om skalan en dag kräver det. Bygg det enkla som löser dagens problem, men lämna morgondagens dörr öppen. Vad som faktiskt skulle förändras vid 50 vårdenheter behandlas i avsnitt 8.

## 4. Stack & motiveringar

Hela stacken är vald medvetet, inte default. Motiveringar i korthet:

| Val | Varför |
|-----|--------|
| **Java 21 + Spring Boot 3.4.5** | LTS (support till 2031). Stacken sitter djupt sedan tidigare projekt → tiden läggs på domänen, inte på att lära verktyg. |
| **MySQL 8.4** | Senaste LTS. Hemmaplan — beprövade Flyway- och Testcontainers-mönster. Se nedan. |
| **Flyway** | Versionshanterade, immutabla migrationer = spårbart schema (kritiskt i vård). Enda sanningskällan; Hibernate kör `validate`, aldrig `ddl-auto: update`. |
| **Hibernate/JPA** | Mogen ORM, integrerar sömlöst med Spring Data. |
| **Spring Security** | Med från dag 1, inte bolt-on. HTTP Basic mot användare i databasen, RBAC genomdrivet per endpoint. RBAC är regulatoriskt krav (HSLF-FS 2017:37, separation of duties). |
| **Testcontainers** | Integrationstester mot riktig MySQL, inte H2 — H2 ljuger om dialektspecifika saker (JSON, CHECK, FK, radlås). |
| **Lombok** | Mindre boilerplate i entiteterna; exkluderad ur slutgiltig JAR. |
| **Vite + React + TypeScript** | Caset namnger React/TS. SPA mot rent JSON-API. Se nedan. |
| **Tailwind v3** | Utility-klasser direkt i JSX, ingen ramverksinlärning. v3 (inte v4) för stabil, väldokumenterad tooling. |
| **Egen `useFetch`-hook + `apiFetch`** | För fyra sidor är TanStack Query/SWR overkill. En central `apiFetch` lägger på Basic auth och hanterar 401/403; `useFetch` täcker läsningarna. Byts vid behov när appen växer. |
| **Gemini (AI-agent)** | Medovias egen LLM — samma logik som React-valet: bygg i den stack ni faktiskt använder. Bakom ett interface, med deterministisk fallback. |

### MySQL framför Postgres

Ursprungsplanen föreslog Postgres — jag bytte medvetet till MySQL. Skälet är hemmaplansfördel: beprövade Flyway-konventioner, `@Column`-mappningar och Testcontainers-uppsättning, vilket undviker en hel klass av nybörjarfel (JSONB-syntax, sekvenshantering, `BIGSERIAL` vs `IDENTITY`). Caset ställer inga databaskrav.

Trade-offen är ärlig: Postgres JSONB är vassare än MySQL JSON för audit-detaljer, och Postgres är vanligare i ny svensk health-tech. Slutsatsen blev att hemmaplansfördelen vägde tyngst för en veckas leverans, och MySQL JSON räcker gott för audit-loggens metadata.

### React framför Thymeleaf

Min huvudsakliga frontend-erfarenhet ligger på server-renderade vyer (Thymeleaf), som hade varit snabbast för mig att leverera. Jag valde ändå React, av två skäl. Backend är redan designad som ett rent, stateless JSON-API (ingen session, CSRF avstängt med motivering) — Thymeleaf hade krävt att de valen omarbetades för noll teknisk vinst. Och caset namnger uttryckligen TypeScript och React. Att leverera Thymeleaf vore att aktivt välja bort signalen om att jag kan jobba i Medovias stack. Ett medvetet val, inte en default.

## 5. Datamodell

Åtta entiteter som speglar verkliga relationer i läkemedelslogistiken. Fullständig modell med alla fält, constraints och designresonemang finns i [`DATAMODELL.md`](./DATAMODELL.md); här är överblicken.

```
CareUnit (vårdenhet, tenant-rot)
   │
   ├──< Medication (läkemedelskatalog per enhet)
   │        │
   │        └──< StockItem (saldo + threshold per enhet)
   │
   ├──< Order (beställning, state machine)
   │        │
   │        └──< OrderLine (rad: läkemedel + kvantitet)
   │
   ├──< User (roll + enhet)
   │
   ├──< StockMovement (lagerrörelse, immutabel)
   │
   └──< AuditLog (spårbarhet, immutabel)
```

Centrala designval:

- **CareUnit är tenant-roten.** Varje domänentitet bär `care_unit_id` — tenant-isolering från första migrationen (Patientdatalagen). CareUnit *är* tenanten och har därför ingen egen tenant-FK.
- **Medication är katalog per enhet, inte global.** En akutmottagning och ett rehab-boende har olika sortiment; PDL-isolering kräver dessutom att data inte delas mellan enheter.
- **StockItem är skild från Medication.** Saldo är operativ data som ändras ofta; läkemedlet är katalogdata som ändras sällan. `threshold` (varningsnivå för lågt lager) sitter på StockItem eftersom den beror på enhetens förbrukning.
- **OrderLine pekar på Medication, inte StockItem.** En beställning gäller *att skaffa ett läkemedel*; rätt lagerpost löses vid leverans via `(medication_id, care_unit_id)`.
- **StockMovement och AuditLog är immutabla.** Historikrader ändras aldrig — det är poängen med spårbarhet. StockMovement bär `quantity_after`, så saldot vid en given tidpunkt kan besvaras utan att spela upp hela historiken.
- **`@Version` (optimistisk låsning) på Order och StockItem.** Hanterar samtidiga uppdateringar; vid den saldokritiska leveransen kompletteras det med pessimistisk låsning (se avsnitt 6).
- **Soft-delete + anonymisering på User.** `deleted_at` + `anonymized_at` istället för hård radering (GDPR art. 17, samtidigt som referensintegritet till historik bevaras).
- **`controlled_substance` på Medication.** Flaggar narkotika (HSLF-FS 2017:37), designat för framtida dubbelsignering.

Affärsreglerna — state machine, lagerlogik, threshold-beräkning och RBAC — beskrivs i [`AFFARSREGLER.md`](./AFFARSREGLER.md). En kortare sammanfattning av de viktigaste tekniska valen finns i [`DESIGNBESLUT.md`](./DESIGNBESLUT.md).

## 6. Funktioner

### Kärnfunktioner (obligatoriska enligt caset)

**Läkemedelsregister**
- Lista läkemedel med namn, ATC-kod, form, styrka och aktuellt lagersaldo
- Lägg till, redigera och ta bort läkemedel (radering är soft-delete — `active = false` — eftersom läkemedel med historik aldrig hård-raderas; listan filtrerar bort inaktiva)
- Sök på namn och ATC-kod samt filtrera på form — allt serverside via en JPQL-query med valfria parametrar, så det skalar med antalet poster istället för att filtrera i minnet

**Beställningsflöde**
- Skapa beställning med en eller flera rader (läkemedel + kvantitet)
- Statusflöde som state machine: Utkast → Skickad → Bekräftad → Levererad, plus Avbruten från icke-terminala lägen
- Övergångar kan inte hoppa över steg; statusen sätts alltid serverside, aldrig från klienten
- Beställningshistorik per vårdenhet, med tidsstämpel och aktör för varje övergång

**Lagerlogik**
- Lagersaldo uppdateras automatiskt vid leverans (domänen är inköpsbeställning — saldo *ökar* när varan kommer in)
- Saldo ändras endast via lagerrörelser (StockMovement), aldrig genom direkt skrivning — varje förändring lämnar ett spår
- Varning när ett läkemedel understiger sin definierade tröskelnivå, beräknat vid hämtning (ingen cachad flagga som kan glida isär)

### Valbara utökningar (byggda)

- **Rollbaserad åtkomst (RBAC) med separation of duties.** Autentisering via HTTP Basic mot användare i databasen (BCrypt-hashade lösenord, ingen hash exponeras). Tre roller (NURSE, PHARMACIST, ADMIN), genomdrivna i två lager: `@PreAuthorize` på controllern (rollkrav — bara PHARMACIST får bekräfta) och ett eget domänundantag i service-lagret (separation of duties på personnivå — den som skickat en beställning får inte själv bekräfta den, även som apotekare). Båda ger 403. Frontend döljer åtgärder rollen inte får utföra, men det är enbart UX — backend genomdriver oavsett vad klienten visar. `/api/me` exponerar inloggad användares roll till frontend.
- **AI-driven påfyllningsagent.** Läser lagerposter under tröskel och föreslår påfyllningskvantiteter via Gemini (Medovias LLM). Agenten skapar ett **DRAFT-utkast** som en människa granskar och skickar — den beslutar aldrig själv. Det är separation of duties applicerat på AI: precis som en sjuksköterska inte får bekräfta sin egen beställning, får agenten inte verkställa sitt eget förslag. Bakom ett interface med en deterministisk regel-fallback, så appen fungerar med eller utan Gemini. Vilka läkemedel som är lågt lager och deras saldon kommer alltid från databasen — modellen föreslår bara kvantiteter, den kan inte hitta på vad som finns.
- **Audit log.** Immutabel logg över vem som gjorde vad och när, med flexibel JSON-kontext per händelse — kritiskt i sjukvårdskontext, designat för både skriv- och läsloggning.
- **Tenant-isolering.** Systemet är multi-tenant från grunden: varje vårdenhet ser bara sin egen data, även för admin-rollen. Seed-datan innehåller en avsiktligt tom andra enhet för att demonstrera att isoleringen håller.

### Medvetet utelämnat

E-post-/in-app-notiser och CSV/PDF-export är specificerade som valbara i caset och inte byggda — prioriteringen lades på en solid, väl motiverad kärna plus de utökningar som bäst speglar Medovias riktning (AI) och domänens krav (audit, tenant-isolering, RBAC). Vad som skulle tillkomma och varför står i avsnitt 8.

## 7. Kända brister

Ärlig självkritik — det här är vad jag är minst nöjd med och medveten om.

- **Autentiseringen är medvetet enkel.** HTTP Basic mot användare i databasen — rätt nivå för ett internt verktyg och håller API:t stateless, men i produktion över publika nät skulle den paras med HTTPS och troligen kompletteras med token-/sessionsbaserad inloggning för webbklienten. Det finns ingen kontolåsning eller rate limiting vid upprepade misslyckade inloggningar, och ingen UI för användaradministration (användare kommer från seed; lägga till eller spärra sker på databasnivå). Själva rollgenomdrivningen och separation of duties är dock på plats och testad.

- **Concurrency-testet täcker inte allt.** Testet kör 20 trådar mot riktig MySQL (Testcontainers) och verifierar att alla 20 saldojusteringar lyckas; den pessimistiska låsningen serialiserar dem (`SELECT ... FOR UPDATE`), vilket syns på att saldot räknas ned monotont utan tappade uppdateringar. Två ärliga begränsningar: assertionen kontrollerar *antalet* lyckade justeringar, inte slutsaldot explicit — att även asserta att saldot landar på start − 20 vore en starkare kontroll. Och leveransflödet skyddas av *samma* lås men har inget eget test; deadlock-scenarier är inte täckta. Viktig distinktion: låsmekanismen är bevisad och gör sitt jobb till rätt kostnad — det är *testtäckningen* som inte är komplett, inte säkerheten. Låset behöver inte förändras för den här skalan.

- **Beställningar skapas inte via UI än.** Läkemedel kan skapas, redigeras och tas bort via formulär i frontend (med klientvalidering som speglar Bean Validation, och fältfel från backend som visas per fält). Att *skapa* en ny beställning med orderrader finns däremot bara som API-endpoint, testad med curl — frontend visar och muterar beställningars status men har inget formulär för att lägga en ny order. Medvetet bortval för att skydda tid.

- **Sökningen hämtar om vid varje tangenttryck.** Sök/filter-fältet utlöser ett nytt API-anrop för varje ändring. För demons datamängd är det omärkbart, men vid hundratals poster och många användare vore en debounce (vänta ~300 ms efter sista tangenttryck) rätt — det skulle minska antalet anrop utan att märkas i UX:en. Medvetet utelämnat som en optimering som inte behövs vid den här skalan.

- **AI-utkast persisteras inte med strukturerad metadata.** Förslaget sparas som en vanlig order med en notering om källan ("Gemini" respektive "Regelbaserad fallback"), men inte som strukturerad metadata (modell, prompt-version, tidpunkt). I en produktionskontext med audit-krav för AI-beslut vore det värt att spåra.

- **Småsaker.** `dialect: MySQLDialect` i `application.yml` är redundant (Hibernate detekterar automatiskt). Flyway varnar vid uppstart eftersom 8.4 är nyare än den testade MySQL-versionen — V1 körde ändå rent. Båda är kosmetiska och dokumenterade.

## 8. Vad jag hade gjort med mer tid

I prioritetsordning.

**1. Resterande frontend-formulär.** Läkemedel kan skapas, redigeras och tas bort via UI, och listan har sök/filter; det som återstår är ett formulär för att *skapa* en beställning (med orderrader), med klientvalidering som speglar Bean Validation även där. Plus toast-notiser för success/fel och bekräftelsedialog före fler irreversibla actions (borttagning av läkemedel har redan en `window.confirm`; t.ex. Avbryt-övergången har ingen ännu).

**2. Härda autentiseringen.** Token-/sessionsbaserad inloggning för webbklienten (Basic är stateless men skickar credentials vid varje request), kontolåsning/rate limiting vid misslyckade försök, och en UI för användaradministration (skapa/spärra användare, återställ lösenord). Rollgenomdrivningen finns redan — det är skyddet runt själva inloggningen som skulle stärkas.

**3. Notiser och export** — de valbara casetilläggen jag inte hann: e-post/in-app-notis vid lågt lager, och CSV/PDF-export av beställningshistorik.

**4. Skalning till 50 vårdenheter.** Den intressantaste frågan. Tenant-isoleringen finns redan i datamodellen, så grunden bär — men flera saker skulle behöva ses över, och det är *inte* en fråga om att dela upp i microservices (se avsnitt 3) utan om att skala monoliten rätt:

- **Indexstrategi.** Sammansatta index på `(care_unit_id, ...)` för de vanligaste queries, eftersom varje query blir tenant-scopad. Mäta med faktiska query-planer, inte gissa.
- **Caching.** Läkemedelskatalogen ändras sällan men läses ofta — en cache (Caffeine eller Redis vid flera instanser) skulle avlasta databasen.
- **Läsrepliker.** Vid läs-tung last kan läsningar gå mot repliker medan skrivningar går mot primärnoden.
- **Partitionering av historiktabeller.** `audit_logs` och `stock_movements` växer obegränsat. Vid 50 enheter skulle de partitioneras (per tid eller tenant) så att de inte blir en flaskhals.
- **Async-jobb.** Notiser och eventuella AI-utkast nattetid skulle köras som bakgrundsjobb, inte i request-tråden.
- **Låsstrategin omprövas.** Vid en enhet är pessimistisk låsning rätt — väntekostnaden realiseras nästan aldrig och skyddet är värt det där felet vore allvarligast (patientsäkerhet). Vid 50 enheter och hög samtidighet skulle jag ompröva: optimistisk låsning med retry för lågkonflikt-operationer, pessimistisk reserverad strikt för leverans. Rätt mekanism beror på faktisk konfliktfrekvens, som man bör mäta innan man väljer.

**5. AI-agenten vidare.** Strukturerad AI-metadata för audit (modell, prompt-version, tidpunkt), striktare påfyllningslogik för narkotikaklassade läkemedel (`controlled_substance`), och eventuellt schemalagda utkast — men då blir den mänskliga granskningsgränsen viktigare, inte mindre.

**6. Aktör per statusövergång i UI:t.** Vem som skapade, skickade, bekräftade och levererade en order spåras redan i datamodellen (`sent_by`, `confirmed_by`, `delivered_by`) — det är den datan separation of duties bygger på. Den exponeras dock inte i `OrderResponse` ännu, så orderdetaljvyn visar *när* varje övergång skedde men inte *vem* som utförde den. Med mer tid hade jag lagt till aktör-fälten i API-svaret och visat dem i tidslinjen — ett naturligt komplement till audit-loggen.

---

Det genomgående draget: bygg det enkla som löser dagens problem, dokumentera resonemanget, och lämna dörren öppen för morgondagen. En välmotiverad kärna framför en otestad bredd.