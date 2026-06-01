# MediTrack — designbeslut & motiveringar

**Arbetsdokument inför teknisk intervju.** Inte tänkt att deployas — används för att förbereda
samtalet med Faraz. Dokumentet är *hela berättelsen*: varje val från projektets början till nu,
i den ordning besluten togs, med en motivering som går att säga högt.

**Datum:** 2026-05-28
**Stack:** Java 21 · Spring Boot 3.4.5 · MySQL 8.4 · Flyway · Hibernate/JPA · Spring Security · Lombok · Testcontainers
**Källor:** Casetexten, regelverksgenomgången, Pastoralen-inventeringen, datamodellen, affärsreglerna.

---

## 0. Berättelsens tidslinje (snabböversikt)

1. **Projektplan** togs fram mot casets bedömningskriterier ("hur du tänker" > "att allt blir klart").
2. **Insikt:** Pastoralens lärdomar blev en erfarenhetsram att bygga vidare på i MediTrack → strategin blev "bygg på beprövade mönster och utveckla dem vidare, lägg tiden på att visa upp dig".
3. **Stack-beslut:** Spring Boot + MySQL (inte Postgres) — medvetet val, hemmaplan.
4. **Regelverksgenomgång** (11 regelverk) → 15 konkreta beslut som format datamodellen *innan* en rad kod skrevs.
5. **Datamodell** (8 entiteter) designad regelverksinformerat.
6. **Affärsregler** (state machine, lagerlogik, threshold, RBAC) dokumenterade.
7. **Repo-setup:** Git-historik som berättelse, Docker + MySQL, pom.xml, application.yml.
8. **V1__init.sql** skriven, testkörd mot separat DB, verifierad.
9. **Spring Boot startar**, Flyway kör V1, schema migrerat.
10. **Entiteter** (8 st) implementerade — JPA-auditing, Auditable, tenant-relationer lazy, enums som STRING, `@Version` på muterbara.
11. **DTO-lagret** — records, entiteter exponeras aldrig, klienten väljer aldrig tenant/status.
12. **Service-lagret** — affärslogik samlad, `@Transactional` med DTO-mappning inuti (löser `open-in-view: false`), tenant alltid inifrån.
13. **OrderService + state machine** — DRAFT → SENT → CONFIRMED → DELIVERED, leverans enda övergången med sidoeffekt.
14. **Seed-data** (`R__seed.sql`) — två enheter (en tom för tenant-demo), två poster under threshold, en levererad order.
15. **Controllers + global exception-advice** — tunna controllers, action-endpoints, enhetlig `ApiError`, verifierat end-to-end med curl.
16. **Concurrency-test** (Testcontainers, 20 trådar) — pessimistic lock bevisad, inte bara demonstrerad. Grönt.
17. **Frontend-stack** — Vite + React + TS + Tailwind v3 + custom `useFetch`, medvetet vald över Thymeleaf.
18. **Frontend** (4 sidor) — läkemedel, lager, beställningar, orderdetalj. Defensiv UI som bara visar lagliga actions.
19. **Sanity-test (30 maj)** från clean state — `mvnw`-fix, felmeddelande-format i OrderDetailPage, seed-fix (`stock_items`), `/stock`-sortering. Allt grönt end-to-end.
20. **AI-agent byggd (30 maj)** — Gemini-driven påfyllningsagent. Läser lågt lager
    → föreslår DRAFT-orderutkast som människa granskar/skickar. @Primary-provider med
    regelbaserad fallback. Bevisad mot riktig Gemini (gemini-2.5-flash).
21. **Säkerhetsgräns för AI verifierad** — agenten föreslår, beslutar aldrig. Utkast +
    manuell Skicka = separation of duties applicerat på AI. Källa transparent i UI.
22. **Autentisering + RBAC byggd (31 maj)** — HTTP Basic mot användare i databasen
    (BCrypt), `MeditrackUserDetailsService`, stateless `SecurityConfig`. `CurrentUserProvider`-
    platshållaren bytt mot riktig `SecurityContextHolder`-lookup — ingen service ändrad.
23. **Separation of duties i två lager** — `@PreAuthorize` på controller (roll) +
    `SeparationOfDutiesException` i service (person får ej bekräfta egen order). Båda 403.
    `/api/me` + full frontend-login (central `apiFetch`, `AuthContext`, `LoginPage`).
    Bevisad live med curl och i webbläsaren.

---

## 1. Strategi & projektplan

### Bedömningskriterierna styr ansatsen
Caset säger uttryckligen: *"En välmotiverad halvfärdig lösning slår en okommenterad fullständig
implementation."* Därför är prioriteringen: solid kärna + dokumenterat resonemang + 1–2 väl
motiverade utökningar, framför att tvinga in allt. Hellre 80 % av scopet med genomtänkt arkitektur
än 100 % slarvigt.

