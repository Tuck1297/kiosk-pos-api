-- Staff who sign in at the register (cashiers) and approve comps (managers).
-- PINs are stored only as bcrypt hashes. Five wrong PINs in a row lock the account
-- for 15 minutes (failed_attempts / locked_until), which stops guessing a 4-6 digit PIN.
CREATE TABLE staff (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id      TEXT        NOT NULL UNIQUE CHECK (employee_id ~ '^[A-Z0-9-]{2,16}$'),
    name             TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
    role             TEXT        NOT NULL CHECK (role IN ('CASHIER', 'MANAGER')),
    pin_hash         TEXT        NOT NULL,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    failed_attempts  INTEGER     NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Who rang up each order (null for orders created before sign-in existed).
ALTER TABLE customer_order ADD COLUMN created_by UUID REFERENCES staff (id);
CREATE INDEX ix_order_created_by ON customer_order (created_by, created_at);

-- Comps: amount taken off an order, with the approving manager. This table is the audit
-- trail, so rows are only ever inserted.
CREATE TABLE order_comp (
    id             UUID        PRIMARY KEY,
    order_id       UUID        NOT NULL REFERENCES customer_order (id),
    order_line_id  UUID        REFERENCES order_line (id),   -- null = the whole order
    reason         TEXT        NOT NULL CHECK (reason IN ('GUEST_RECOVERY', 'WRONG_ORDER', 'QUALITY_ISSUE', 'EMPLOYEE_MEAL', 'OTHER')),
    note           TEXT        CHECK (length(note) <= 200),
    amount_cents   BIGINT      NOT NULL CHECK (amount_cents > 0),
    approved_by    UUID        NOT NULL REFERENCES staff (id),
    requested_by   UUID        REFERENCES staff (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_comp_other_needs_note CHECK (reason <> 'OTHER' OR note IS NOT NULL)
);

-- An item can be comped once, and an order can get one whole-order comp.
CREATE UNIQUE INDEX uq_comp_per_line ON order_comp (order_line_id) WHERE order_line_id IS NOT NULL;
CREATE UNIQUE INDEX uq_comp_whole_order ON order_comp (order_id) WHERE order_line_id IS NULL;
CREATE INDEX ix_comp_order ON order_comp (order_id);
-- "Comps approved by this manager this week" is the report loss prevention runs.
CREATE INDEX ix_comp_approver ON order_comp (approved_by, created_at);

-- Comps reduce the taxable amount: total = subtotal - comps + tax(subtotal - comps).
ALTER TABLE customer_order ADD COLUMN comp_cents BIGINT NOT NULL DEFAULT 0 CHECK (comp_cents >= 0);
ALTER TABLE customer_order DROP CONSTRAINT ck_order_total;
ALTER TABLE customer_order ADD CONSTRAINT ck_order_total
    CHECK (comp_cents <= subtotal_cents AND total_cents = subtotal_cents - comp_cents + tax_cents);
