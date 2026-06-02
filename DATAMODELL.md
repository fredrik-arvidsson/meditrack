# MediTrack — Datamodell

**Stack:** Java 21 + Spring Boot 3.x + MySQL 8.4 + Flyway + Hibernate
**Källa:** Regelverk-informerad design (GDPR, PDL, NIS2, HSLF-FS 2017:37, SOSFS 2011:9, MDR gränsfall).

---

## Lager 1 — Entiteter och syfte

**8 entiteter:** 6 kärndomän + 2 stödentiteter för regelverkskrav.

### Kärndomän

| Entitet | Syfte |
|---|---|
| **CareUnit** | Vårdenheten. Tenant-roten — allt annat pekar tillbaka hit. |
| **Medication** | Läkemedel som *kan* lagerföras (namn, ATC, form, styrka). Katalog per vårdenhet. |
| **StockItem** | Lagersaldo per läkemedel per vårdenhet. Threshold (varningsnivå) här, inte på Medication. |
| **Order** | Beställning med state machine: DRAFT → SENT → CONFIRMED → DELIVERED (+ CANCELLED). |
| **OrderLine** | Rad i en beställning — ett läkemedel + kvantitet. En order har många rader. |
| **User** | Vårdpersonalen. Roll (NURSE / PHARMACIST / ADMIN), hör till en vårdenhet. |

### Stödentiteter

| Entitet | Syfte |
|---|---|
| **AuditLog** | Strukturerad logg för PDL/GDPR/NIS2/SOSFS. Vem gjorde vad och när. Immutabel. |
| **StockMovement** | Historik över lagerförändringar. Spårbarhet ("varför sjönk lagret den 5 maj?"). Immutabel. |

---

## Lager 2 — Relationer

```
CareUnit (1) ───── (N) User
   │
   │ (1)
   │
   ├───── (N) Medication ────── (1) ─── (N) StockItem ── (1) ─── (N) StockMovement
   │                                          │
   │                                          │
   ├───── (N) Order ─── (1) ─── (N) OrderLine ── (N) ─── (1) Medication
   │
   └───── (N) AuditLog


User (1) ────── (N) Order            (created_by)
User (1) ────── (N) AuditLog
```

### Viktiga designval

**1. Medication per tenant (inte global katalog).**
Matchar PDL:s isoleringskrav. Bättre att isolera från dag 1 än retrofitta.

**2. StockItem separat från Medication.**
Saldo per enhet är *operativ data*, läkemedel är *katalogdata*. Olika ändringstakter. Threshold hör hemma på StockItem (varningsnivå är *per enhet*).

**3. OrderLine pekar på Medication, inte StockItem.**
En beställning gäller *att skaffa ett läkemedel*. Vid leverans (status → DELIVERED) hittar systemet rätt StockItem via `medication_id + care_unit_id` och uppdaterar saldot atomiskt.

**4. AuditLog och StockMovement är immutabla.**
Inga `updated_*`-fält. Historikrader ändras aldrig — det är *poängen* för spårbarhet.

---

## Lager 3 — Fält per entitet (MySQL 8 / Flyway-syntax)

### Auditable (`@MappedSuperclass`, inte tabell)

Alla muterbara domänentiteter ärver:

```sql
created_at   DATETIME(6) NOT NULL,
created_by   BIGINT      NOT NULL,  -- FK → users.id
updated_at   DATETIME(6) NOT NULL,
updated_by   BIGINT      NOT NULL   -- FK → users.id
```

### CareUnit (tenant-roten)

```sql
CREATE TABLE care_units (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(200) NOT NULL,
  external_id VARCHAR(50)  NULL,        -- t.ex. HSA-id
  address     VARCHAR(500) NULL,
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)  NOT NULL,
  created_by  BIGINT       NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  updated_by  BIGINT       NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_care_unit_external (external_id)
);
```

Ingen tenant-FK — det här *är* tenant.

### User

```sql
CREATE TABLE users (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  care_unit_id   BIGINT       NOT NULL,
  email          VARCHAR(255) NULL,        -- NULL efter anonymisering
  name           VARCHAR(200) NULL,        -- NULL efter anonymisering
  role           VARCHAR(20)  NOT NULL,    -- NURSE | PHARMACIST | ADMIN
  password_hash  VARCHAR(255) NULL,        -- BCrypt
  active         BOOLEAN      NOT NULL DEFAULT TRUE,
  deleted_at     DATETIME(6)  NULL,        -- soft-delete (GDPR art. 17)
  anonymized_at  DATETIME(6)  NULL,        -- när PII nollställdes
  created_at     DATETIME(6)  NOT NULL,
  created_by     BIGINT       NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  updated_by     BIGINT       NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_email_per_unit (care_unit_id, email),
  CONSTRAINT fk_user_care_unit FOREIGN KEY (care_unit_id) REFERENCES care_units(id)
);
```