### Pastoralen som hävstång
Pastoralen (Java 21 + Spring Boot 3.4.5 + MySQL, ~25 400 LOC, 262 tester, multi-tenant,
AI-integrerad, med ärlig skuldredovisning) är ett produktionsprojekt jag redan byggt. Det gör att
MediTrack inte handlar om *att kunna bygga* utan om *hur jag visar upp mig på en vecka*. Pastoralens
ärliga skuldredovisning matchar exakt Medovias bedömningskriterier ("ärlig självkritik, reflektioner
kring framtida förbättringar"). Samma röststil används i MediTrack.

### Risken är inte tidsbrist — det är scope creep
Med Pastoralen-erfarenheten finns gott om tid. Största risken är över-engineering och att utöka
scope när det går bra. Disciplin: bygg planen, motstå frestelsen.

---

## 2. Stack-val

### Java 21 + Spring Boot 3.4.5
Medvetet, inte default. Jag kan stacken från Pastoralen → fart utan kvalitetsförlust. Java 21
är LTS (support till 2031). Tiden läggs på domänkomplexiteten, inte på att lära verktyg.

### MySQL 8.4 — bytet från Postgres
Ursprungsplanen föreslog Postgres. Jag bytte medvetet till MySQL. **Skäl:** (1) Hemmaplan — Pastoralen
kör samma, Flyway-syntax/`@Column`-konventioner/Testcontainers-uppsättning är beprövade. (2) Undviker
en hel klass nybörjarfel (Postgres JSONB-syntax, `BIGSERIAL` vs `IDENTITY`, sekvenshantering). (3)
Caset ställer inga databaskrav. (4) Pastoralen-inventeringen är direkt återanvändbar.
**Trade-offs jag kan diskutera:** Postgres JSONB > MySQL JSON för audit-detaljer; Postgres är
vanligare i ny svensk health-tech; Pastoralen hade MySQL-versionsspridning (8.0 CI / 8.4 TC / 9.4
prod) som Postgres hade distanserat oss från. Slutsats: hemmaplansfördelen vägde tyngst, JSON räcker.
8.4 är senaste LTS (support till 2032).

### Frontend: planerad React + TypeScript
Valt för att visa det Medovia *inte* såg i portföljen (SPA-frontend separerad från backend) — lyfter
från "server-rendered Java-utvecklare" till "fullstack". Backend-fart behålls (Spring i sömnen),
React är där mest finns att vinna på att demonstrera. *(Byggd — se avsnitt 19–20.)*

### Flyway istället för Hibernate ddl-auto
Flyway är enda källan till sanning för schemat. Versionshanterade, immutabla migrationer ger spårbar
historik — kritiskt i sjukvård (SOSFS kvalitetsledning). `ddl-auto: update` i produktion är en känd
fara (kan tappa kolumner). Vi använder `validate` (se avsnitt 6).

### Testcontainers för integrationstester
Tester mot riktig MySQL i container, inte H2. H2 ljuger om dialektspecifika saker (JSON-typ,
CHECK-constraints, FK-beteende). Mönster återanvänt från Pastoralen (`AbstractMysqlIntegrationTest`).

### Spring Security från dag 1
I `pom.xml` från start. RBAC är regulatoriskt krav (HSLF-FS 2017:37, separation of duties), inte en
extra. Designas in, inte bolt-on.

### Lombok
Minskar boilerplate, exkluderad ur slutgiltig JAR. Konsekvent med Pastoralen.

---

## 3. Regelverksgenomgång — 11 regelverk som formade designen

Regelverken gicks igenom *före* datamodellen, eftersom de påverkar *hur* man designar (audit,
tenant-isolering, soft-delete, åtkomstkontroll). Hållningen i README: "jag vet vad jag täckt och vad
som ligger utanför min roll" — inte "jag har täckt allt". En formell regelverkskartläggning är ett
separat juridiskt arbete före produktionssättning.

| # | Regelverk | Påverkan | Vad det gav |
|---|---|---|---|
| 1 | **GDPR** (2016/679) | Hög | Audit, soft-delete + anonymisering, dataportabilitet, FK-design |
| 2 | **Patientdatalagen** (2008:355) | Hög | Tenant-isolering, åtkomstloggning |
| 3 | **NIS2 / Cybersäkerhetslagen** | Medel | Strukturerad logging, incidentspårbarhet (Medovias kunder = väsentliga entiteter → smittar) |
| 4 | **MDR** (2017/745) | Låg (gränsfall) | Inte MDSW i nuläget; gränsen värd att kunna diskutera |
| 5 | **HSLF-FS 2017:37** | **Hög — kärnregelverket** | Ordination/hantering av läkemedel; RBAC + separation of duties = regulatoriskt krav |
| 6 | HSLF-FS 2016:40 | Medel | Journaldetaljer om patientdata berörs (gör det inte direkt) |
| 7 | SOSFS 2011:9 | Medel | Kvalitetsledning → avvikelsehantering, audit som kvalitetskrav |
| 8 | Patientsäkerhetslagen (2010:659) | Medel | Lex Maria; audit-logg blir bevismaterial vid vårdskada |
| 9 | LVFS 2012:8 | Låg–medel | Rekvisitionsflödet → order-state machine ska kunna mappa mot det |
| 10 | ISO 13485 / 14971 / IEC 62304 | Låg | Tillkommer bara om MDSW; värt att veta att de finns |
| 11 | Särskilda läkemedel (narkotika) | Låg–medel | `controlled_substance`-flagga, designat för dubbelsignering |

### MDR/MDSW-gränsen (bra intervjusvar)
MediTrack är i nuvarande form ett **logistik- och inventeringssystem** → inte MDR-pliktigt. Gränsen mot
medicinteknisk programvara (MDSW) går när systemet gör *patientspecifika* beslut: dosering,
interaktionsvarningar, administrationsövervakning. Då blir det klass IIa+ under MDR med CE-märkning,
klinisk utvärdering och IEC 62304 — vilket påverkar arkitektur, kvalitetssystem och utvecklingstid.
Att veta *exakt var gränsen går* visar förståelse för landskapet.

### 15 konkreta beslut som föll ut till datamodellen
Audit & spårbarhet: (1) `Auditable @MappedSuperclass`, (2) strukturerad `audit_log`-tabell, (3)
designad för läs-loggning även om bara skrivningar implementeras, (4) FK till users för "vem", aldrig
strängar. Tenant: (5) `care_unit_id` på alla domäntabeller från V1, (6) Hibernate-filter så filtrering
är default, (7) integrationstest som bevisar isolering. GDPR: (8) soft-delete + anonymisering på User,
(9) dataportabilitets-endpoint, (10) dataminimering. Säkerhet: (11) `@PreAuthorize` på skrivande
endpoints, `hasRole` utan `ROLE_`-prefix (Pastoralens läxa), (12) ingen PII i loggar. Domän: (13)
`controlled_substance` på Medication, (14) `signed_by` förberett för dubbelsignering, (15)
avvikelse-/incident-action-typer i audit-loggen.

---

## 4. Datamodell — 8 entiteter

### Entiteter och syfte
Kärndomän: **CareUnit** (tenant-rot), **Medication** (katalog per enhet), **StockItem** (saldo +
threshold per enhet), **Order** (state machine), **OrderLine** (rad), **User** (roll + enhet).
Stöd: **AuditLog** (immutabel) och **StockMovement** (immutabel).

### Designval med motivering
- **Medication per tenant, inte global katalog** — PDL-isolering. Pastoralens läxa: isolera från dag 1.
- **StockItem separat från Medication** — saldo är *operativ* data, läkemedel är *katalog*data; olika
  ändringstakter. Threshold på StockItem för "lågt lager" beror på enhetens förbrukning (akutmottagning
  ≠ rehab-boende).
- **OrderLine pekar på Medication, inte StockItem** — en beställning gäller *att skaffa ett läkemedel*;
  rätt StockItem löses vid leverans via `(medication_id, care_unit_id)`.
- **AuditLog & StockMovement immutabla** — inga `updated_*`-fält; historikrader ändras aldrig (poängen
  med spårbarhet). StockMovement har `quantity_after` → svara "vad var saldot kl 14:30 den 5 maj?" utan
  att spela upp historiken.
- **Tenant-FK på alla domänentiteter** (PDL, GDPR). CareUnit *är* tenanten → ingen egen tenant-FK.
- **`@Version` (optimistic lock) på Order och StockItem** — concurrency (Faraz frågar uttryckligen).
- **`controlled_substance` på Medication** — HSLF-FS 2017:37 (narkotika), designat för dubbelsignering.
- **`UNIQUE (care_unit_id, email)` på User** — e-post unik per enhet, inte globalt; förenklar
  soft-delete.
- **Soft-delete + anonymisering på User** — `deleted_at` + `anonymized_at` (GDPR art. 17), inte hård
  radering.
- **`JSON` på `audit_logs.details`** — strukturerade metadata. (MySQL JSON; Postgres JSONB hade varit
  marginellt bättre — en av få punkter där Postgres vinner.)
- **`UNIQUE (order_id, medication_id)` på OrderLine** — samma läkemedel kan inte finnas två gånger i en
  beställning; tvingar kvantitetsaggregering uppströms.

### Implementeringsordning (Flyway V1, topologisk efter FK)
care_units → users → medications → stock_items → orders → order_lines → stock_movements → audit_logs.
Allt i **en** migration `V1__init.sql`. Seed planeras som `R__seed.sql` (repeatable) — håll seed skilt
från schema (Pastoralens läxa).

### Medvetna bortval (datamodell)
Schema-per-tenant (overkill; tenant-FK + filter räcker), globala läkemedelskataloger (bryter
PDL-isolering), separata historik-arkivtabeller (kan partitioneras senare), lot/batch-spårning +
utgångsdatum (MDR-territorium, kräver omdesign), JOINED-table-inheritance på Medication (onödig
prestandakostnad).

---

## 5. Affärsregler

### Order state machine i dedikerad service
DRAFT → SENT → CONFIRMED → DELIVERED (+ CANCELLED från icke-terminala lägen). Logiken i
`OrderStateMachine`, inte i entitet/controller — explicit, testbart, JPA-oberoende. Status kan inte
hoppa över steg (SENT → DELIVERED utan CONFIRMED bryter spårbarheten). DELIVERED är terminalt —
korrigeringar görs som nya StockMovement/orders, inte genom att backa status. Endast DRAFT-orders får
ändras (en skickad order är ett avtal). CANCELLED är en medveten extension utöver casets fyra statusar.

### Differentierad concurrency — optimistic vs pessimistic
Olika operationer, olika konfliktprofiler. Order: optimistisk (`@Version`). Lageruppdatering vid
leverans: pessimistic lock (`SELECT ... FOR UPDATE`) på StockItem — undviker retry-loop när två
leveranser träffar samma StockItem samtidigt. Att veta *när* man väljer vilket är poängen — direkt svar
på Faraz concurrency-fråga.

### Lagerlogik
Saldo ändras *endast* via StockMovement (att sätta `quantity = 42` direkt lämnar inget spår). INITIAL-
rörelse skapas när StockItem skapas. Saldo kan aldrig bli negativt (validering + CHECK som backstop).
Leverans → atomisk uppdatering under transaktion.

### Threshold som beräknat fält, inte lagrat
"Under threshold" = `quantity < threshold`, beräknas vid hämtning, lagras inte. Förhindrar inkonsistens
mellan quantity och en cachad flagga. Strict less-than (vid `quantity == threshold` är vi vid gränsen,
inte under). Varningen är informativ — blockerar ingen handling.

### Validering på flera lager
DTO (Bean Validation: `@NotNull`, `@Email`, ATC-regex `^[A-Z]\d{2}[A-Z]{2}\d{2}$`), tjänstelager
(affärsregler), databas (CHECK + FK + UNIQUE som backstop). Databasen är sista försvarslinjen om något
går runt service-lagret. Order-number genereras serverside (inte användarinput).

### RBAC med separation of duties
Sjuksköterska som beställt får inte själv bekräfta (SENT → CONFIRMED kräver PHARMACIST/ADMIN) —
HSLF-FS 2017:37. NURSE kan bara avbryta egna beställningar. Tenant-isolering trumfar roller: även ADMIN
ser bara sin egen enhets data (PDL). `@PreAuthorize` + `hasRole` utan `ROLE_`-prefix (Pastoralens läxa).

### Medvetna bortval (affärslogik)
Komplex approval workflow (caset specificerar fyra statusar), automatisk re-order vid threshold
(varningen är informativ; möjlig sen-feature), eskalation/delegering vid frånvaro (overkill för pilot).

---

## 6. Infrastruktur, repo & konfiguration

### Git-historik som berättelse
Designdokumenten (datamodell + affärsregler) commitades *före* koden, så historiken berättar: först kom
designen, sen koden som implementerar den. Conventional Commits (`docs:`-prefix), rubrik + brödtext
(Pastoralens stil). Faraz läser historiken.

### Docker + MySQL — port 3307
En nativ MySQL (`mysqld`, user `_mysql`, autostart vid boot) låg redan på 3306 på maskinen. Bytte
hostport till 3307, behöll container-intern 3306. Reversibelt, noll risk att störa den nativa
installationen eller andra projekt. Persistent named volume (`meditrack-mysql-data`) — verifierat att
data överlever `docker compose down` (schemat fanns kvar, Flyway hoppade över redan körd V1).
Healthcheck via `mysqladmin ping`. `TZ: Europe/Stockholm` (viktigt för audit-tidsstämplar).

### `application.yml`-beslut
- **`ddl-auto: validate`** — mest kritiska valet. Hibernate jämför entiteter mot DB vid uppstart och
  kraschar vid drift, men ändrar aldrig schemat. Flyway äger schemat, Hibernate validerar bara.
- **Strikt Flyway:** `baseline-on-migrate: false` (tvingar start från tomt schema, krasch istället för
  tyst baseline), `validate-on-migrate: true` (checksum-koll vid varje uppstart → krasch om en körd
  migration ändrats i efterhand). Migrationer är immutabla efter körning.
- **`open-in-view: false`** — stänger Springs "Open Session In View"-antipattern, som döljer lazy-load-
  problem tills produktion (Pastoralens läxa). Tvingar explicit laddning i service-lagret.
- **HikariCP:** `maximum-pool-size: 10`, `minimum-idle: 2`, namngiven pool (`MeditrackHikariCP`) som
  syns i loggar.
- **SQL-loggning på DEBUG/TRACE** under dev (`format_sql`, `hibernate.SQL`, `jdbc.bind`) — stängs av i
  produktion.

### `pom.xml`
Spring Boot 3.4.5, Java 21. Web + JPA + Validation + Security + Flyway (core + mysql) + MySQL connector
(scope runtime) + Lombok + Testcontainers (mysql + junit-jupiter) + Spring Security Test. Versioner ärvs
från Spring Boot BOM (inga explicita Testcontainers-versioner → undviker konflikter).

### V1 testkörd mot separat databas innan Flyway
Innan Flyway släpptes mot huvuddatabasen kördes `V1__init.sql` mot en isolerad `meditrack_test`-DB. Det
isolerar SQL-syntaxfel från Spring Boot/Flyway-stacktraces — ett fel hade synts som ett tydligt
MySQL-meddelande direkt, inte som en 50-frame Java-trace (Pastoralens läxa).

### CHECK-constraints verifierade manuellt
CHECK är backstop bakom service-validering. Verifierat att de faktiskt avvisar ogiltig data: ett
`INSERT` med `role = 'BOSS'` avvisades med `chk_user_role`-fel — inte bara att de skapades. CHECK
utvärderas dessutom före FK-lookup (snabbavslag).

### `utf8mb4` på alla tabeller
MySQL:s "utf8" är 3-byte och rymmer inte all Unicode (emoji, vissa CJK). `utf8mb4` är 4-byte riktig
UTF-8. Standard i moderna MySQL-projekt.

### Resultat
Spring Boot startar på ~5,8 s. Flyway kör V1 (255 ms, 8 tabeller + constraints + index + JSON-kolumn).
Hibernate `validate` passerar rent (inga entiteter ännu → inget att validera). Tomcat på 8080.

---

## 7. Entiteter — tidiga beslut

### Audit-fält: Spring Data Auditing + system-auditor (platshållare)
**Beslut efter att ha kollat Pastoralen:** Pastoralen spårar vem som ett strängvärde (`skapad_av
VARCHAR`), mappat manuellt per entitet, tider via `DEFAULT CURRENT_TIMESTAMP`. MediTrack går medvetet
längre — domänkravet (PDL) kräver *verifierbar* spårbarhet kopplad till en identitet, inte fritextnamn.
Därför: `@EnableJpaAuditing` + en `AuditorAware<Long>`-bean fyller `created_by`/`updated_by` automatiskt.
Under utveckling returnerar auditorn system-användaren (id = 1) tills riktig auth finns — byts mot
SecurityContext-lookup när Spring Security konfigureras. Låter audit-fälten fungera från dag 1 utan att
blockeras av att auth ännu inte byggts.
*Intervjupoäng: "jag kopierade inte Pastoralen — jag anpassade mönstret efter domänens krav."*

### `Auditable` som `@MappedSuperclass`
Samlar de fyra audit-fälten (`created_at/by`, `updated_at/by`) så alla muterbara entiteter ärver dem
konsekvent — DRY jämfört med att upprepa kolumnerna i sex entiteter. `created_by`/`updated_by` lagras
som rått `Long` (FK-id), inte `@ManyToOne`, för att undvika tunga laddningar och cirkulära beroenden
(User ärver också Auditable). `@CreatedDate`/`@CreatedBy` har `updatable = false` (sätts en gång).
Immutabla entiteter (AuditLog, StockMovement) ärver medvetet INTE Auditable — AuditLog *är* audit-
mekanismen (`user_id` är aktören, `created_at` är när, ändras aldrig).

### CareUnit — första entiteten
Tenant-roten, har ingen `care_unit_id` (den *är* tenanten). `@GeneratedValue(IDENTITY)` matchar MySQL
`AUTO_INCREMENT` (rätt strategi för MySQL, till skillnad från SEQUENCE för Postgres). Explicita
`@Column`-namn (`external_id` etc.) snarare än förlitan på Hibernates naming strategy — eliminerar
camelCase↔snake_case-tvetydighet (Pastoralens läxa: explicit slår implicit för kolumnnamn).
`@NoArgsConstructor` (JPA-krav). Primitiv `boolean active = true` matchar `DEFAULT TRUE`.

---

## 8. Kända avvikelser / "minst nöjd med"-frågan

- **Flyway 10.20.1 testad mot MySQL ≤ 8.1**, vi kör 8.4 → varning vid uppstart, men V1 körde rent. Kan
  uppgraderas till Flyway 11.
- **`dialect: MySQLDialect` i `application.yml` är redundant** — Hibernate detekterar automatiskt och
  varnar. Kan tas bort för renare config.
- **System-auditor (id = 1) är en platshållare** — måste bytas mot SecurityContext-lookup när auth byggs.
- **JDBC-version skiljer sig:** Maven använder Connector/J 9.1.0 (Spring Boot BOM), IntelliJ
  Database-verktyget 9.7.0. Oberoende, ingen funktionsskillnad.
- **Frontend ej påbörjad** — backend byggs först; React + TS är den största återstående posten och bör
  inte trängas undan av över-polerad backend.

---

## 9. Compliance — kort version till README

MediTrack är designat med medvetenhet om de centrala regelverk som påverkar svenska vårdsystem: GDPR,
Patientdatalagen (2008:355), Hälso- och sjukvårdslagen (2017:30), Patientsäkerhetslagen (2010:659),
Socialstyrelsens föreskrifter om läkemedelshantering (HSLF-FS 2017:37) och om kvalitetsledning
(SOSFS 2011:9), NIS2/Cybersäkerhetslagen, samt MDR (2017/745) i gränsfall. Datamodellen stödjer GDPR
(audit, soft-delete + anonymisering, dataportabilitet), PDL (tenant-isolering, åtkomstloggning) och
kvalitetsledning (avvikelsehantering via audit). En formell regelverkskartläggning är ett separat
juridiskt arbete som föregår produktionssättning och täcker även DOS-lagen, läkemedelsförordningen,
LVFS 2012:8 och Läkemedelsverkets föreskrifter om särskilda läkemedel.

---

## 11. AI-funktion (planerad utökning)

Casets valbara AI-funktion ska göras, och göras *ödmjuk* — "AI som komponent, inte som magi" (Pastoralens
läxa: tidiga K3-prompten lät modellen gissa noll vid saknad data → falska bokslut). Kandidater: ATC-baserad
automatisk kategorisering, prediktiv lagerpåfyllning, eller en chatbot som svarar på lagerstatus med
defensiv JSON-parse och "data saknas"-hantering. Prompt caching diskuteras i README men implementeras inte
(för låg volym). Relevant gentemot ett bolag som omvandlar sig till "AI-first".

## 12. Entitets- och repository-lagret (28 maj)

Datamodellen fanns på papper sedan tidigare (avsnitt 4). Här är besluten
som togs när den blev till Java-kod, i den ordning de uppstod.

### 12.1 Tenant-relationer: navigerbara, lata, obligatoriska

Varje entitet som tillhör en vårdenhet (User, Medication, StockItem, Order,
StockMovement, AuditLog) har `@ManyToOne(fetch = LAZY, optional = false)`
mot CareUnit.

- **LAZY** — vårdenheten laddas inte automatiskt när vi hämtar t.ex. en
  User. Den hämtas bara om vi faktiskt rör `user.getCareUnit()`. Det är
  därför `open-in-view: false` är satt — det tvingar oss att vara medvetna
  om när relationer laddas, istället för att dölja extra databasträffar.
- **optional = false** — en User utan vårdenhet är meningslös; speglar
  NOT NULL i schemat.

### 12.2 Relation kontra rått id — ett medvetet val

Tenant-kopplingen är en `@ManyToOne`-relation (CareUnit-objekt), men
audit-fälten (`created_by`, `updated_by`, `sent_by` osv.) är råa `Long`.

Skillnaden är avsiktlig: tenant-relationen navigerar vi ofta (hämta
användarens enhet), medan audit-by sällan följs upp — det är spårning,
inte navigering. Att göra varje by-fält till en User-relation hade tyngt
modellen utan vinst.

### 12.3 Enums lagras som sträng, aldrig ordinal

Alla enums (`Role`, `MedicationForm`, `MovementReason`, `OrderStatus`)
mappas med `@Enumerated(EnumType.STRING)`.

Default är ORDINAL — enumens position lagras som siffra (0, 1, 2...). Det
är en tyst datakorruptionsfälla: lägger man till ett värde i mitten eller
ändrar ordning, pekar gamla rader plötsligt på fel värde. STRING lagrar
namnet ("NURSE") — robust och läsbart i databasen. Kostar några byte, värt
varje.

### 12.4 Optimistisk låsning på muterbara entiteter

Order och StockItem har `@Version`. Hibernate ökar fältet vid varje update
och lägger till `WHERE version = <läst värde>` — krockar två uppdateringar
får den andra OptimisticLockException istället för att tyst skriva över
(lost update).

Det är förstaförsvaret mot samtidighet. Vid leverans räcker det inte (se
12.7).

### 12.5 Immutabla historikrader ärver inte Auditable

StockMovement och AuditLog ärver INTE basklassen Auditable. De är
oföränderliga — en historikrad uppdateras aldrig, så `updated_at/by` vore
meningslöst. De har bara `created_at` (auto-satt via @CreatedDate) och
sätter `created_by`/`user_id` explicit i service-lagret.

AuditLog är dessutom själva audit-mekanismen snarare än något som behöver
auditeras: `user_id` är aktören, `created_at` är när, raden ändras aldrig.

### 12.6 AuditLog.details som JSON

Audit-rader behöver flexibla metadata (gammalt/nytt värde, kontext) som
varierar per händelsetyp. Istället för en stel kolumnuppsättning lagras
detta som MySQL JSON via `@JdbcTypeCode(SqlTypes.JSON)`, hållet som String
i Java och serialiserat i service-lagret. (Här hade Postgres JSONB varit
vassare — en av kompromisserna med MySQL-valet, se avsnitt 2.)

### 12.7 Repository-lagret: tenant-scoping och två lås

Åtta Spring Data JPA-repositories. Två beslut värda att lyfta:

**Tenant-isolering — mönster A (explicit).** Repository-metoderna tar
`careUnitId` som parameter: `findByIdAndCareUnitId`, `findAllByCareUnitId`.
Alternativet var Hibernate `@Filter`/`@TenantId` (automatisk filtrering).
Jag valde det explicita för att det är synligt och försvarbart — varje
query är uppenbart tenant-scoped, syns direkt i signaturen, ingen dold
magi. `@TenantId` är ett rimligt nästa steg vid fler enheter och noteras
som framtida förbättring.

**Två låsstrategier för olika operationer.** Vanliga uppdateringar förlitar
sig på optimistisk låsning (@Version, 12.4). Men den integritetskritiska
saldouppdateringen vid leverans använder pessimistisk låsning:
`findByIdAndCareUnitIdForUpdate` med `@Lock(PESSIMISTIC_WRITE)` →
`SELECT ... FOR UPDATE`. Raden låses tills transaktionen committar, så
samtidiga leveranser serialiseras istället för att tävla.

Analogin: ett bokningssystem låser inte hotellrummet medan gästen funderar
— det låser i själva bokningsögonblicket. Likadant här: en order kan ligga
i DRAFT i dagar utan lås, men den korta stunden då saldot faktiskt skrivs
vid leverans låses smalt och kort. Pessimistisk låsning bara där fel vore
allvarligast (fel lagersaldo = patientsäkerhetsrisk), inte överallt — det
undviker onödig blockering och deadlock-risk.

### 12.8 CRUD och soft-delete

Repositories ger CRUD gratis via JpaRepository. Men "D" är inte alltid
radering: User soft-raderas (deleted_at sätts, raden finns kvar), och
historiktabellerna (StockMovement, AuditLog) raderas aldrig — de är ju
spårbarheten. Hård delete är undantag, inte regel, i ett system där
spårbarhet är ett krav.

## 13. DTO-lagret (28 maj)

API:t ska inte tala entiteter. Här är besluten kring data transfer objects,
i den ordning de uppstod.

### 13.1 Records, inte klasser

Alla DTOs är Java records (Java 21). Immutabla, kompakta, ingen
Lombok-boilerplate. Entiteterna förblir klasser — JPA kräver muterbara
objekt med no-args-konstruktor. Records för det som bara bär data en väg,
klasser för det som lever i persistenskontexten. Att skilja på de två
visar att verktyget väljs efter uppgift.

### 13.2 Entiteter exponeras aldrig direkt

Varje resurs har en egen Response-record med en handskriven statisk
from(entity)-mapper. Skälen:

- **Inget läckage.** Entiteten har fält som inte hör hemma i API:t
  (version, audit-by, tenant-relation, och i Users fall passwordHash).
  Returneras entiteten rakt ut läcker allt. Response-recorden plockar ut
  exakt det som ska visas.
- **Frikoppling.** API-kontraktet binds inte till databasstrukturen. En
  kolumnändring tvingar inte fram en API-ändring.

Handskrivna mappers framför MapStruct: synligt, noll magi, inget extra
beroende. Vid fler resurser vore MapStruct rimligt — noteras som möjligt
nästa steg.

### 13.3 Klienten väljer aldrig tenant eller status

CreateMedicationRequest och CreateOrderRequest saknar careUnitId. Det vore
en säkerhetslucka om klienten fick ange tenant — man kunde skapa data i
någon annans vårdenhet. Service sätter careUnitId från inloggad användares
kontext. Likadant sätts en orders startstatus (DRAFT) av service, inte av
klienten. Indata bestämmer aldrig säkerhetsgränser.

### 13.4 Separata Create- och Update-records

Trots nästan identiska fält är CreateMedicationRequest och
UpdateMedicationRequest skilda. De divergerar: update har active (kan
inaktivera), create inte (nya är alltid aktiva). Att slå ihop dem "för att
spara rader" låser ihop två saker som kommer skilja sig. Lite duplicering
är billigare än fel abstraktion.

### 13.5 Validering på vägen in

Bean Validation-annoteringar (@NotBlank, @NotNull, @Size, @Positive) sitter
på request-records, inte på entiteterna. Validering hör till API-gränsen.
På CreateOrderRequest sitter @Valid på radlistan — det aktiverar
kaskadvalidering så att varje OrderLineRequest valideras individuellt, inte
bara att listan är icke-tom.

### 13.6 Saldo ändras aldrig via en setter

StockItem har UpdateThresholdRequest men ingen "UpdateQuantityRequest".
Saldot får aldrig sättas rakt — det ändras alltid via en lagerrörelse
(leverans eller AdjustStockRequest med reason), som lämnar en StockMovement
efter sig. Det skyddar spårbarheten: varje saldoförändring har en orsak och
en historikrad. Threshold däremot är ren konfiguration och ändras fritt.

### 13.7 Beräknade fält i mappern

StockItemResponse.belowThreshold beräknas i from() (quantity < threshold).
Affärsregeln "lågt lager" presenteras färdig för frontend — en stressad
sjuksköterska ska se varningen, inte tolka två tal. Logik som hör till
presentationen läggs där svaret formas.

### 13.8 Utplattade fält och nästling

Response-records plattar ut det frontend annars måste slå upp separat:
medicationName ligger direkt på StockItemResponse och OrderLineResponse.
OrderResponse nästlar däremot sina rader (List<OrderLineResponse>) — hela
beställningen i ett svar, inga extra anrop. OrderResponse.from() återanvänder
OrderLineResponse.from() per rad; mönstret bär sig självt.

### 13.9 Öppen fråga inför services: lazy-laddning

from()-mappers rör lazy-relationer (getMedication(), getLines()). Med
open-in-view: false stängs Hibernate-sessionen när service-metoden
returnerar. Mappas DTO:n efter det → LazyInitializationException, ett fel
som inte syns förrän runtime. Services måste därför mappa innanför
transaktionen, eller ladda relationerna explicit med JOIN FETCH. Detta är
ett medvetet designkrav på service-lagret, inte en eftertanke.

## 14. Service-lagret (28 maj)

Här bor affärslogiken. Lagren under (entitet, repository, DTO) bär data;
servicen bestämmer vad som FÅR hända. Besluten, i den ordning de uppstod.

### 14.1 En regel löser open-in-view: false

Service-metoderna är @Transactional och DTO-mappningen sker INUTI servicen,
aldrig i controllern. Så länge from(entity) körs medan transaktionen lever
är Hibernate-sessionen öppen och lazy-relationer kan laddas. Controllern får
en färdig DTO där inget lazy finns kvar. Regel: entiteter lämnar aldrig
service-lagret, controllern ser bara DTOs. Det löser lazy-fällan (13.9) och
ger ren skiktning på köpet.

### 14.2 @Transactional med readOnly på läsningar

Klassnivå-@Transactional för skrivmetoder; readOnly = true på läsmetoder.
Read-only låter Hibernate hoppa över dirty-checking och signalerar avsikt.
Skrivtransaktionen håller dessutom sessionen öppen för mappningen (14.1).

### 14.3 Användarkontext bakom en abstraktion

CurrentUserProvider svarar på "vem är inloggad och vilken vårdenhet?".
Just nu en platshållare med fasta id (1). När Spring Security kopplas in
byts implementationen mot en SecurityContextHolder-lookup — ingen service
behöver röras. Det lät hela service-lagret byggas och testas innan auth
fanns, och isolerar säkerhetskopplingen till ett ställe (samma idé som
AuditorAware).

### 14.4 Tenant kommer alltid inifrån, aldrig från parametrar

Varje metod hämtar careUnitId från CurrentUserProvider, aldrig från
klientens indata. En klient kan inte be om en annan enhets data. NotFound
är tenant-medveten: "finns inte för dig" skiljer sig inte från "finns inte"
— ingen läcka om vad som finns i andra enheter.

### 14.5 Update litar på dirty checking

Uppdateringsmetoder hämtar entiteten (managed inom transaktionen), ändrar
fält, och förlitar sig på att Hibernate flushar vid commit. Ingen explicit
save(). Mindre kod, och det visar förståelse för persistenskontextens
livscykel.

### 14.6 Soft-delete i tjänsten

MedicationService.delete sätter active = false. Ett läkemedel med historik
(orderrader, lagerrörelser) hård-raderas aldrig — det bryter
referensintegritet och spårbarhet. "Radera" i ett spårbart system betyder
oftast "inaktivera".

### 14.7 StockService: saldo ändras bara på ett ställe

All saldomutation går genom en privat applyMovement som gör två saker
atomärt: ändrar quantity OCH skapar en StockMovement med quantity_after.
Det finns ingen kodväg som rör saldot utan att lämna historik. Invarianten
"saldo och historik glider aldrig isär" är därmed en egenskap hos koden,
inte en förhoppning. Negativt saldo blockeras centralt i samma metod — en
regel, ett ställe.

### 14.8 Pessimistisk låsning på saldokritiska skrivningar

receiveDelivery och adjustStock hämtar StockItem via findBy...ForUpdate
(@Lock PESSIMISTIC_WRITE → SELECT ... FOR UPDATE). Raden låses tills
transaktionen committar; samtidiga leveranser serialiseras istället för att
skapa lost updates. updateThreshold använder INTE lås — threshold är
konfiguration, inte saldo. Skillnaden syns direkt i vilken repo-metod som
anropas.

Kostnadsavvägningen var medveten: pessimistisk låsning kostar i form av
väntan och deadlock-risk, optimistisk kostar i form av retries vid konflikt.
Vid den här skalan (en enhet, få samtidiga användare) realiseras
väntekostnaden nästan aldrig, medan konsekvensen av fel saldo är hög
(patientsäkerhet) — så den säkrare mekanismen valdes. Vid 50+ enheter och
hög samtidighet skulle valet omprövas: optimistisk med retry kan då skala
bättre på lågkonflikt-operationer, pessimistisk reserveras strikt för
leverans. adjustStock kunde redan nu nöjt sig med optimistisk (sällan
samtidig) — en kodväg valdes för enkelhet, noteras i README.

### 14.9 En medveten söm mot OrderService

StockService.receiveDelivery tar emot ett orderId men sätter inte
StockMovement.order — det kräver ett Order-objekt, och att ladda hela ordern
bara för referensen vore slöseri. Sömmen sys av OrderService, som redan har
Order-objektet när den anropar leverans. En designad ofullständighet, inte
en glömska.

## 15. OrderService och statusövergångarna (28 maj)

Beställningsflödets logik bor i två klasser med tydlig ansvarsfördelning:
reglerna i OrderStateMachine, applikationen av reglerna i OrderService.

### 15.1 State machine som separat klass

Övergångsreglerna ligger i OrderStateMachine — ren logik utan
databasberoende, trivialt enhetstestbar i isolation. Reglerna uttrycks som
data, inte kod: en `Map<OrderStatus, Set<OrderStatus>>` listar tillåtna
övergångar per ursprungstillstånd. Hela tabellen läses på fem rader.

Alternativet vore en metod på Order-entiteten ("OO-snyggt"), men det skulle
blanda persistens med affärsregler och göra entiteten beroende av
exceptions. Separat klass håller entiteten ren och låter reglerna leva på
ett ställe.

Terminala tillstånd (DELIVERED, CANCELLED) mappas till tom mängd — ingen
specialhantering behövs, validation faller naturligt eftersom inget finns
i mängden att tillåta.

### 15.2 Status sätts aldrig från klient

Två platser kan ändra status, båda kontrollerade av servicen:
- createOrder sätter alltid DRAFT (hårdkodat, inte från request).
- updateStatus validerar målet via OrderStateMachine innan ändring.

Klientens UpdateOrderStatusRequest skickar bara *målstatusen* — inte ett
kommando. Service-lagret avgör om språnget är lagligt. Reglerna förblir
hos servern.

### 15.3 DELIVERED är den enda övergången med sidoeffekter

Tidsstämpel + aktör sätts per övergång (SENT/CONFIRMED/DELIVERED har egna
*_at och *_by-kolumner — spårbarhet för PDL). Men bara DELIVERED utlöser
saldoökning genom anrop till lager-skiktet. Switch-blocket i updateStatus
visar asymmetrin direkt: leverans har sidoeffekt, övriga inte.

CANCELLED har medvetet ingen egen tidsstämpel-kolumn — updated_at från
Auditable räcker som spår. Mindre kolumner, samma information.

### 15.4 Atomäritet sträcker sig över hela leveransen

Vid DELIVERED körs allt inom samma @Transactional-metod: validera övergång,
sätt tidsstämpel, för varje rad: hämta lagerpost med pessimistisk låsning,
öka saldo, skapa StockMovement. Misslyckas något i mitten → rollback. Det
betyder att en order aldrig blir "halvlevererad" — antingen är hela
leveransen registrerad eller ingen alls.

### 15.5 Sömmen sys: order-referensen på StockMovement

StockService (14.9) lämnade medvetet StockMovement.order osatt eftersom den
inte hade Order-objektet. OrderService har det när leverans sker, och syr
sömmen där. Designad ofullständighet, inte glömska — vi flyttar
sammankopplingen till det lager som naturligt har all information.

### 15.6 Leveranskoden ligger i OrderService, inte i StockService

Ett medvetet val: leveransloopen (per rad: lås StockItem, uppdatera saldo,
skapa movement) är inte ett anrop till StockService.receiveDelivery, utan
direkt kod i OrderService. Skälet är referensen i 15.5 — vi har Order-
objektet här och behöver det för movement.order.

Det innebär en liten duplicering av mönstret "uppdatera saldo + skapa
movement" mellan StockService.applyMovement och OrderService.
applyDeliveryToStock. Duplicering valdes över ett kontorsavbrott i
abstraktionen — bättre att två likartade rutiner är synliga än att en
abstraktion bryts för att tvinga ihop dem. Vid mer komplex orderlogik
(t.ex. delleveranser) vore en gemensam, parametriserad hjälpmetod
motiverad.

### 15.7 cancelOrder återanvänder updateStatus

cancelOrder(orderId) anropar updateStatus(orderId, CANCELLED) under huven.
Samma övergångsregler gäller automatiskt — man kan inte avbeställa en
DELIVERED order (vilket är korrekt: varan har redan kommit). En liten
bekvämlighetsmetod, ingen separat övergångsmekanism.

### 15.8 OrderNumber: UUID-suffix räcker för caset

Format: ORD-XXXXXXXX (åtta hex-tecken från en UUID). Inte sekventiellt och
inte garanterat unikt över hela världen, men kollisionsrisken är
försumbar för en demo. I produktion vore ett sekventiellt nummer per
enhet (egen sekvenstabell med radlås per generering) lämpligare —
noteras som framtida förbättring.


## 16. Seed-data: en demo-bar databas (28 maj)

Backend utan data är en katedral utan möbler. Seed-data ger den något att
visa upp.

### 16.1 R__seed.sql som repeatable Flyway-migration

Filen ligger i `db/migration/` bredvid V1__init.sql, men med R__-prefix
(repeatable). Flyway kör om den varje gång innehållet ändras — perfekt för
seed eftersom man vill kunna justera demo-datan utan att uppfinna en ny
V-migration varje gång. V-migrationer är förändringar av schemat;
R-migrationer är förändringar av innehållet.

### 16.2 INSERT IGNORE för idempotens

Varje INSERT använder MySQL:s INSERT IGNORE — krockar med befintlig primary
key tyst istället för att krascha. Det gör att seed kan köras om utan
sidoeffekt: körs den om utan ändringar händer ingenting; körs den om med
nya rader läggs bara de nya till. Postgres motsvarighet vore
ON CONFLICT DO NOTHING.

### 16.3 Explicita id:n — medvetet brott mot vanan

Normalt låter man AUTO_INCREMENT sköta id-tilldelning. Här sätts id:n
explicit (id 1, 2, 3...) för att andra tabeller ska kunna referera dem
säkert: orderns care_unit_id = 1 pekar garanterat på Akutmottagningen.
Detta är seed-specifikt — i produktion låter man auto-increment styra.

### 16.4 Tom enhet 2 — designad demo av tenant-isolering

Två vårdenheter: Akutmottagningen (full data) och Internmedicin (helt
tom). Den tomma är inte en miss — den finns för att (a) demonstrera att
tenant-filtreringen faktiskt isolerar data, och (b) visa "inga läkemedel
än"-vyn i UI:t. Två problem, en miljö.

### 16.5 Två lagerposter under threshold

Paracetamol (saldo 15 / threshold 50) och Salbutamol (3/20) ligger
medvetet under sin varningsnivå. Det betyder att låglagervarningen har
något att visa direkt vid demo — annars hade kärnfunktionen sett tom ut
tills användaren manuellt manipulerat saldon. Demo-realism.

### 16.6 En levererad order med full historik

Order 2 (ORD-DEMO0002) är DELIVERED med sent_at, confirmed_at,
delivered_at från olika datum, plus en matchande StockMovement i
stock_movements. Det visar hela state machine-resan + lagerlogiken i ett
exempel — en användare som öppnar appen ser direkt vad en "färdig"
beställning ser ut.

### 16.7 NOW(6) och version = 0 — små men avgörande detaljer

Tidsstämplar använder NOW(6) (microsecond-precision) för att matcha
DATETIME(6) i schemat — utan (6) hade Hibernate kunnat klaga på precision.
@Version-fälten (StockItem, Order) sätts till 0; NULL hade gjort entiteten
"transient" enligt JPA-specifikationen.

### 16.8 Seed-data är inte test-data

En distinktion som lätt slarvas bort: seed-datan här är för demo och
manuell utveckling — den laddas av Flyway och bor i den verkliga databasen.
Tester (när de byggs) använder Testcontainers med en ren MySQL per
körning; varje test bygger sin egen specifika testdata. Att hålla dem
åtskilda gör testerna oberoende av seedens innehåll.

## 17. Controller- och exception-lagret (28-29 maj)

Backend gjordes HTTP-nåbar för första gången. Tre controllers, en global
exception-hanterare, en minimal säkerhetskonfig — och en lång rad små
beslut som blev synliga när det testades end-to-end med curl.

### 17.1 Controllern är tunn — all logik i servicen

Varje endpoint är 1-3 rader: ta emot, delegera till service, returnera DTO.
Affärslogik i controllern är ett klassiskt antimönster — vi får då
duplicering vid varje ny klient, svårare testbarhet, och regler som glider
isär mellan controller och service. Med tunn controller är det otvetydigt
var "vad får hända?" bor: i service-lagret.

### 17.2 HTTP-statuskoder med semantik

Inte 200 till allt. Konventioner respekterade:
- 200 OK för läsningar och idempotenta uppdateringar (PATCH /threshold,
  POST action-endpoints som send/confirm/deliver/cancel)
- 201 Created när en ny resurs eller händelse skapades (POST på
  /medications, /stock, /stock/{id}/adjustments)
- 204 No Content vid soft-delete (controllern returnerar ingen body)
- 400 Bad Request vid valideringsfel eller bruten affärsregel
- 404 Not Found när resurs saknas i tenant
- 401 Unauthorized hanteras av Spring Security

Statuskoden bär information som klienten kan agera på utan att läsa
meddelandet.

### 17.3 Status sätts aldrig från klient (förstärkning från 15.2)

Genomfört också i URL-design: action-endpoints (POST /send) framför PATCH
med status-värde i body. Klienten kan inte be om en godtycklig status —
bara om en specifik handling, som servicen avgör om är laglig.

### 17.4 Filtrering via query-param framför sub-resource

Första instinkten för "lagerposter under threshold" var en sub-resource
(GET /stock/below-threshold), motiverad av att lågt lager är en distinkt
affärsfunktion. Vid eftertanke ändrades valet till query-param
(GET /stock?belowThreshold=true) — skalbart för framtida filter (form,
search, pagination) utan att uppfinna nya URL-prefix per dimension.
Sub-resources skalar inte längs flera dimensioner, query-params gör det.

Värt att kunna säga rakt ut i intervjun: "första instinkten var
sub-resource, men jag svängde efter att ha tänkt på framtida filter".
Ärlighet om processen slår en torrt motiverad slutprodukt.

### 17.5 PATCH vs POST: attribut vs händelse

Genomgående asymmetri i samma controller, medvetet:

- PATCH /stock/{id}/threshold — ren attribut-ändring, idempotent,
  ingen historikrad
- POST /stock/{id}/adjustments — händelse som skapar en immutabel
  StockMovement, inte idempotent (två POST = två historikrader)

PATCH skulle felaktigt antyda att retry är säker vid nätverksfel — för
adjustments är det farligt eftersom varje retry skapar ny justering. POST
signalerar "händelse, retry på egen risk". URL:en `/adjustments`
substantiverar händelsen — det är *justeringar* du POSTar, inte
*kvantiteter* du PATCHar.

### 17.6 Action-endpoints framför generisk PATCH för statusövergångar

OrderController: POST /send, /confirm, /deliver, /cancel istället för
PATCH /orders/{id} med ny status i body. Motiv (utvecklat från 15.1):

- Statusövergångar är *verb med sidoeffekter*, inte attribut.
  /deliver utlöser saldoökning per orderrad; det är inte "ändra ett fält".
- Action-endpoints är explicit definierade (fyra metoder framför en
  generisk dispatcher) — möjliggör per-action-behörighet
  (@PreAuthorize på en specifik metod) utan refaktorering.
- Matchar Rails-traditionen Medovia jobbar med.

Kostnaden — inkonsekvens med PATCH /threshold på samma controller — är
priset för att domänen *själv* skiljer mellan attribut och händelser.
Inkonsekvens som speglar verkligheten är inte fel sorts inkonsekvens.

### 17.7 Global exception-advice med enhetlig respons-form

@RestControllerAdvice fångar alla exceptions från alla controllers och
översätter till HTTP-svar med samma struktur (ApiError-record):

- NotFoundException → 404
- ValidationException → 400
- MethodArgumentNotValidException → 400 med fieldErrors-array
- Allt annat → 500 med fullt log-spår men generiskt klient-meddelande

Två principer:
1. *Centralisering* — varje controller slipper try/catch.
2. *Asymmetrisk verbosity* — intern observability (loggen) får all
   information; externt svar (klienten) får bara det som behövs för att
   agera. Stack traces ska aldrig läcka via HTTP.

### 17.8 Pedagogiska felmeddelanden

ValidationException från OrderStateMachine bär konkret information:
"Otillåten statusövergång: SENT → DELIVERED (tillåtna från SENT:
[CONFIRMED, CANCELLED])". Inte bara "fel status" — frontend och utvecklare
ser direkt vad som *vore* lagligt. Felmeddelandet formateras från
samma Map som validerar — en sanningskälla, formaterad olika för
intern logik och extern feedback.

