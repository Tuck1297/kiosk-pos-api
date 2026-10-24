-- Kiosk POS: initial schema (Flyway V1)
-- Money is stored as BIGINT minor units (cents). Never NUMERIC/float in the API layer.
-- Enum-like columns use TEXT + CHECK so Hibernate maps them as plain strings
-- and adding a value is a one-line migration.

CREATE TABLE location (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name               TEXT        NOT NULL,
    park_code          TEXT        NOT NULL,
    timezone           TEXT        NOT NULL,
    tax_rate_bps       INTEGER     NOT NULL CHECK (tax_rate_bps BETWEEN 0 AND 3000),
    currency           CHAR(3)     NOT NULL DEFAULT 'USD' CHECK (currency ~ '^[A-Z]{3}$'),
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Counter for the short pickup number. Incremented with
    -- UPDATE ... SET next_order_number = next_order_number + 1 RETURNING ...
    next_order_number  INTEGER     NOT NULL DEFAULT 1000,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE menu_item (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id  UUID        NOT NULL REFERENCES location (id),
    sku          TEXT        NOT NULL CHECK (sku ~ '^[A-Z0-9-]{3,32}$'),
    name         TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
    description  TEXT        CHECK (length(description) <= 500),
    category     TEXT        NOT NULL CHECK (category IN ('ENTREE','SNACK','DESSERT','BEVERAGE','MERCHANDISE')),
    price_cents  BIGINT      NOT NULL CHECK (price_cents BETWEEN 0 AND 100000),
    available    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_menu_item_location_sku UNIQUE (location_id, sku)
);

CREATE INDEX ix_menu_item_location_category ON menu_item (location_id, category);

-- "order" is a reserved word, so the table is customer_order.
CREATE TABLE customer_order (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id     UUID        NOT NULL REFERENCES location (id),
    order_number    INTEGER     NOT NULL,
    channel         TEXT        NOT NULL CHECK (channel IN ('MOBILE','KIOSK','COUNTER')),
    guest_name      TEXT        CHECK (length(guest_name) <= 40),
    status          TEXT        NOT NULL DEFAULT 'OPEN'
                                CHECK (status IN ('OPEN','PAID','IN_PREPARATION','READY','COMPLETED','CANCELLED')),
    currency        CHAR(3)     NOT NULL,
    subtotal_cents  BIGINT      NOT NULL CHECK (subtotal_cents >= 0),
    tax_cents       BIGINT      NOT NULL CHECK (tax_cents >= 0),
    total_cents     BIGINT      NOT NULL CHECK (total_cents >= 0),
    cancel_reason   TEXT        CHECK (length(cancel_reason) <= 200),
    version         INTEGER     NOT NULL DEFAULT 0,  -- JPA @Version; exposed as ETag
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_total CHECK (total_cents = subtotal_cents + tax_cents),
    CONSTRAINT ck_cancel_reason CHECK (cancel_reason IS NULL OR status = 'CANCELLED'),
    CONSTRAINT uq_order_location_number UNIQUE (location_id, order_number)
);

-- Staff order queue: "PAID and IN_PREPARATION orders at my location, oldest first".
CREATE INDEX ix_order_queue ON customer_order (location_id, status, created_at, id);

CREATE TABLE order_line (
    id                UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID    NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    line_no           SMALLINT NOT NULL,
    menu_item_id      UUID    NOT NULL REFERENCES menu_item (id),
    -- Snapshots: the order must not change if the menu does.
    sku               TEXT    NOT NULL,
    name              TEXT    NOT NULL,
    unit_price_cents  BIGINT  NOT NULL CHECK (unit_price_cents >= 0),
    quantity          INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 20),
    line_total_cents  BIGINT  NOT NULL,
    note              TEXT    CHECK (length(note) <= 120),
    CONSTRAINT ck_line_total CHECK (line_total_cents = unit_price_cents * quantity),
    CONSTRAINT uq_order_line_no UNIQUE (order_id, line_no)
);

CREATE INDEX ix_order_line_menu_item ON order_line (menu_item_id);

CREATE TABLE payment (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID        NOT NULL REFERENCES customer_order (id),
    tender_type          TEXT        NOT NULL CHECK (tender_type IN ('CARD','CASH')),
    status               TEXT        NOT NULL CHECK (status IN ('CAPTURED','DECLINED','VOIDED')),
    amount_cents         BIGINT      NOT NULL CHECK (amount_cents >= 0),
    currency             CHAR(3)     NOT NULL,
    cash_tendered_cents  BIGINT      CHECK (cash_tendered_cents >= 0),
    change_due_cents     BIGINT      CHECK (change_due_cents >= 0),
    card_last4           CHAR(4)     CHECK (card_last4 ~ '^[0-9]{4}$'),
    processor_reference  TEXT,
    decline_code         TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_cash_fields CHECK (
        tender_type <> 'CASH'
        OR (cash_tendered_cents IS NOT NULL AND change_due_cents = cash_tendered_cents - amount_cents)
    ),
    CONSTRAINT ck_card_fields CHECK (tender_type <> 'CARD' OR cash_tendered_cents IS NULL),
    CONSTRAINT ck_decline_code CHECK ((status = 'DECLINED') = (decline_code IS NOT NULL))
);

CREATE INDEX ix_payment_order ON payment (order_id, created_at DESC);

-- Last line of defence against double charging: at most one captured payment per order,
-- even if two requests slip past the idempotency check.
CREATE UNIQUE INDEX uq_payment_one_captured_per_order ON payment (order_id) WHERE status = 'CAPTURED';

-- Idempotency-Key store. Flow:
--   1. INSERT (scope, key, request_hash, 'IN_PROGRESS') ON CONFLICT DO NOTHING
--   2. If no row inserted: same hash + COMPLETED -> replay stored response;
--      same hash + IN_PROGRESS -> 409 IDEMPOTENCY_IN_PROGRESS;
--      different hash -> 422 IDEMPOTENCY_KEY_REUSED
--   3. Do the work, then UPDATE to COMPLETED with the response, in the same transaction.
-- A scheduled job deletes rows past expires_at.
CREATE TABLE idempotency_key (
    scope            TEXT        NOT NULL,  -- caller principal + operationId, e.g. 'app-123:payOrder'
    idempotency_key  UUID        NOT NULL,
    request_hash     CHAR(64)    NOT NULL,  -- SHA-256 hex of method + path + canonical body
    state            TEXT        NOT NULL CHECK (state IN ('IN_PROGRESS','COMPLETED')),
    response_status  SMALLINT,
    response_body    JSONB,
    resource_path    TEXT,                  -- for the Location header on replay
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours',
    PRIMARY KEY (scope, idempotency_key),
    CONSTRAINT ck_completed_has_response CHECK (state = 'IN_PROGRESS' OR response_status IS NOT NULL)
);

CREATE INDEX ix_idempotency_expires ON idempotency_key (expires_at);
