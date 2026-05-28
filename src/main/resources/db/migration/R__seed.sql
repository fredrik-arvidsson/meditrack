-- ============================================================
-- R__seed.sql — demo- och utvecklingsdata för MediTrack
-- ============================================================
-- Repeatable migration (R__ prefix): körs om varje gång filens
-- innehåll ändras. Använder INSERT IGNORE för att vara idempotent
-- — körs den om utan ändringar händer inget, körs den om med nya
-- rader läggs bara de nya till.
--
-- NOTE: Detta är seed-data för demo, inte test-data. Tester
-- bygger sin egen data per testkörning (se README).
--
-- Alla rader hör till care_unit_id 1 förutom enhet 2 själv —
-- enhet 2 finns för att visa att tenant-isoleringen fungerar
-- men har medvetet ingen egen data (visar tom-vy-fallet).
-- ============================================================

-- ---------- VÅRDENHETER ----------
INSERT IGNORE INTO care_units (id, name, external_id, address, active, created_at, created_by, updated_at, updated_by)
VALUES
  (1, 'Akutmottagningen Solna', 'AKUT-SOLNA-01', 'Sjukhusvägen 1, 171 76 Solna', TRUE,
   NOW(6), 1, NOW(6), 1),
  (2, 'Internmedicin Huddinge', 'INTMED-HUD-01', 'Hälsovägen 2, 141 86 Stockholm', TRUE,
   NOW(6), 1, NOW(6), 1);

-- ---------- ANVÄNDARE ----------
-- En av varje roll i enhet 1. password_hash lämnas NULL tills
-- security-lagret implementeras. created_by/updated_by pekar på
-- sig själva (id 1) för cirkelreferens i utgångsläget.
INSERT IGNORE INTO users (id, care_unit_id, email, name, role, password_hash, active, created_at, created_by, updated_at, updated_by)
VALUES
  (1, 1, 'anna.lindberg@meditrack.demo',   'Anna Lindberg',   'ADMIN',      NULL, TRUE, NOW(6), 1, NOW(6), 1),
  (2, 1, 'erik.svensson@meditrack.demo',   'Erik Svensson',   'PHARMACIST', NULL, TRUE, NOW(6), 1, NOW(6), 1),
  (3, 1, 'sara.johansson@meditrack.demo',  'Sara Johansson',  'NURSE',      NULL, TRUE, NOW(6), 1, NOW(6), 1);

-- ---------- LÄKEMEDEL ----------
-- Sju läkemedel i enhet 1, varierande former. Morfin är flaggat
-- som controlled_substance (HSLF-FS 2017:37 — narkotika).
INSERT IGNORE INTO medications (id, care_unit_id, name, atc_code, form, strength, unit, controlled_substance, active, created_at, created_by, updated_at, updated_by)
VALUES
  (1, 1, 'Paracetamol',     'N02BE01', 'TABLET',     '500 mg',  'tablett', FALSE, TRUE, NOW(6), 1, NOW(6), 1),
  (2, 1, 'Ibuprofen',       'M01AE01', 'TABLET',     '400 mg',  'tablett', FALSE, TRUE, NOW(6), 1, NOW(6), 1),
  (3, 1, 'Amoxicillin',     'J01CA04', 'TABLET',     '500 mg',  'kapsel',  FALSE, TRUE, NOW(6), 1, NOW(6), 1),
  (4, 1, 'Morfin',          'N02AA01', 'INJECTION',  '10 mg/ml','ampull',  TRUE,  TRUE, NOW(6), 1, NOW(6), 1),
  (5, 1, 'Adrenalin',       'C01CA24', 'INJECTION',  '1 mg/ml', 'ampull',  FALSE, TRUE, NOW(6), 1, NOW(6), 1),
  (6, 1, 'Natriumklorid',   'B05XA03', 'SOLUTION',   '9 mg/ml', 'påse',    FALSE, TRUE, NOW(6), 1, NOW(6), 1),
  (7, 1, 'Salbutamol',      'R03AC02', 'INHALATION', '0,1 mg',  'dos',     FALSE, TRUE, NOW(6), 1, NOW(6), 1);

-- ---------- LAGERPOSTER ----------
-- Saldon för fem av läkemedlen. Paracetamol och Salbutamol är
-- medvetet UNDER sin threshold så låglagervarningen har något att
-- visa direkt vid demo. Notera @Version-fältet — Hibernate kräver
-- att det är satt till 0 vid skapande.
INSERT IGNORE INTO stock_items (id, care_unit_id, medication_id, quantity, threshold, version, created_at, created_by, updated_at, updated_by)
VALUES
  (1, 1, 1,  15, 50, 0, NOW(6), 1, NOW(6), 1),  -- Paracetamol: 15 < 50 → varning
  (2, 1, 2, 120, 30, 0, NOW(6), 1, NOW(6), 1),  -- Ibuprofen: ok
  (3, 1, 4,  25, 10, 0, NOW(6), 1, NOW(6), 1),  -- Morfin: ok
  (4, 1, 5,  40, 15, 0, NOW(6), 1, NOW(6), 1),  -- Adrenalin: ok
  (5, 1, 7,   3, 20, 0, NOW(6), 1, NOW(6), 1);  -- Salbutamol: 3 < 20 → varning

