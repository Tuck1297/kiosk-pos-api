-- Promo codes. A code is either a percentage (percent_bps, 1000 = 10%) or a fixed amount
-- off (amount_cents), optionally with a minimum order, a date window and a redemption cap.
CREATE TABLE discount_code (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code                TEXT        NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9]{3,20}$'),
    description         TEXT        NOT NULL CHECK (length(description) BETWEEN 1 AND 120),
    kind                TEXT        NOT NULL CHECK (kind IN ('PERCENT', 'AMOUNT')),
    percent_bps         INTEGER     CHECK (percent_bps BETWEEN 1 AND 10000),
    amount_cents        BIGINT      CHECK (amount_cents > 0),
    min_subtotal_cents  BIGINT      NOT NULL DEFAULT 0 CHECK (min_subtotal_cents >= 0),
    starts_at           TIMESTAMPTZ,
    ends_at             TIMESTAMPTZ,
    -- Null = unlimited. Only capped codes are counted, so a popular unlimited code never
    -- becomes a row every register writes to.
    max_redemptions     INTEGER     CHECK (max_redemptions > 0),
    times_redeemed      INTEGER     NOT NULL DEFAULT 0 CHECK (times_redeemed >= 0),
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_discount_value CHECK (
        (kind = 'PERCENT' AND percent_bps IS NOT NULL AND amount_cents IS NULL)
        OR (kind = 'AMOUNT' AND amount_cents IS NOT NULL AND percent_bps IS NULL)),
    CONSTRAINT ck_discount_window CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at),
    CONSTRAINT ck_discount_cap CHECK (max_redemptions IS NULL OR times_redeemed <= max_redemptions)
);

-- The discount on an order, snapshotted like prices: later changes to a code never alter it.
-- Order of operations: subtotal - discount - comps = taxable; tax on that; total = taxable + tax.
ALTER TABLE customer_order
    ADD COLUMN discount_cents        BIGINT  NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),
    ADD COLUMN discount_kind         TEXT    CHECK (discount_kind IN ('CODE', 'EMPLOYEE')),
    ADD COLUMN discount_code_id      UUID    REFERENCES discount_code (id),
    ADD COLUMN discount_employee_id  UUID    REFERENCES staff (id),
    ADD COLUMN discount_label        TEXT    CHECK (length(discount_label) <= 120),
    ADD COLUMN discount_rate_bps     INTEGER CHECK (discount_rate_bps BETWEEN 1 AND 10000),
    ADD CONSTRAINT ck_discount_source CHECK (
        (discount_kind IS NULL AND discount_cents = 0 AND discount_code_id IS NULL AND discount_employee_id IS NULL)
        OR (discount_kind = 'CODE' AND discount_code_id IS NOT NULL AND discount_employee_id IS NULL
            AND discount_label IS NOT NULL)
        OR (discount_kind = 'EMPLOYEE' AND discount_employee_id IS NOT NULL AND discount_code_id IS NULL
            AND discount_label IS NOT NULL));

ALTER TABLE customer_order DROP CONSTRAINT ck_order_total;
ALTER TABLE customer_order ADD CONSTRAINT ck_order_total CHECK (
    discount_cents + comp_cents <= subtotal_cents
    AND total_cents = subtotal_cents - discount_cents - comp_cents + tax_cents);

-- "Which orders used this code" and "employee discounts by employee" for loss prevention.
CREATE INDEX ix_order_discount_code ON customer_order (discount_code_id) WHERE discount_code_id IS NOT NULL;
CREATE INDEX ix_order_discount_employee ON customer_order (discount_employee_id, created_at)
    WHERE discount_employee_id IS NOT NULL;

-- Card brand for receipts ("Visa ending 4242"). Card numbers themselves never reach the API.
ALTER TABLE payment ADD COLUMN card_brand TEXT CHECK (card_brand IN ('VISA', 'MASTERCARD', 'AMEX', 'DISCOVER'));
