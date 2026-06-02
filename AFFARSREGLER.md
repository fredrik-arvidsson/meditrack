# MediTrack — Affärsregler

**Källa:** Casetexten + datamodellen + HSLF-FS 2017:37 (läkemedelshantering).

Detta dokument beskriver affärsreglerna för MediTrack. Varje regel motiveras kort.

## 1. Order state machine

### Tillåtna övergångar

```
DRAFT ──────► SENT ──────► CONFIRMED ──────► DELIVERED
  │             │              │
  └─► CANCELLED └─► CANCELLED   └─► CANCELLED

(DELIVERED är slutligt — inga övergångar därifrån)
```

### Regler

**R1.1:** Endast DRAFT-orders får ha rader läggas till/ändras/tas bort. När en order är skickad är den ett avtal. Att ändra rader efteråt är att förfalska beställningen. Spårbarhet kräver att skickade orders är låsta.

**R1.2:** DRAFT → SENT kräver minst en orderrad. Tom beställning är meningslös och troligen ett UI-fel.

**R1.3:** SENT → CONFIRMED får göras av användare med rollen PHARMACIST eller ADMIN. Bekräftelse är apotekets handling. En sjuksköterska kan inte bekräfta sin egen beställning (separation of duties — HSLF-FS 2017:37).

**R1.4:** CONFIRMED → DELIVERED utlöser atomär lageruppdatering. Systemets enda integritetskritiska operation. Måste vara transactional. När statusen ändras till DELIVERED ska samtidigt StockItem.quantity ökas och StockMovement skapas — eller inget av det.

**R1.5:** Status kan inte hoppa över steg. SENT → DELIVERED utan CONFIRMED bryter spårbarheten. Apotekets bekräftelse måste finnas registrerad.

**R1.6:** DELIVERED är terminalt — inga övergångar därifrån. En levererad order är slutförd. Korrigeringar görs som nya StockMovement eller nya orders, inte genom att backa status.

**R1.7:** CANCELLED kan ske från DRAFT, SENT, CONFIRMED — inte från DELIVERED. En levererad order kan inte avbeställas — varorna är redan på plats.

**R1.8:** Statusövergångar registreras med timestamp + user. PDL/audit-krav. Vem som godkände, när.

### Designkonsekvens

State machine-logik i en dedikerad OrderStateMachine-service, inte i Order-entiteten eller controllern. Gör reglerna explicita, testbara, och oberoende av JPA.

```java
@Service
public class OrderStateMachine {
    public void transition(Order order, OrderStatus newStatus, User actor) {
        validateTransition(order.getStatus(), newStatus);
        validateActor(order, newStatus, actor);
        applyTransition(order, newStatus, actor);
        if (newStatus == DELIVERED) {
            stockService.applyDelivery(order, actor);
        }
        auditLog.record(...);
    }
}
```

## 2. Lagerlogik

**R2.1:** StockItem skapas automatiskt när en Medication skapas. En medication utan lagerpost är meningslös — du kan varken se saldo eller threshold. Skapa båda i samma transaktion.

**R2.2:** Lagersaldo ändras endast via StockMovement. Spårbarhet. Att direkt sätta stock_item.quantity = 42 lämnar inget spår. Alla ändringar måste gå via en StockMovement-rad så vi kan se varför saldot är vad det är.

**R2.3:** Vid leverans uppdateras saldot atomiskt under pessimistic lock. Concurrency. Om två leveranser kommer in samtidigt på samma StockItem kan optimistisk låsning leda till retry-loop. Pessimistic lock på StockItem (SELECT ... FOR UPDATE) garanterar serialisering.

**R2.4:** Saldot kan aldrig bli negativt. Logisk omöjlighet. Validering i tjänstelagret + CHECK constraint i databas som backstop.

**R2.5:** Threshold är aldrig negativ; threshold = 0 betyder "ingen varning". Konvention. En varning utlöses när quantity < threshold. Om threshold är 0 utlöses aldrig varning.

**R2.6:** StockMovement för INITIAL skapas när StockItem skapas. Spårbarhet från första början. Saldo 0 vid skapande → en INITIAL-rörelse med delta=0 dokumenterar starten.

### Designkonsekvens

Lagerlogik i en dedikerad StockService:

```java
@Service
public class StockService {
    @Transactional
    public void applyDelivery(Order order, User actor) {
        for (OrderLine line : order.getLines()) {
            StockItem stock = stockRepo.findByMedicationForUpdate(
                line.getMedicationId(), order.getCareUnitId()
            );
            stock.setQuantity(stock.getQuantity() + line.getQuantity());
            stockMovementRepo.save(new StockMovement(
                stock, line.getQuantity(), DELIVERY, order, actor
            ));
        }
    }
}
```

## 3. Threshold-varningar

**R3.1:** Ett läkemedel är "under threshold" när quantity < threshold. Strict less-than. Vid quantity == threshold är vi vid gränsen men inte under. Beställning bör övervägas men inte tvingas.