`UNIQUE (care_unit_id, email)`: e-post unik **per vårdenhet**, inte globalt. Förenklar soft-delete-flödet.

### Medication

```sql
CREATE TABLE medications (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  care_unit_id         BIGINT       NOT NULL,
  name                 VARCHAR(200) NOT NULL,
  atc_code             VARCHAR(20)  NULL,        -- "N02BE01"
  form                 VARCHAR(50)  NOT NULL,    -- TABLET | INJECTION | SOLUTION ...
  strength             VARCHAR(50)  NULL,        -- "500 mg"
  unit                 VARCHAR(20)  NOT NULL,    -- "tablett", "ml" ...
  controlled_substance BOOLEAN      NOT NULL DEFAULT FALSE,  -- narkotika
  active               BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at           DATETIME(6)  NOT NULL,
  created_by           BIGINT       NOT NULL,
  updated_at           DATETIME(6)  NOT NULL,
  updated_by           BIGINT       NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_medication_name (care_unit_id, name),
  INDEX idx_medication_atc  (care_unit_id, atc_code),
  CONSTRAINT fk_medication_care_unit FOREIGN KEY (care_unit_id) REFERENCES care_units(id)
);
```

### StockItem

```sql
CREATE TABLE stock_items (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  care_unit_id  BIGINT      NOT NULL,
  medication_id BIGINT      NOT NULL,
  quantity      INT         NOT NULL DEFAULT 0,
  threshold     INT         NOT NULL DEFAULT 0,
  version       BIGINT      NOT NULL DEFAULT 0,   -- @Version (optimistic lock)
  created_at    DATETIME(6) NOT NULL,
  created_by    BIGINT      NOT NULL,
  updated_at    DATETIME(6) NOT NULL,
  updated_by    BIGINT      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stock_unit_medication (care_unit_id, medication_id),
  CONSTRAINT fk_stock_care_unit  FOREIGN KEY (care_unit_id)  REFERENCES care_units(id),
  CONSTRAINT fk_stock_medication FOREIGN KEY (medication_id) REFERENCES medications(id)
);
```

`version`-kolumnen för Hibernates `@Version` — optimistisk låsning vid concurrent updates.

### StockMovement (immutabel)

```sql
CREATE TABLE stock_movements (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  care_unit_id    BIGINT       NOT NULL,
  stock_item_id   BIGINT       NOT NULL,
  medication_id   BIGINT       NOT NULL,      -- denormaliserat för enklare query
  delta           INT          NOT NULL,      -- positivt = inkommande, negativt = uttag
  quantity_after  INT          NOT NULL,      -- saldot efter rörelsen
  reason          VARCHAR(50)  NOT NULL,      -- DELIVERY | MANUAL_ADJUSTMENT | INITIAL ...
  order_id        BIGINT       NULL,          -- vid leverans, vilken order
  notes           VARCHAR(500) NULL,
  created_at      DATETIME(6)  NOT NULL,
  created_by      BIGINT       NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_movement_stock_item (stock_item_id, created_at),
  INDEX idx_movement_care_unit  (care_unit_id, created_at),
  CONSTRAINT fk_movement_care_unit  FOREIGN KEY (care_unit_id)  REFERENCES care_units(id),
  CONSTRAINT fk_movement_stock_item FOREIGN KEY (stock_item_id) REFERENCES stock_items(id),
  CONSTRAINT fk_movement_medication FOREIGN KEY (medication_id) REFERENCES medications(id),
  CONSTRAINT fk_movement_order     FOREIGN KEY (order_id)       REFERENCES orders(id)
);
```

Inga `updated_*`-fält. Rörelser är immutabla.

### Order