Samma princip på saldofel: "saldo kan inte bli negativt (nuvarande 20,
delta -1000)" — felets *kontext* finns med, inte bara dess existens.

### 17.9 SecurityConfig: CSRF av, stateless, basic auth som mellansteg

CSRF avstängt — motiverat eftersom detta är ett stateless JSON-API utan
cookie-baserad session. CSRF skyddar mot att en angripare-webbsida triggar
en autentiserad POST genom användarens cookie; vi har ingen cookie att
utnyttja. Avstängning är *rätt verktyg för rätt hot*, inte "minskad
säkerhet".

Stateless sessions (ingen JSESSIONID) — varje request bär auth i
Authorization-headern. Lämpligt för rent API med separat frontend.

Basic auth är medvetet mellansteg. Riktig RBAC enligt rollmatrisen
(NURSE/PHARMACIST/ADMIN) byggs ut i security-iteration 2: byta ut
CurrentUserProviders platshållare mot SecurityContextHolder-lookup, lägga
till @PreAuthorize på per-action-nivå.

### 17.10 End-to-end-verifiering som dokumenteras

Backend verifierades med curl, inte med automatiska tester (de byggs
senare). Fem scenarier per controller som tillsammans visar att alla lager
samverkar: lista, hämta enskild, skapa giltig, försök skapa ogiltig,
försök bryta affärsregel. Varje misslyckande-scenario bevisar att
exception-pipen → ApiError → korrekt HTTP-status fungerar.