**R3.2:** Threshold-status är ett beräknat fält, inte lagrat. Lagra inte derivat data. Beräknas vid hämtning. Förhindrar inkonsistens där quantity uppdateras men "under_threshold" inte gör det.

**R3.3:** Threshold-varning visas i UI men blockerar ingen handling. Det är en signal, inte en regel. En sjuksköterska kan ändå skapa en beställning, ändå se lagret. Varningen är informativ.

**R3.4:** Threshold sätts per StockItem (per enhet), inte per Medication. Vad som är "lågt lager" beror på vårdenhetens förbrukningsmönster. En akutmottagning behöver mer reserv än ett rehab-boende.

## 4. Validering

**R4.1:** ATC-kod följer EU-format om angiven — `^[A-Z]\d{2}[A-Z]{2}\d{2}$` (t.ex. "N02BE01"). Förhindrar slarvfel. ATC är optional (NULL) men om angiven ska den vara giltig.

**R4.2:** Quantity i OrderLine måste vara > 0. Beställa 0 enheter är meningslöst. CHECK constraint i databas + Bean Validation i DTO.

**R4.3:** Threshold och quantity i StockItem måste vara ≥ 0. Negativa saldon är logisk omöjlighet (R2.4).

**R4.4:** Order_number är auto-genererat, inte användarens input. Risk för dubbletter, formatproblem, manipulation. Genereras serverside (t.ex. ORD-2026-00042).

**R4.5:** Email i User följer e-postformat när satt. Bean Validation @Email. Standard.

**R4.6:** Role i User är en av tre värden: NURSE, PHARMACIST, ADMIN. Enum, inte fritext. CHECK constraint som backstop.

**R4.7:** Medication.form är från en definierad lista. TABLET, INJECTION, SOLUTION, CREAM, INHALATION, OINTMENT, DROPS, SUPPOSITORY, PATCH. Enum i koden, VARCHAR i DB för flexibilitet.

## 5. Behörighet (RBAC)

| Handling | NURSE | PHARMACIST | ADMIN |
|---|---|---|---|
| Visa läkemedelsregister | ✓ | ✓ | ✓ |
| Skapa/redigera/ta bort Medication | ✗ | ✓ | ✓ |
| Visa lager | ✓ | ✓ | ✓ |
| Justera threshold | ✗ | ✓ | ✓ |
| Skapa beställning (DRAFT) | ✓ | ✓ | ✓ |
| Skicka beställning (DRAFT → SENT) | ✓ | ✓ | ✓ |
| Bekräfta beställning (SENT → CONFIRMED) | ✗ | ✓ | ✓ |
| Markera levererad (CONFIRMED → DELIVERED) | ✓ | ✓ | ✓ |
| Avbryta beställning (→ CANCELLED) | ✓ (egen) | ✓ | ✓ |
| Visa audit-logg | ✗ | ✗ | ✓ |
| Hantera användare | ✗ | ✗ | ✓ |

**R5.1:** NURSE kan bara avbryta egna beställningar. Sjuksköterska kan inte avbryta en annan sjuksköterskas beställning utan bekräftelse från ADMIN/PHARMACIST.

**R5.2:** PHARMACIST har "separation of duties" från NURSE. En sjuksköterska som beställt får inte vara den som bekräftar (R1.3). Förhindrar enkla fel och fusk.

**R5.3:** ADMIN kan se all data inom sin vårdenhet — inte över vårdenheter. Tenant-isolering trumfar roller. PDL-krav.

### Designkonsekvens

`@PreAuthorize` på controllers + tenant-filter i Hibernate. Konsekvent `hasRole`-konvention utan `ROLE_`-prefix i uttrycken.

## Vad designen ger

- Tydlig state machine för Order — testbar, dokumenterad, isolerad i egen service
- Atomär lageruppdatering vid leverans med pessimistic lock — concurrency-säker
- Spårbarhet via StockMovement — alla saldoändringar har historik
- Threshold som beräknat fält — ingen konsistensrisk
- Validering på flera lager — DTO (Bean Validation), tjänstelager (affärsregler), databas (constraints)
- RBAC med separation of duties — matchar HSLF-FS 2017:37
- Tenant-isolering trumfar roller — PDL-krav

## Vad designen inte gör (medvetet)

- Komplex approval workflow (flera nivåers godkännanden) — caset specificerar fyra statusar; ytterligare nivåer avviker från kravspec
- Automatisk re-order vid threshold — möjlig sen-feature om tid finns; varningen är annars informativ
- Lot/batch-spårning — kräver omdesign av StockItem; MDR-territorium om patient-koppling införs
- Utgångsdatum på läkemedel — förutsätter batch-spårning för att vara meningsfull
- Eskalation/delegering vid frånvaro — overkill för pilotscope; kan tilläggas om scope växer
