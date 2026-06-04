# MediTrack — Designbeslut

Kort sammanfattning av de viktigaste tekniska valen och motiveringen bakom dem. Fördjupning finns i koden och commit-historiken.

## 1. Datamodell

Åtta entiteter: CareUnit (tenant-rot), Medication, StockItem, Order, OrderLine, User, samt de immutabla StockMovement och AuditLog.

Tre val värda att lyfta:

- **StockItem är skild från Medication.** Läkemedlet är *katalogdata*; saldo och tröskel är *operativ* data som ändras i en annan takt och skiljer sig per vårdenhet (en akutmottagning och ett rehab-boende har olika förbrukning och därmed olika tröskel för samma läkemedel). Hade saldo legat på Medication hade modellen brutit ihop så snart fler än en vårdenhet finns — vilket är casets uttryckliga skalbarhetsfråga.
- **OrderLine är en egen entitet** som pekar på Medication. En beställning kan innehålla flera läkemedel med varsin kvantitet; raden är den naturliga platsen för kvantitet och radnivå-data. Rätt StockItem löses vid leverans via `(medication_id, care_unit_id)`.
- **Tenant-isolering i botten.** Alla domänentiteter hänger på CareUnit. Service-lagret sätter vårdenheten från den inloggade användaren, aldrig från en parameter som klienten väljer. Varje enhet ser bara sin egen data — även admin (Patientdatalagen).

Medvetet bortvalt: schema-per-tenant (tenant-FK + filter räcker), global läkemedelskatalog (bryter isoleringen), lot/batch-spårning med utgångsdatum (kräver omdesign, ligger i MDR-territorium).

## 2. Concurrency — olika strategier för olika konfliktprofiler

Caset frågar uttryckligen vad som händer när två sjuksköterskor beställer samtidigt. Svaret är att olika operationer har olika konfliktprofiler och därför olika lösning:

- **Optimistisk låsning (`@Version`) på Order och StockItem.** Hibernate lägger `WHERE version = <läst värde>` på varje uppdatering; krockar två uppdateringar failar den andra istället för att tyst skriva över den första (lost update). Förstaförsvaret.
- **Pessimistisk låsning på den saldokritiska skrivningen vid leverans.** `SELECT ... FOR UPDATE` på StockItem (`@Lock(PESSIMISTIC_WRITE)`). Raden låses tills transaktionen committar, så samtidiga leveranser serialiseras istället för att tävla och tvinga fram en retry-loop.

Varför inte samma överallt: pessimistisk låsning kostar väntan och har deadlock-risk; optimistisk kostar retries vid konflikt. Vid den integritetskritiska saldouppdateringen är en kort, smal låsning att föredra framför en retry-loop — för användaren är "det tog en halv sekund till" bättre än "försök igen".

Låsningen är verifierad med ett concurrency-test: 20 trådar gör samtidiga saldojusteringar mot en riktig MySQL (Testcontainers), och alla 20 serialiseras korrekt — saldot räknas ned utan tappade uppdateringar. Det skiljer ett *påstående* om låsning från ett *bevis*: testet dog först på en orelaterad sak (SecurityContext propageras inte till trådpoolens trådar), vilket fick mig att skilja "testet är trasigt" från "är låsningen korrekt" — koden var sund, testuppställningen var det inte.

## 3. Säkerhet — RBAC med separation of duties i två lager

Rollbaserad åtkomstkontroll är ett regulatoriskt krav i vården (HSLF-FS 2017:37), inte ett tillägg — därför inbyggt från start, inte påbolt-at. Regeln har två karaktärer och ligger därför i två lager:

- **Rollkravet är statiskt** — `@PreAuthorize(hasRole('PHARMACIST'))` på bekräfta-endpointen. En sjuksköterska får över huvud taget inte bekräfta; det hör hemma i ramverket.
- **Personregeln är dynamisk** — även en apotekare får inte bekräfta en order *hen själv skickat*. Det beror på vem som skickade just den ordern, vilket bara service-lagret vet, så regeln ligger där som ett eget domänundantag (`SeparationOfDutiesException`), inte i en annotering.

Tenant-isolering trumfar roller: även admin ser bara sin egen enhets data. Rolldöljning i UI:t är UX, inte säkerhet — backend genomdriver regeln oavsett vad klienten visar, och svarar 403 (inte 500) vid otillåten åtgärd.

## 4. Stack — modulär monolit

Java 21, Spring Boot 3.4.5, MySQL 8.4, Flyway, Hibernate/JPA, Testcontainers, Spring Security. Frontend: Vite + React + TypeScript + Tailwind.

- **Modulär monolit, inte microservices.** Med ett team och en avgränsad domän hade microservices varit over-engineering. Prioriteringen var tydlig separation *inom* en monolit (controller → service → repository) som faktiskt går att köra och resonera om.
- **Flyway som enda källa till schemat.** Versionerade, immutabla migrationer ger spårbar historik och ett schema som är reproducerbart från clean state. `validate` mot databasen fångar mappningsfel tidigt; `ddl-auto: update` i produktion är en känd risk.
- **Testcontainers mot riktig MySQL**, inte en H2-attrapp som beter sig annorlunda på dialektspecifika punkter (JSON-typ, CHECK-constraints, FK-beteende).
- **MySQL framför Postgres** — beprövad uppsättning för Flyway, `@Column`-konventioner och Testcontainers; caset ställer inga databaskrav. Trade-off jag kan diskutera: Postgres JSONB hade varit marginellt vassare för audit-detaljerna.

## 5. Spårbarhet — och en medveten begränsning

Spårbarhet är centralt i vårdkontext (Patientdatalagen). Varje statusövergång och lagerrörelse registreras med tidsstämpel och användare; StockMovement och AuditLog är immutabla, så historikrader ändras aldrig. Saldo ändras endast via StockMovement — att sätta ett värde direkt lämnar inget spår.

**Känd begränsning:** aktören per statusövergång (vem som skickade/bekräftade/levererade) fångas i botten men exponeras inte i gränssnittet än. Det är ett medvetet bortval på presentationslagret, inte en saknad förmåga — själva spårbarheten finns; det som saknas är att lyfta upp den i ett fält i orderdetaljen. Backend äger sanningen, gränssnittet har bara inte fått ytan för historiken än.