Den manuella verifieringen är inget substitut för automatiska tester men
ger en tidig signal: kedjan håller från MySQL till JSON i båda
riktningarna, redan innan testkod finns.

## 18. Concurrency-testet — bevisning av pessimistic lock (29 maj)

Det här är showcase-testet för Faraz' nyckelfråga om samtidiga uppdateringar.
Pessimistic locking har funnits i StockService-koden sedan dag 2, men en
annotation utan körd verifikation är bara en avsikt. Avsnittet beskriver
hur avsikten gjordes till bevisning.

### 18.1 Testet ska bevisa, inte demonstrera

Skillnaden är viktig. Ett demonstrationstest visar att API:t har rätt form
— "se, här finns en lås-annotation". Ett bevisningstest sätter systemet
under den exakta press som låset *påstås* skydda mot, och mäter resultatet
mot ett deterministiskt förväntat värde.

Konkret: spawna N parallella trådar som var och en gör adjustStock(-1)
mot samma stock_item. Förvänta saldot = startvärde - N. Avvikelse vore
en lost update; exakthet bevisar serialisering.

Värdet av det är att det är *falsifierbart*. Om någon framtida ändring
råkar ta bort @Lock(PESSIMISTIC_WRITE), eller byta till en isolation
level som inte serialiserar, eller introducerar en cache som returnerar
gammalt saldo, så failar testet med ett konkret felmeddelande:
"expected 80 but was 87".