-- ---------- BESTÄLLNINGAR ----------
-- En i DRAFT (utkast, ej skickad) och en levererad. Den levererade
-- har tidsstämplar för hela sin resa genom state machine.
INSERT IGNORE INTO orders (id, care_unit_id, order_number, status, sent_at, sent_by, confirmed_at, confirmed_by, delivered_at, delivered_by, notes, version, created_at, created_by, updated_at, updated_by)
VALUES
  (1, 1, 'ORD-DEMO0001', 'DRAFT',
     NULL, NULL, NULL, NULL, NULL, NULL,
     'Utkast — vänta med beställning till nästa vecka', 0,
     NOW(6), 2, NOW(6), 2),
  (2, 1, 'ORD-DEMO0002', 'DELIVERED',
     DATE_SUB(NOW(6), INTERVAL 5 DAY), 2,
     DATE_SUB(NOW(6), INTERVAL 4 DAY), 2,
     DATE_SUB(NOW(6), INTERVAL 1 DAY), 2,
     'Brådskande påfyllnad av Paracetamol', 0,
     DATE_SUB(NOW(6), INTERVAL 6 DAY), 3, NOW(6), 2);

-- ---------- ORDERRADER ----------
INSERT IGNORE INTO order_lines (id, order_id, medication_id, quantity, notes, created_at, created_by, updated_at, updated_by)
VALUES
  -- Utkast (order 1): två rader, inte skickad
  (1, 1, 6, 50, NULL, NOW(6), 2, NOW(6), 2),
  (2, 1, 3, 30, NULL, NOW(6), 2, NOW(6), 2),
  -- Levererad (order 2): en rad, Paracetamol
  (3, 2, 1, 100, 'Stor påfyllnad', DATE_SUB(NOW(6), INTERVAL 6 DAY), 3, NOW(6), 2);

-- ---------- LAGERRÖRELSER ----------
-- Startsaldon (INITIAL) för de fem lagerposterna, plus leveransen
-- från order 2 som lade till 100 paracetamol (men efter det har
-- några tagits ut — därför saldo 15 nu, inte 100+ursprung).
-- Vi förenklar: visar bara INITIAL + DELIVERY, ingen utförsel-
-- historik. Det räcker för att illustrera spårbarhet.
INSERT IGNORE INTO stock_movements (id, care_unit_id, stock_item_id, medication_id, delta, quantity_after, reason, order_id, notes, created_at, created_by)
VALUES
  (1, 1, 1, 1, 0,   0,   'INITIAL',   NULL, 'Startsaldo',
     DATE_SUB(NOW(6), INTERVAL 30 DAY), 1),
  (2, 1, 2, 2, 120, 120, 'INITIAL',   NULL, 'Startsaldo',
     DATE_SUB(NOW(6), INTERVAL 30 DAY), 1),
  (3, 1, 3, 4, 25,  25,  'INITIAL',   NULL, 'Startsaldo',
     DATE_SUB(NOW(6), INTERVAL 30 DAY), 1),
  (4, 1, 4, 5, 40,  40,  'INITIAL',   NULL, 'Startsaldo',
     DATE_SUB(NOW(6), INTERVAL 30 DAY), 1),
  (5, 1, 5, 7, 3,   3,   'INITIAL',   NULL, 'Startsaldo',
     DATE_SUB(NOW(6), INTERVAL 30 DAY), 1),
  (6, 1, 1, 1, 100, 100, 'DELIVERY',  2,    'Leverans av order ORD-DEMO0002',
     DATE_SUB(NOW(6), INTERVAL 1 DAY), 2);

-- ---------- AUDIT-LOGG ----------
-- Ett par exempelrader för att visa loggens form. JSON i details
-- — flexibel kontext per händelse.
INSERT IGNORE INTO audit_logs (id, care_unit_id, user_id, action, entity_type, entity_id, ip_address, details, created_at)
VALUES
  (1, 1, 3, 'CREATE', 'Order', 2, '192.168.1.45',
     JSON_OBJECT('orderNumber', 'ORD-DEMO0002', 'lineCount', 1),
     DATE_SUB(NOW(6), INTERVAL 6 DAY)),
  (2, 1, 2, 'STATUS_CHANGE', 'Order', 2, '192.168.1.42',
     JSON_OBJECT('from', 'CONFIRMED', 'to', 'DELIVERED', 'orderNumber', 'ORD-DEMO0002'),
     DATE_SUB(NOW(6), INTERVAL 1 DAY));