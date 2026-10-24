-- Local/dev seed data only. Enabled by adding classpath:db/seed to
-- spring.flyway.locations in application-local.yml. Never runs in prod.

INSERT INTO location (id, name, park_code, timezone, tax_rate_bps, currency) VALUES
    ('5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'Lakeside Snack Stand', 'LAKE', 'America/New_York', 650, 'USD'),
    ('7d2e8b3f-9a4c-4b5d-8f6e-3a5c7b9d1e22', 'Frontier Outpost Shop', 'FRNT', 'America/New_York', 650, 'USD');

INSERT INTO menu_item (id, location_id, sku, name, description, category, price_cents, available) VALUES
    ('9a3e7c51-2b4d-4e6f-8a1c-3d5e7f9b1c22', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'SNK-PRETZEL', 'Soft Pretzel', 'Warm pretzel with cheese dip', 'SNACK', 750, TRUE),
    ('1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e33', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'BEV-LEM-FRZ', 'Frozen Lemonade', NULL, 'BEVERAGE', 599, TRUE),
    ('2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f44', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'ENT-HOTDOG', 'Grilled Hot Dog', 'With chips', 'ENTREE', 1299, TRUE),
    ('3e4f5a6b-7c8d-4e9f-0a1b-2c3d4e5f6a55', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'DST-CHURRO', 'Churro', NULL, 'DESSERT', 650, FALSE),
    ('4f5a6b7c-8d9e-4f0a-1b2c-3d4e5f6a7b66', '7d2e8b3f-9a4c-4b5d-8f6e-3a5c7b9d1e22', 'MER-PIN-001', 'Collector Pin', 'Limited edition enamel pin', 'MERCHANDISE', 1799, TRUE),
    ('5a6b7c8d-9e0f-4a1b-2c3d-4e5f6a7b8c77', '7d2e8b3f-9a4c-4b5d-8f6e-3a5c7b9d1e22', 'BEV-WATER', 'Bottled Water', NULL, 'BEVERAGE', 450, TRUE);