### 18.2 Testcontainers över H2

Att H2 är snabbare är sant och oviktigt. Pessimistic locking testas
inte mot någon databas — det testas mot *en specifik SQL-dialekts*
implementation av `SELECT ... FOR UPDATE`. Produktion är MySQL 8.4.
Testet måste vara MySQL 8.4, annars bevisar det fel sak.

Testcontainers startar en isolerad container per testkörning. Första
körningen tar ~30 sekunder (image-pull + container-start + Flyway),
efterföljande ~15 sekunder (image cachad). Den kostnaden är värd att
betala för att slippa "fungerar i H2, kraschar i produktion".

### 18.3 Integrationstest, inte enhetstest

Concurrency-buggar är inte synliga i unittester med mockade repos.
Mocken returnerar bara det den blivit instruerad att returnera; den
simulerar inte SQL-låsens beteende. Att skriva ett unit-test för
"pessimistic lock fungerar" är cirkellogik: vi testar mockens
beteende, inte verkligheten.

`@SpringBootTest` + Testcontainers ger full Spring-kontext, riktiga
repos, riktig transaktionshantering, riktig MySQL. Det är det enda
sättet att testa just denna typ av problem på ett meningsfullt sätt.

### 18.4 ExecutorService + två CountDownLatch

M�nstret är klassiskt och värt att kunna utantill:

- `ExecutorService` med fast pool av N trådar
- `startLatch = CountDownLatch(1)` — alla trådar väntar på samma signal
- `doneLatch = CountDownLatch(N)` — huvudtråden väntar tills alla är klara
- Varje tråd: `startLatch.await()` → arbete → `doneLatch.countDown()`
- Huvudtråden: `startLatch.countDown()` släpper alla → `doneLatch.await()`

Poängen med att starta alla samtidigt är att maximera sannolikheten
för att de hinner råka göra `SELECT` *innan* den första hinner göra
`UPDATE`. Utan synkstart skulle de troligen serialiseras naturligt
av thread scheduler, och testet skulle passera även utan lås.

20 trådar valdes som balans: tillräckligt många för att lost updates
hade varit högst troliga utan lås, inte så många att MySQL-poolen
eller HikariCP blir flaskhals.

### 18.5 AtomicInteger för räkning

Varje tråd skriver till `successes` eller `failures`. Eftersom dessa
delas mellan trådar måste de vara thread-safe — och `AtomicInteger`
är det enklaste valet för "räknare". `int++` skulle vara en lost
update i sig, paradoxalt nog (read-modify-write som inte är atomär).

### 18.6 Tre assertioner, inte en

Det vore enkelt att bara mäta slutsaldot. Men då skulle ett dolt fel
kunna gömma sig: vad om bara 15 trådar lyckas och 5 silently failar?
Slutsaldot vore då 85, vi förväntade 85 — testet passerar, men
saldot är *fel av rätt anledning*.

Tre assertioner skyddar mot detta:
1. Alla trådar är klara inom timeout
2. Antal lyckade = antal trådar (inga silent failures)
3. Slutsaldot = start - N

