-- =====================================================================
-- MediTrack — Initial schema
-- =====================================================================
-- Skapar 8 tabeller i topologisk ordning efter FK-beroenden:
--   1. care_units      (tenant-roten, inga FK)
--   2. users           (FK → care_units)
--   3. medications     (FK → care_units)
--   4. stock_items     (FK → care_units, medications)
--   5. orders          (FK → care_units)
--   6. order_lines     (FK → orders, medications)
--   7. stock_movements (FK → care_units, stock_items, medications, orders)
--   8. audit_logs      (FK → care_units, users)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. care_units (tenant-roten)
-- ---------------------------------------------------------------------
CREATE TABLE care_units (
                            id          BIGINT       NOT NULL AUTO_INCREMENT,
                            name        VARCHAR(200) NOT NULL,
                            external_id VARCHAR(50)  NULL,
                            address     VARCHAR(500) NULL,
                            active      BOOLEAN      NOT NULL DEFAULT TRUE,
                            created_at  DATETIME(6)  NOT NULL,
                            created_by  BIGINT       NOT NULL,
                            updated_at  DATETIME(6)  NOT NULL,
                            updated_by  BIGINT       NOT NULL,
                            PRIMARY KEY (id),
                            INDEX idx_care_unit_external (external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. users
-- ---------------------------------------------------------------------
CREATE TABLE users (
                       id             BIGINT       NOT NULL AUTO_INCREMENT,
                       care_unit_id   BIGINT       NOT NULL,
                       email          VARCHAR(255) NULL,
                       name           VARCHAR(200) NULL,
                       role           VARCHAR(20)  NOT NULL,
                       password_hash  VARCHAR(255) NULL,
                       active         BOOLEAN      NOT NULL DEFAULT TRUE,
                       deleted_at     DATETIME(6)  NULL,
                       anonymized_at  DATETIME(6)  NULL,
                       created_at     DATETIME(6)  NOT NULL,
                       created_by     BIGINT       NOT NULL,
                       updated_at     DATETIME(6)  NOT NULL,
                       updated_by     BIGINT       NOT NULL,
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_user_email_per_unit (care_unit_id, email),
                       CONSTRAINT chk_user_role CHECK (role IN ('NURSE', 'PHARMACIST', 'ADMIN')),
                       CONSTRAINT fk_user_care_unit FOREIGN KEY (care_unit_id) REFERENCES care_units(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. medications
-- ---------------------------------------------------------------------
CREATE TABLE medications (
                             id                   BIGINT       NOT NULL AUTO_INCREMENT,
                             care_unit_id         BIGINT       NOT NULL,
                             name                 VARCHAR(200) NOT NULL,
                             atc_code             VARCHAR(20)  NULL,
                             form                 VARCHAR(50)  NOT NULL,
                             strength             VARCHAR(50)  NULL,
                             unit                 VARCHAR(20)  NOT NULL,
                             controlled_substance BOOLEAN      NOT NULL DEFAULT FALSE,
                             active               BOOLEAN      NOT NULL DEFAULT TRUE,
                             created_at           DATETIME(6)  NOT NULL,
                             created_by           BIGINT       NOT NULL,
                             updated_at           DATETIME(6)  NOT NULL,
                             updated_by           BIGINT       NOT NULL,
                             PRIMARY KEY (id),
                             INDEX idx_medication_name (care_unit_id, name),
                             INDEX idx_medication_atc  (care_unit_id, atc_code),
                             CONSTRAINT chk_medication_form CHECK (form IN (
                                                                            'TABLET','INJECTION','SOLUTION','CREAM','INHALATION',
                                                                            'OINTMENT','DROPS','SUPPOSITORY','PATCH'
                                 )),
                             CONSTRAINT fk_medication_care_unit FOREIGN KEY (care_unit_id) REFERENCES care_units(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. stock_items
-- ---------------------------------------------------------------------
CREATE TABLE stock_items (
                             id            BIGINT      NOT NULL AUTO_INCREMENT,
                             care_unit_id  BIGINT      NOT NULL,
                             medication_id BIGINT      NOT NULL,
                             quantity      INT         NOT NULL DEFAULT 0,
                             threshold     INT         NOT NULL DEFAULT 0,
                             version       BIGINT      NOT NULL DEFAULT 0,
                             created_at    DATETIME(6) NOT NULL,
                             created_by    BIGINT      NOT NULL,
                             updated_at    DATETIME(6) NOT NULL,
                             updated_by    BIGINT      NOT NULL,
                             PRIMARY KEY (id),
                             UNIQUE KEY uk_stock_unit_medication (care_unit_id, medication_id),
                             CONSTRAINT chk_stock_quantity_nonneg  CHECK (quantity  >= 0),
                             CONSTRAINT chk_stock_threshold_nonneg CHECK (threshold >= 0),
                             CONSTRAINT fk_stock_care_unit  FOREIGN KEY (care_unit_id)  REFERENCES care_units(id),
                             CONSTRAINT fk_stock_medication FOREIGN KEY (medication_id) REFERENCES medications(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. orders
-- ---------------------------------------------------------------------
CREATE TABLE orders (
                        id            BIGINT        NOT NULL AUTO_INCREMENT,
                        care_unit_id  BIGINT        NOT NULL,
                        order_number  VARCHAR(50)   NOT NULL,
                        status        VARCHAR(20)   NOT NULL,
                        sent_at       DATETIME(6)   NULL,
                        sent_by       BIGINT        NULL,
                        confirmed_at  DATETIME(6)   NULL,
                        confirmed_by  BIGINT        NULL,
                        delivered_at  DATETIME(6)   NULL,
                        delivered_by  BIGINT        NULL,
                        notes         VARCHAR(1000) NULL,
                        version       BIGINT        NOT NULL DEFAULT 0,
                        created_at    DATETIME(6)   NOT NULL,
                        created_by    BIGINT        NOT NULL,
                        updated_at    DATETIME(6)   NOT NULL,
                        updated_by    BIGINT        NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_order_number (care_unit_id, order_number),
                        INDEX idx_order_status (care_unit_id, status, created_at),
                        CONSTRAINT chk_order_status CHECK (status IN (
                                                                      'DRAFT','SENT','CONFIRMED','DELIVERED','CANCELLED'
                            )),
                        CONSTRAINT fk_order_care_unit FOREIGN KEY (care_unit_id) REFERENCES care_units(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 6. order_lines
-- ---------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 7. stock_movements (immutabel)
-- ---------------------------------------------------------------------
CREATE TABLE stock_movements (
                                 id             BIGINT       NOT NULL AUTO_INCREMENT,
                                 care_unit_id   BIGINT       NOT NULL,
                                 stock_item_id  BIGINT       NOT NULL,
                                 medication_id  BIGINT       NOT NULL,
                                 delta          INT          NOT NULL,
                                 quantity_after INT          NOT NULL,
                                 reason         VARCHAR(50)  NOT NULL,
                                 order_id       BIGINT       NULL,
                                 notes          VARCHAR(500) NULL,
                                 created_at     DATETIME(6)  NOT NULL,
                                 created_by     BIGINT       NOT NULL,
                                 PRIMARY KEY (id),
                                 INDEX idx_movement_stock_item (stock_item_id, created_at),
                                 INDEX idx_movement_care_unit  (care_unit_id, created_at),
                                 CONSTRAINT chk_movement_qty_after_nonneg CHECK (quantity_after >= 0),
                                 CONSTRAINT chk_movement_reason CHECK (reason IN (
                                                                                  'INITIAL','DELIVERY','MANUAL_ADJUSTMENT','CORRECTION','EXPIRY','LOSS'
                                     )),
                                 CONSTRAINT fk_movement_care_unit  FOREIGN KEY (care_unit_id)  REFERENCES care_units(id),
                                 CONSTRAINT fk_movement_stock_item FOREIGN KEY (stock_item_id) REFERENCES stock_items(id),
                                 CONSTRAINT fk_movement_medication FOREIGN KEY (medication_id) REFERENCES medications(id),
                                 CONSTRAINT fk_movement_order     FOREIGN KEY (order_id)       REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 8. audit_logs (immutabel)
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
                            id           BIGINT       NOT NULL AUTO_INCREMENT,
                            care_unit_id BIGINT       NOT NULL,
                            user_id      BIGINT       NOT NULL,
                            action       VARCHAR(50)  NOT NULL,
                            entity_type  VARCHAR(50)  NOT NULL,
                            entity_id    BIGINT       NULL,
                            ip_address   VARCHAR(45)  NULL,
                            details      JSON         NULL,
                            created_at   DATETIME(6)  NOT NULL,
                            PRIMARY KEY (id),
                            INDEX idx_audit_care_unit_time (care_unit_id, created_at),
                            INDEX idx_audit_user           (user_id, created_at),
                            INDEX idx_audit_entity         (entity_type, entity_id),
                            CONSTRAINT fk_audit_care_unit FOREIGN KEY (care_unit_id) REFERENCES care_units(id),
                            CONSTRAINT fk_audit_user      FOREIGN KEY (user_id)      REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;