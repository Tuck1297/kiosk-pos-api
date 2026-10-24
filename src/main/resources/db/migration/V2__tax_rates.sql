-- Tax rates live in their own table so a rate is defined once and shared by
-- every location in that tax jurisdiction. Rates are basis points: 650 = 6.50%.
--
-- Orders snapshot the rate they were priced with (customer_order.tax_rate_bps),
-- so changing a rate later never alters an existing order.

CREATE TABLE tax_rate (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        TEXT        NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9-]{2,32}$'),
    name        TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
    rate_bps    INTEGER     NOT NULL CHECK (rate_bps BETWEEN 0 AND 3000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Move any existing per-location rates into the new table.
INSERT INTO tax_rate (code, name, rate_bps)
SELECT DISTINCT 'RATE-' || tax_rate_bps,
                'Sales tax ' || to_char(tax_rate_bps / 100.0, 'FM990.00') || '%',
                tax_rate_bps
FROM location;

ALTER TABLE location ADD COLUMN tax_rate_id UUID REFERENCES tax_rate (id);

UPDATE location l
SET tax_rate_id = t.id
FROM tax_rate t
WHERE t.code = 'RATE-' || l.tax_rate_bps;

ALTER TABLE location ALTER COLUMN tax_rate_id SET NOT NULL;

CREATE INDEX ix_location_tax_rate ON location (tax_rate_id);

-- Snapshot of the rate each order was priced with.
ALTER TABLE customer_order ADD COLUMN tax_rate_bps INTEGER CHECK (tax_rate_bps BETWEEN 0 AND 3000);

UPDATE customer_order o
SET tax_rate_bps = l.tax_rate_bps
FROM location l
WHERE l.id = o.location_id;

ALTER TABLE customer_order ALTER COLUMN tax_rate_bps SET NOT NULL;

ALTER TABLE location DROP COLUMN tax_rate_bps;