Alla tre måste hålla. Om en faller får vi konkret diagnos: "5 trådar
misslyckades" är annan information än "saldot är fel".

### 18.7 Använda seed-care-unit, inte skapa egen

Första iterationen skapade egen CareUnit i testet. Den föll på en
subtilitet: `CurrentUserProvider` är en platshållare som returnerar
hårdkodad `careUnitId=1`. Testdatans care-unit fick id=3.
Tenant-filtreringen blockerade alla anrop — testet rapporterade
"expected: 20, but was: 0" eftersom service inte hittade stock_itemet
under "fel" care-unit.

Fixen: hämta seed-care-unit 1 istället för att skapa egen. Det är
*medveten* koppling till seed-datan, dokumenterad i en kommentar.
Det är ett av två rimliga val; det andra hade varit att override:a
CurrentUserProvider med en testbean. För det här testet var den
enklare vägen rätt — vi testar låsning, inte tenant-logik. Mindre
bewegliga delar = mindre att felsöka.

Värt att kunna säga i intervjun: "den första testkörningen failade
på ett sätt som faktiskt bekräftade tenant-filtreringen — fel
care-unit gav 0 lyckade, vilket är precis vad tenant-isolation ska
göra. Fixet var att medvetet använda seed-data".

### 18.8 AbstractIntegrationTest som basklass

Testcontainers-setupet är generiskt — varje integrationstest behöver
samma MySQL-container, samma Spring-kontext, samma Flyway-migration.
Att duplicera det per testfil vore antimönster.

Lösningen: en `@SpringBootTest`-annoterad abstrakt basklass. Sub-
klasser ärver containern och kontexten utan att veta hur det fungerar.
Förbättrad återanvändning, mindre boilerplate, och ett ställe att
ändra om containern behöver tweakas.

`static`-blocket startar containern *innan* Spring-kontexten startar,
vilket är nödvändigt — annars försöker Spring ansluta till en
databas som inte finns. `@DynamicPropertySource` skriver in den
slumpmässiga port containern fick i Spring-kontexten *innan*
HikariCP försöker etablera pool.

### 18.9 Vad testet *inte* bevisar

Ärlighet om gränserna:

- Det testar inte att leveransflödet (OrderService.deliver) som också
  använder pessimistic lock fungerar korrekt. Det vore ett separat
  test som spawnar samtidiga deliver-anrop på orders med rader som
  pekar på samma stock_item.
- Det testar inte deadlock-scenarier (två trådar låser i olika
  ordning). MediTracks domän har bara ett relevant lås per
  transaktion, så deadlocks är svåra att konstruera — men ett test
  som *försöker* hade gett ytterligare trygghet.
- Det testar inte vad som händer vid timeout — `@Lock` har en default
  timeout i MySQL (InnoDB lock wait, 50s). Vi testar inte gränsfallet.

I en produktionskontext med mer tid skulle dessa kompletteras. För
case-syftet är det här testet det viktigaste, och det är det vi har.

## 19. Frontend — stackval och motiveringar (29 maj eftermiddag)

Backend nådde funktionell färdighet i förmiddags (concurrency-testet
grönt, alla controllers på plats, designdokumentet komplett t.o.m.
avsnitt 18). Återstående arbete: ett UI som demonstrerar att API:t
faktiskt går att använda av en människa.

### 19.1 Övervägd: Thymeleaf

Första instinkten — min huvudsakliga frontend-erfarenhet ligger på
server-renderade vyer i Spring-stacken, och Thymeleaf vore klart
snabbast att leverera för någon med min bakgrund.

Avvisat av två skäl, från viktigast till minst viktigt:

**1. Backend är redan designad som rent JSON-API.** SecurityConfig
sätter `SessionCreationPolicy.STATELESS`, CSRF är avstängt med
motivering "stateless JSON-klienter har inga sessions att skydda",
controllers returnerar JSON via `@RestController`. Att byta till
Thymeleaf hade krävt mig att om-motivera dessa val — sessions skulle
plötsligt behövas, CSRF skulle behöva återinföras med token-strategi,
hela avsnitt 17.9 om CSRF-disable skulle behöva skrivas om. Det är
inte omöjligt men det är mycket dokumenteringsskada för noll teknisk
vinst i case-kontext.

**2. Caset signalerar JS-frontend.** Texten lyder ordagrant "Vi
arbetar med TypeScript, React, Go och Ruby on Rails – känner du
igen dig är det ett plus." TypeScript och React står först. Att
leverera Thymeleaf vore att medvetet välja bort signalen om att vi
kan jobba i deras stack. I produktion på Medovia hade Thymeleaf
varit fullt rimligt för en intern app — i intervjun är det fel
signal.

Värt att kunna säga i intervjun: "Jag är inte React-utvecklare i
första hand — min frontend-erfarenhet ligger på Thymeleaf. Jag
övervägde det aktivt, men valde React av de två skälen ovan."
Det är ärligt, det visar att jag tänkte på trade-offen, och det
positionerar React-leveransen som ett medvetet val snarare än
default.

### 19.2 Vald: Vite + React + TypeScript

**Vite** över Create React App eftersom CRA inte längre underhålls
aktivt, Vite är snabbare och har lägre setup-kostnad. Next.js avvägdes
men förkastades — Next.js är ett ramverk *runt* React med SSR/SSG-
opinioner som inte tillför något här. Vi vill ha en SPA mot ett
externt API; Vite är det enklaste sättet att leverera det.

**TypeScript** över ren JavaScript av samma skäl som backend valde
Java med strikt typning: kompilatorhjälp över runtime-överraskningar.
En person som kommer från Java får dessutom en mjukare landning i
TS än i otypad JS.

**React** över Vue/Svelte/Angular eftersom caset namnger det.
För MediTracks komplexitet (fyra sidor, hanterbar state) är alla
fyra ramverken överkomplexa — valet är estetiskt, inte tekniskt,
och Medovias stack avgör estetiken.

### 19.3 Vald: Tailwind CSS v3 (inte v4)

**Tailwind** över komponentbibliotek (MUI, shadcn/ui) eftersom:

- Komponentbibliotek låser oss till deras estetik och kräver
  inlärning av deras API. Tailwind är direkta utility-klasser i JSX —
  inlärningskostnaden är "lär dig klassnamnen efter hand".
- För fyra sidor i ett intervjucase är ett komponentbibliotek
  overkill. Vi vill *visa designtänk*, inte konfigurera ramverk.
- Caset nämner "stressade sjuksköterskor" och "tydligt UI" som
  bedömningskriterier. Det är inte krav på fancy komponenter — det
  är krav på *läsbarhet*, vilket Tailwind med Slate-färgskalan
  levererar med minimal friktion.

**v3 över v4** — Tailwind 4 är senaste men har ny syntax och
konfigurationsmodell (CSS-baserad istället för JS). All
dokumentation och alla exempel jag hittar är fortfarande v3. För
ett case där jag ska kunna förklara mina val är "stabil och väl-
dokumenterad" ett starkare argument än "bleeding edge". I produktion
hade jag uppgraderat när tooling och dokumentation är ikapp.

### 19.4 Vald: fetch + custom hook (inga externa deps för API)

**fetch** över axios eftersom fetch är inbyggt i alla moderna
browsers — ingen extra dep. Axios fördelar (interceptors,
request-cancellation) är värdefulla i större appar men är overkill
för fyra sidor mot ett internt API.

**Custom hook** över TanStack Query / SWR. Dessa bibliotek är
fantastiska för cache, refetch-logik, optimistic updates osv. För
fyra sidor med tre-fyra endpoints är det overkill. En enkel hook
som returnerar `{data, loading, error}` täcker behovet.

Avvägning: om appen växte till 20+ sidor med komplexa cache-behov
hade TanStack Query varit rätt val. För MediTracks scope vore det
"komplexitet utan motsvarande nytta".

### 19.5 Vald: React Router

Standardvalet. Det finns inga konkurrenter på samma erfarenhetsnivå
— React Router är vad alla använder, dokumentationen är överlägsen,
inlärningskostnaden minimal. Inga övervägningar att rapportera.

### 19.6 Sidor och scope (medveten begränsning)

Fyra sidor:
1. `/medications` — läkemedelsregister (lista, sök, lägg till)
2. `/stock` — lager med varning för låga nivåer
3. `/orders` — beställningslista med statusbadges
4. `/orders/:id` — orderdetalj med state machine-actions

Plus en navbar.

**Medvetet INTE med**: användarhantering, ATC-redigerare,
audit log-vy, dashboard, mörkt tema, multispråk, drag-and-drop,
animationer. Vart och ett av dessa skulle vara *möjligt* men skulle
dränera tid från README, manus, och presentationsträning.

Värt att kunna säga: "Jag valde fyra sidor som täcker kärnflödet
från caset (läkemedel, lager, beställning). Allt annat hade varit
extras som inte demonstrerar något nytt. Caset säger explicit att
välmotiverad halvfärdighet slår okommenterad fullständighet."

### 19.7 Repo-struktur: monorepo

Frontend bor i `frontend/` under samma repo som backend, inte i
ett separat repo. Skäl:

- En klon → hela lösningen. Faraz slipper navigera mellan två repos.
- En git-historia berättar hela utvecklingsförloppet (backend dag
  1-2, frontend dag 3, polish dag 4-5).
- En README täcker både delar.
- För en intervjucase är detta enklast. För produktion hade
  separata repos varit värda att överväga (oberoende deploys,
  separata behörigheter).

### 19.8 CORS — kommer behöva läggas till

Frontend kör på `http://localhost:5173`, backend på
`http://localhost:8080`. Det är olika origins, så CORS-konfiguration
behövs i SecurityConfig för att browserns same-origin-policy ska
tillåta API-anrop.

Lösning planerad: en `WebMvcConfigurer`-bean eller motsvarande i
SecurityConfig som tillåter `http://localhost:5173` för dev. För
produktion skulle vi tillåta deploy-URL:en istället eller köra
frontend och backend bakom samma reverse proxy så CORS inte behövs.

### 19.9 Vad denna sektion *inte* täcker

Sektionen handlar om stackval, inte implementation. Vart efter
sidorna byggs kommer specifika tekniska val (hur state hanteras
mellan sidor, hur formulär valideras, hur loading-states visas)
få egen dokumentation i senare avsnitt — eller, om tiden tryter,
i README under "kända brister och vad jag hade gjort med mer tid".

## 20. Frontend-implementation — fyra sidor och en princip (29 maj eftermiddag)

Avsnitt 19 dokumenterade stackvalen. Detta avsnitt dokumenterar vad
som faktiskt byggdes under fredagseftermiddagen och vilka principiella
val som styrde implementationen.

### 20.1 Custom hook över bibliotek

Första frågan var hur API-anrop ska hanteras. Övervägt: TanStack Query,
SWR, axios med interceptors. Valt: en egen useFetch-hook på cirka 30
rader.

Motivering: för fyra sidor med ett antal endpoints är ett dataladdnings-
bibliotek overkill. TanStack Query är ett utmärkt val när cache,
optimistic updates och bakgrundsrefetch är reella behov - här är de
det inte. En egen hook gör koden lättare att läsa för någon som inte
kan TanStack Query, och den är lätt att förstå inifrån.

Hooken är generisk via TypeScript - `useFetch<Medication[]>(path)` ger
typsäker data utan att hooken vet något om domänen. Det är samma
princip som Spring Data JPA's typade repositories på backend-sidan.

