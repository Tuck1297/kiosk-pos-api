-- Demo promo codes (local/dev only). The employee discount isn't a code: it's applied by
-- employee ID at the rate in kiosk.discounts.employee-rate-bps (20%).
--
--   PARKFAN10  10% off any order
--   WELCOME5   $5 off orders of $20 or more
--   LIMITED50  50% off, first 3 orders only
--   SUMMER25   25% off, expired (to see that error)
INSERT INTO discount_code (code, description, kind, percent_bps, amount_cents, min_subtotal_cents, starts_at, ends_at, max_redemptions) VALUES
    ('PARKFAN10', '10% off any order',               'PERCENT', 1000, NULL, 0,    NULL, NULL, NULL),
    ('WELCOME5',  '$5 off orders of $20 or more',    'AMOUNT',  NULL, 500,  2000, NULL, NULL, NULL),
    ('LIMITED50', '50% off, first 3 orders only',    'PERCENT', 5000, NULL, 0,    NULL, NULL, 3),
    ('SUMMER25',  '25% off summer special',          'PERCENT', 2500, NULL, 0,    '2026-06-01T00:00:00-04:00', '2026-09-30T23:59:59-04:00', NULL);