```sql
CREATE TABLE orders (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  care_unit_id   BIGINT        NOT NULL,
  order_number   VARCHAR(50)   NOT NULL,      -- "ORD-2026-001"
  status         VARCHAR(20)   NOT NULL,      -- DRAFT|SENT|CONFIRMED|DELIVERED|CANCELLED
  sent_at        DATETIME(6)   NULL,
  sent_by        BIGINT        NULL,
  confirmed_at   DATETIME(6)   NULL,
  confirmed_by   BIGINT        NULL,
  delivered_at   DATETIME(6)   NULL,
  delivered_by   BIGINT        NULL,
  notes          VARCHAR(1000) NULL,
  version        BIGINT        NOT NULL DEFAULT 0,  -- @Version
  created_at     DATETIME(6)   NOT NULL,
  created_by     BIGINT        NOT NULL,
  updated_at     DATETIME(6)   NOT NULL,
  updated_by     BIGINT        NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_number (care_unit_id, order_number),
  INDEX idx_order_status (care_unit_id, status, created_at),
  CONSTRAINT fk_order_care_unit FOREIGN KEY (care_unit_id) REFERENCES care_units(id)
);
```

State machine valideras i tjänstelagret, inte i databas. CANCELLED är en realistisk extension utöver casets fyra grundstatusar.

### OrderLine

```sql
CREATE TABLE order_lines (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  order_id      BIGINT       NOT NULL,
  medication_id BIGINT       NOT NULL,
  quantity      INT          NOT NULL,
  notes         VARCHAR(500) NULL,
  created_at    DATETIME(6)  NOT NULL,
  created_by    BIGINT       NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  updated_by    BIGINT       NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_orderline_med (order_id, medication_id),
  CONSTRAINT chk_orderline_qty CHECK (quantity > 0),
  CONSTRAINT fk_orderline_order      FOREIGN KEY (order_id)      REFERENCES orders(id),
  CONSTRAINT fk_orderline_medication FOREIGN KEY (medication_id) REFERENCES medications(id)
);
```

`UNIQUE (order_id, medication_id)`: samma läkemedel kan inte finnas två gånger i samma beställning. Tvingar kvantitetsaggregering uppströms.

### AuditLog (immutabel)

```sql
CREATE TABLE audit_logs (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  care_unit_id BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  action       VARCHAR(50)  NOT NULL,    -- ORDER_CREATED | ORDER_STATUS_CHANGED | ...
  entity_type  VARCHAR(50)  NOT NULL,    -- ORDER | MEDICATION | STOCK_ITEM | USER
  entity_id    BIGINT       NULL,
  ip_address   VARCHAR(45)  NULL,        -- IPv6-kompatibel
  details      JSON         NULL,        -- strukturerade metadata
  created_at   DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_audit_care_unit_time (care_unit_id, created_at),
  INDEX idx_audit_user           (user_id, created_at),
  INDEX idx_audit_entity         (entity_type, entity_id),
  CONSTRAINT fk_audit_care_unit FOREIGN KEY (care_unit_id) REFERENCES care_units(id),
  CONSTRAINT fk_audit_user      FOREIGN KEY (user_id)      REFERENCES users(id)
);
```

MySQL JSON-typ för `details` — strukturerade metadata (gammalt värde, nytt värde, kontext). Ingen `created_by`/`updated_*` — `user_id` är aktören och loggen är immutabel.

---

## Sammanfattning — vad designen ger oss

- **Tenant-FK på alla domänentiteter** (PDL, GDPR)
- **Auditable-fält på muterbara entiteter** (GDPR, SOSFS, NIS2)
- **Soft-delete + anonymisering på User** (GDPR art. 17)
- **Immutabel AuditLog och StockMovement** (PDL/SOSFS spårbarhet)
- **Optimistic locking via `version`** på Order och StockItem (concurrency)
- **`controlled_substance`-flagga** (HSLF-FS 2017:37, narkotika)
- **State machine för Order** (caset + CANCELLED som extension)
- **Unique constraints för datakvalitet** (en orderrad per medication)
- **Index för förväntade query-mönster** (sök läkemedel, lista orders per status, audit per tenant)

## Vad designen *medvetet inte* gör

- **Schema-per-tenant** — overkill, tenant-FK + Hibernate-filter räcker
- **Globala läkemedelskataloger** — bryter PDL-isolering
- **Separata historik-arkivtabeller** — kan partitioneras senare vid behov
- **CHECK-constraints för status-enum** — valideras i tjänstelagret för bättre felmeddelanden

---

## Implementeringsordning (för Flyway V1)

1. `care_units` (ingen FK)
2. `users` (FK → care_units)
3. `medications` (FK → care_units)
4. `stock_items` (FK → care_units, medications)
5. `orders` (FK → care_units)
6. `order_lines` (FK → orders, medications)
7. `stock_movements` (FK → care_units, stock_items, medications, orders)
8. `audit_logs` (FK → care_units, users)

Allt i **en** migration `V1__init.sql`. Seed-data som `R__seed.sql` (repeatable) — håll seed separat från schema.