Avvägning: i en större app skulle hooken sakna features som
automatisk retry, dedupering av samtidiga requests, och cache mellan
komponenter. Det är medvetna avgränsningar - när appen växer byter vi
hook mot TanStack Query och får dessa features gratis.

### 20.2 Defensiv UI över förtroende

OrderDetailPage visar bara *lagliga* state machine-actions. En SENT-
order har "Bekräfta" och "Avbryt" - inte "Leverera" (som hade krävt
CONFIRMED först) och inte "Skicka" (redan gjort). Terminala tillstånd
(DELIVERED, CANCELLED) visar "Inga åtgärder tillgängliga".

Värdet: användaren kan inte be om något olagligt. Backend's
OrderStateMachine kastar ändå ValidationException om man försökte,
men det är dålig UX att visa knappar som garanterat ger fel.

Trade-offen: frontend duplicerar reglerna från backend. Om backend
lägger till en ny övergång (säg DRAFT -> CANCELLED via reject-knapp)
måste frontend hållas i synk manuellt. I produktion skulle backend
exponera en availableTransitions-fält på order-DTO:n så frontend
bara renderar det den får - men det är extra komplexitet vi inte
behövde för fyra sidor.

### 20.3 Sub-resource-actions framför PATCH

OrderController valde sub-resource-endpoints (POST /orders/:id/send,
/confirm, /deliver, /cancel) framför att skicka PATCH med ny status.
Avsnitt 17.7 dokumenterar varför på backend-sidan.

Frontend belönar det valet. performAction-funktionen tar bara en
endpoint-sträng som argument:

    performAction("send")
    performAction("confirm")
    performAction("deliver")

Hade vi haft PATCH med ny status hade vi behövt skicka en JSON-body
med status-värdet, hantera content-type-headers, och konstruera
övergången på frontend-sidan. Sub-resource gör att frontend bara
behöver veta *vilken action* - inte hur den implementeras.

### 20.4 refreshKey-trick för refetch efter mutation

useFetch refetchar när dess path-argument ändras. Efter en action
vill vi hämta orderns nya status från servern (vi litar inte på att
gissa).

M�nstret:

    const [refreshKey, setRefreshKey] = useState(0);
    const { data } = useFetch(`/api/orders/${id}?_=${refreshKey}`);

Efter en lyckad action: `setRefreshKey(k => k + 1)`. Path ändras,
hooken refetchar.

Det är ett enkelt mönster utan att skriva en refetch-funktion eller
en mer komplex hook. Server-sidan ignorerar query-paramen `_` - den
finns bara för att tvinga React Routern att se en ny path.

I produktionskod skulle jag exponera en refetch-funktion från hooken
direkt - cleaner än URL-tricket. Men det här fungerar och är enkelt
att förstå.

### 20.5 Optimistic locking konfronteras inte än

Backend har @Version på Order. Om två användare bekräftar samma
order samtidigt får den andra OptimisticLockException. Frontend
visar i nuläget bara felmeddelandet som plain text om det händer -
ingen specifik UX för "någon annan redan ändrat denna".

Förbättring: om vi får OptimisticLockException skulle vi automatiskt
refetcha och visa "Ordern uppdaterades av någon annan - kontrollera
den nya statusen". Inte gjort eftersom det är extremt osannolikt i
en intern app med fyra sidor, men värt att nämna i intervjun när
Faraz frågar om concurrent updates.

### 20.6 Tailwind-färgsemantik

Färgvalen är medvetna, inte estetiska:

- Slate (grå): default-text, bakgrunder, OK-status
- Amber (orange): varningar, threshold-överträdelser, "Under tröskel"
- Emerald (grön): lyckade slutförda actions, "OK", "Levererad"
- Blue (blå): pågående, "Skickad"
- Indigo (mörkblå): nästan klart, "Bekräftad"
- Red (röd): fel, "Avbruten", "Kunde inte hämta"

Det följer etablerad UI-konvention: gult-orange för "uppmärksamhet
behövs", grönt för "klart", rött för "fel". En sjuksköterska kan
färgkoda en lagervy på en sekund utan att läsa text. Caset belönar
denna typ av UX-design under "intuitivt flöde".

### 20.7 Saknas medvetet

Funktioner som hade varit triviala att lägga till men hoppades över
för att skydda README- och presentationstid:

- POST-formulär (lägga till läkemedel, skapa beställning)
- PUT-formulär (redigera threshold)
- Sökning och filtrering i medications-listan
- Sortering av kolumner
- Bekräftelsedialog innan Avbryt-action (just nu klick = action)
- Autentisering och rollvy
- Toast-notifieringar för success/failure

Var och en hade tagit 30-60 minuter och inte tillfört nytt
arkitektoniskt. Värt att kunna säga: "Jag valde fyra sidor som
visar bredden av problemet snarare än många features som visar
djupet av en sida."

## 21. AI-agenten — Gemini-driven påfyllning (30 maj)

Avsnitt 11 planerade AI-funktionen ("AI som komponent, inte magi"). Detta avsnitt
dokumenterar vad som faktiskt byggdes, och de beslut som uppstod på vägen.

### 21.1 Vald funktion: påfyllningsagent, inte chatbot

Caset gav tre kandidater: ATC-kategorisering, prediktiv påfyllning, eller chatbot.
Valt: en agent som läser lagerposter under tröskel och föreslår påfyllnings-
kvantiteter. Skälen:

- Den knyter an till kärndomänen (lagerlogik, threshold) istället för att vara en
  påklistrad extra. Agenten använder samma `findBelowThreshold` som lagervarningen.
- Den producerar något *granskningsbart* — ett orderutkast, inte ett fritextsvar.
  Det gör säkerhetsgränsen konkret (se 21.3).
- En chatbot hade varit mer demo-spektakulär men mindre intressant arkitektoniskt:
  den hade levt vid sidan av domänen, inte i den.

### 21.2 Provider-abstraktion med @Primary och fallback

Kärnan i designen: ett `ReorderSuggestionProvider`-interface med två
implementationer.

- `RuleBasedReorderProvider` — deterministisk heuristik (2× tröskel minus
  nuvarande saldo). Förutsägbar, gratis, alltid tillgänglig.
- `GeminiReorderProvider` — `@Primary`, ringer Gemini direkt. Får den
  regelbaserade providern injicerad som intern fallback.

`@Primary` löser Springs tvetydighet (två bönor av samma interface) elegant:
`ReorderSuggestionService` injicerar bara interfacet och får Gemini. Det är
exakt det verktyg `@Primary` finns för — "flera kandidater, välj den här som
default". Bra att kunna peka på i intervjun.

Resiliensen är inbyggd, inte påklistrad: saknas API-nyckel, eller failar anropet
(timeout, HTTP-fel, trasig JSON) → faller Gemini-providern tillbaka på den
injicerade regeln. Appen fungerar ALLTID, med eller utan Gemini. `sourceLabel()`
rapporterar vilken väg som faktiskt användes ("Gemini (gemini-2.5-flash)" vs
"Regelbaserad fallback") — ärlighet om var svaret kom ifrån, hela vägen ut i UI:t.

Detta är Pastoralens läxa applicerad: tidiga K3-prompten lät modellen gissa vid
saknad data → falska bokslut. Här gissar agenten aldrig fritt — den får exakt de
poster den får föreslå för, och fallbacken är deterministisk.

### 21.3 Säkerhetsgräns: agenten föreslår, människan beslutar

Det viktigaste designvalet. Agenten skapar en order i status **DRAFT** — den
skickar aldrig själv. En människa måste granska utkastet och trycka Skicka. Det är
separation of duties (avsnitt 5, HSLF-FS 2017:37) applicerat på AI: precis som en
sjuksköterska som beställt inte får bekräfta sin egen order, får en AI som föreslår
inte verkställa sitt eget förslag.

Konkret i UI:t: utkastet öppnas med status Utkast, åtgärder Skicka/Avbryt, och
källan utskriven ("Automatiskt påfyllningsutkast (Gemini ...)"). Användaren ser att
det är AI-genererat, ser vad som föreslogs och varför, och fattar beslutet.
Verifierat live 30 maj.

### 21.4 Väg B: ringa Gemini direkt, inte via mellantjänst

Pastoralen ringer sin LLM via en intern `AIClient`-abstraktion. Här valdes att
ringa Gemini direkt med `RestTemplate` (konsekvent med Pastoralens HTTP-stil). Skäl:
för en funktion behövs ingen mellantjänst-abstraktion ännu — det vore föregripande
generalisering. Provider-interfacet (21.2) ÄR abstraktionen; den sitter på rätt
nivå (utbytbar strategi), inte på transportnivå. Vid fler AI-funktioner vore en
gemensam Gemini-klient motiverad — noteras som framtida steg.

Modell `gemini-2.5-flash` (via `GEMINI_MODEL`, fallback hårdkodad). Gemini = Medovias
LLM, samma logik som React-valet: bygg i deras stack.

### 21.5 Defensiv JSON-parsing — modellen får inte hitta på läkemedel

Prompten ger Gemini exakt de poster som ligger under tröskel (medicationId, saldo,
threshold) och tvingar JSON-svar via `responseMimeType: "application/json"`.
Svaret parsas defensivt:

- Texten plockas ur Geminis `candidates[0].content.parts[0].text`-struktur
- Eventuella markdown-fences rensas trots instruktionen
- Svaret mappas mot de poster vi SKICKADE — okänt medicationId ignoreras,
  negativ/noll kvantitet ignoreras
- Tomt eller ogiltigt svar → exception → fallback

Säkerhetspoängen: läkemedel och saldon kommer ALLTID från databasen. Modellen
bidrar bara med en kvantitet per redan-känd post. Den kan inte introducera
påhittade läkemedel i en beställning — även om den hallucinerar ett id, finns det
inte bland de skickade posterna och förkastas.

### 21.6 Verifierat: regel vs LLM, faktiska siffror

Live-testet 30 maj visade skillnaden konkret:

| Läkemedel | Regel (2× tröskel) | Gemini |
|---|---|---|
| Paracetamol (15/50) | 85 | 40 |
| Salbutamol (3/20) | 37 | 27 |

Och — det intressanta — Gemini graderade språket: Paracetamol fick "Lågt saldo, fyll
på till en bit över tröskelvärde", Salbutamol "Kritiskt lågt saldo, fyll på rejält".
Modellen uppfattade att Salbutamol (15% kvar) är proportionellt mer akut än
Paracetamol (30% kvar). Regeln applicerar samma formel oavsett hur akut läget är;
LLM:en resonerar situationsmedvetet. Det är hela poängen med att ha båda: regeln är
förutsägbar och gratis, Gemini är nyanserad men kostar ett API-anrop och kan vara
nere — därav fallbacken.

### 21.7 Medvetna bortval (AI)

- **Ingen prompt caching** — för låg volym (en knapptryckning då och då), ingen vinst.
  Diskuteras men implementeras inte (samma hållning som avsnitt 11).
- **Ingen automatisk schemaläggning** — agenten triggas manuellt via knapp, inte av
  ett cron-jobb. Att låta den skapa utkast automatiskt nattetid vore en rimlig
  utökning, men då blir granskningsgränsen (21.3) viktigare, inte mindre.
- **Ingen finjustering av prompten per läkemedelstyp** — narkotikaklassade
  (`controlled_substance`) borde rimligen ha striktare påfyllningslogik (dubbel-
  signering, mindre kvantiteter), men det är domänfördjupning utöver caset.
- **Resultatet persisteras inte som AI-metadata** — vi sparar utkastet som vanlig
  order med en note, inte med strukturerad "föreslagen av modell X, prompt-version Y,
  vid tidpunkt Z". I produktion vore det spårbarhet värd att ha (audit för AI-beslut).

## 22. Autentisering och RBAC (31 maj)

Avsnitt 17.9 lämnade auth som "medvetet mellansteg" och avsnitt 14.3 / 7
beskrev `CurrentUserProvider` och AuditorAware som platshållare "tills riktig
auth finns". Detta avsnitt dokumenterar när den byggdes — och varför
abstraktionerna från dag 1 betalade sig.

### 22.1 Beslutet: Basic auth + genomdriven RBAC, inte JWT

Casets fråga 4 ("hur skulle du lägga till autentisering?") kunde besvarats
med ett resonemang. Jag valde att bygga det istället, och landade i HTTP
Basic mot användare i egen databas, med genomdriven RBAC.

Basic auth över JWT/sessions, av ett skäl som redan var avgjort i
arkitekturen: API:t är stateless (avsnitt 17.9, `SessionCreationPolicy.STATELESS`,
CSRF av). JWT hade lagt till token-utgivning, signaturvalidering och
refresh-logik; sessions hade krävt JSESSIONID och återinförd CSRF — och
rivit motiveringen i 17.9. Basic auth bär credentials i `Authorization`-
headern per request, vilket passar ett stateless API utan att lägga till
infrastruktur som domänen inte behöver.

*Intervjupoäng:* "Basic auth för ett internt verktyg med användare i egen
databas, körbart fristående. OAuth2 mot en IdP — som Pastoralen kör — hade
jag valt i en publik flerorganisationskontext, men det vore overkill här.
Rätt verktyg för rätt skala, samma hållning som genom hela projektet."

### 22.2 Riktiga användare: BCrypt + UserDetailsService

Seed-användarna fick BCrypt-hashade lösenord i `R__seed.sql` (samma hash på
alla tre — BCrypt saltar, så identiskt klartextlösenord ger ändå olika
verifiering). Lösenordet exponeras aldrig; hashen ligger bara i seed.

`MeditrackUserDetailsService` implementerar Springs `UserDetailsService`,
läser via `findByEmailAndDeletedAtIsNull` (soft-delete-respekt — en
borttagen användare kan inte logga in) och mappar `Role`-enumen till
`ROLE_`-prefixad authority. Prefixet läggs på här, en gång, så att
`hasRole('PHARMACIST')` funkar utan att prefixet läcker in i domänkoden
(Pastoralens läxa: `hasRole` utan `ROLE_`-prefix i annoteringarna).

### 22.3 SecurityConfig — stateless, CSRF av, CORS för Vite

`@EnableMethodSecurity` för `@PreAuthorize`. En `BCryptPasswordEncoder`-böna.
`.anyRequest().authenticated()` med `httpBasic()`. CSRF av och STATELESS
står kvar oförändrade från 17.9 — de var rätt redan, och auth-bygget rev
dem inte.

Två konkreta tillägg för att frontend ska fungera: `OPTIONS` tillåts utan
auth (CORS preflight skickar ingen `Authorization`-header, så annars hade
varje skrivanrop blockerats före själva anropet), och CORS tillåter
`localhost:5173` med `allowCredentials` (Basic-headern räknas som
credential).

### 22.4 CurrentUserProvider: platshållaren betalade sig

Det här är guldmomentet. `CurrentUserProvider` (14.3) returnerade fasta
id:n (1/1) "tills auth finns". Nu byttes den interna implementationen mot
en `SecurityContextHolder`-lookup: hämta inloggad e-post → `findByEmailAnd
DeletedAtIsNull` → verklig `userId` och `careUnitId`.

**Ingen service ändrades.** Hela service-lagret anropar fortfarande
`currentUser.getCurrentCareUnitId()` / `getCurrentUserId()` precis som
förut — det visste aldrig om att användaren var hårdkodad, och vet inte nu
att den kommer från säkerhetskontexten. Beslutet "vem är inloggad?" var
isolerat till ett ställe från dag 1, exakt som avsnitt 14.3 lovade. Samma
gäller AuditorAware (avsnitt 7): audit-fälten fylls nu med den riktiga
användaren utan att någon entitet rördes.

*Intervjupoäng:* "Jag byggde abstraktionen innan jag hade auth, så att
inkopplingen blev ett byte på ett ställe — inte en refaktorering genom hela
service-lagret. Det är skillnaden mellan att designa in en söm och att
bolta på säkerhet efteråt."

### 22.5 Separation of duties i två lager — rätt regel på rätt lager

RBAC genomdrevs på två nivåer, eftersom regeln har två olika karaktärer:

- **Rollkrav (roll-lager):** `@PreAuthorize("hasRole('PHARMACIST')")` på
  confirm-endpointen i `OrderController`. En NURSE får över huvud taget
  inte bekräfta — det är en statisk rollregel, och Spring Security är rätt
  ställe för den. Avvisas med 403.
- **Personregel (domän-lager):** även en apotekare får inte bekräfta en
  order *hen själv skickat* (HSLF-FS 2017:37). Det är inte en rollfråga —
  det beror på *vem* som skickade *just den ordern*, vilket bara service-
  lagret vet. I `OrderService`, i CONFIRMED-grenen:
  `if (actorId.equals(order.getSentBy())) throw new SeparationOfDuties
  Exception(...)`. (`actorId.equals(sentBy)`, inte tvärtom — undviker NPE
  om `sentBy` skulle vara null.)

Poängen är att rollkravet är generiskt och statiskt (hör hemma i
ramverket), medan person-regeln är dynamisk och domänspecifik (hör hemma i
domänen). Att lägga person-regeln som en `@PreAuthorize`-SpEL hade tvingat
in databasfrågor i en annotering — fel lager. Två regler, två lager, varje
på sin rätta plats.

### 22.6 Eget SeparationOfDutiesException — varför inte AccessDeniedException

Tre alternativ övervägdes för person-regeln i service: (A) kasta Springs
`AccessDeniedException`, (B) en generisk `ValidationException`, (C) ett eget
domänundantag. Valt: C.

Skälen: (1) **Lagerseparation** — service-lagret ska inte importera Spring
Securitys undantag; det knyter domänlogiken till säkerhetsramverket. (2)
**Semantik** — det är inte ett valideringsfel (indata är giltig), det är en
behörighetsregel; ett eget namn säger exakt vad som hände. (3)
**Självdokumenterande** — `SeparationOfDutiesException` i en stacktrace
eller en kodväg berättar för nästa utvecklare *vilken regel* som brast,
utan kommentar.

### 22.7 GlobalExceptionHandler — och 403→500-fällan

Två nya hanterare i `@RestControllerAdvice`:
- `AccessDeniedException` (från `@PreAuthorize`) → 403, generiskt meddelande
  (läcker inte vilken roll som krävdes).
- `SeparationOfDutiesException` → 403, men släpper fram `ex.getMessage()`
  ("Du som skickade beställningen får inte själv bekräfta den") — här är det
  *pedagogiskt* att säga varför (samma princip som 17.8).

Kritisk ordning: båda måste ligga *före* catch-all-hanteraren. Catch-all
fångar allt och ger 500 — ligger den först skulle ett korrekt 403 bli ett
felaktigt 500. En subtil bugg som bara syns när man faktiskt testar nekande
(vilket vi gjorde, se 22.11).

### 22.8 /api/me — rollen till frontend utan att läcka

`MeController` (GET `/api/me`) returnerar inloggad användare via
`CurrentUserProvider` → `MeResponse` (record: id, name, email, role).
`from(User)` exponerar medvetet inte lösenordshashen — samma DTO-disciplin
som 13.2. Frontend behöver rollen för att veta vilka åtgärder den ska visa
(22.10), och hämtar den härifrån istället för att gissa.

### 22.9 Frontend-auth: central apiFetch — refaktorering vid rätt tillfälle

Frontend hade tidigare `fetch` på spridda ställen (`useFetch`, `performAction`
i OrderDetailPage, reorder-knappen i StockPage). Auth-kravet — varje anrop
behöver `Authorization: Basic`-headern och en samlad 401/403-hantering —
gjorde det till rätt tillfälle att samla det.

`api.ts` exporterar en central `apiFetch<T>(path, options)` som lägger på
Basic-headern (`btoa(email:password)`), sätter JSON-content-type, och
hanterar svaren: 401 → `onAuthError` (logga ut), 403 → kastar serverns
`body.message` (så NURSE ser "Du saknar behörighet", inte en rå status).
`useFetch`, `performAction` och reorder-knappen anropar nu `apiFetch`
istället för rå `fetch` — `API_BASE`-konstanten försvann på köpet.

Credentials hålls i minnet på modulnivå, **inte i localStorage**. Det är ett
medvetet val: en omladdning loggar ut (ingen kvardröjande session i
webbläsarlagret), vilket är rimligt för ett internt verktyg och undviker att
credentials ligger kvar på en delad dator. `AuthContext` (`AuthProvider` +
`useAuth`-hook) håller `login()` (sätter credentials → anropar `/api/me` →
sparar användaren; 401 rensar och kastar), `logout()`, och registrerar
auth-error-handlern vid mount. `LoginPage` är ett formulär som listar
demo-inloggningarna. `App` lindar allt i `AuthProvider`; `AppContent` läser
`useAuth()` och visar `LoginPage` om ingen är inloggad, annars appen.

### 22.10 Rolldöljning i UI — UX, inte säkerhet

`OrderDetailPage` filtrerar bort "Bekräfta"-åtgärden om
`user?.role !== "PHARMACIST"`. Det är medvetet *bara* UX: att inte visa en
knapp som ändå skulle ge 403 är god känsla, men det är inget skydd —
backend nekar oavsett vad klienten visar. Samma princip som den defensiva
UI:n i 20.2 (visa bara lagliga actions), nu utökad till roller.

*Intervjupoäng — det fina demo-momentet:* logga in som Erik (PHARMACIST),
tvinga fram bekräfta-anropet på en order han själv skickat → 403 från
person-regeln (22.5), trots att han har rätt roll och knappen visas. Det
bevisar live att backend är sista vakten och att de två lagren är
oberoende.

### 22.11 Verifierat — curl och webbläsare

Bevisat end-to-end (alla gröna):
- Ingen login → 401. Sara + demo1234 → 200. Fel lösenord → 401.
- Sara (NURSE) POST /confirm → 403 (roll-lagret).
- Erik (PHARMACIST) → 200 CONFIRMED. Erik skapar order, skickar den själv,
  försöker bekräfta egen → 403 (person-lagret). Två oberoende 403, båda
  regler bevisade.
- `/api/me`: Sara → NURSE, Erik → PHARMACIST.
- I webbläsaren: Sara ser ingen Bekräfta-knapp på en SENT-order; Erik ser
  den. Perfekt kontrast — rolldöljningen fungerar live.

### 22.12 Medvetna bortval (auth)

- **HTTP Basic, inte token/session.** Rätt för internt, stateless API. I
  produktion över publika nät: HTTPS + sannolikt token-/sessionsbaserad
  inloggning för webbklienten (Basic skickar credentials vid varje request).
- **Ingen kontolåsning / rate limiting** vid upprepade misslyckade
  inloggningar. En produktionshärdning, noterad i README:s Kända brister.
- **Ingen UI för användaradministration.** Användare kommer från seed;
  skapa/spärra sker på databasnivå. Att bygga ett admin-UI vore en egen
  funktion utöver caset.
- **Credentials i minnet, inte localStorage.** Omladdning loggar ut.
  Medvetet — undviker kvardröjande credentials, till priset av att man får
  logga in igen efter refresh. För ett internt verktyg rätt avvägning.
