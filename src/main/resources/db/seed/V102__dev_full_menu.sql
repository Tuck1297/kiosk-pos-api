-- Fills out the Lakeside Snack Stand to a 20-item quick-service menu (local/dev only).
-- With the 4 items from V100 (one, the churro, sold out to show that state), the
-- register has a full menu as soon as a cashier signs in.

INSERT INTO menu_item (id, location_id, sku, name, description, category, price_cents, available) VALUES
    -- Entrees
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d01', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'ENT-TURKEY-LEG', 'Smoked Turkey Leg', 'Slow-smoked, served whole', 'ENTREE', 1499, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d02', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'ENT-TENDERS', 'Chicken Tenders & Fries', 'Four tenders, honey mustard', 'ENTREE', 1349, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d03', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'ENT-CHEESEBURGER', 'Classic Cheeseburger', 'With lettuce, tomato and fries', 'ENTREE', 1399, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d04', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'ENT-VEGGIE-WRAP', 'Grilled Veggie Wrap', 'Hummus, peppers, spinach', 'ENTREE', 1199, TRUE),
    -- Snacks
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d05', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'SNK-KETTLE-CORN', 'Kettle Corn Bucket', 'Sweet and salty, shareable', 'SNACK', 899, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d06', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'SNK-NACHOS', 'Loaded Nachos', 'Cheese, jalapeños, salsa', 'SNACK', 1099, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d07', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'SNK-PICKLE', 'Giant Dill Pickle', NULL, 'SNACK', 549, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d08', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'SNK-FRUIT-CUP', 'Fresh Fruit Cup', 'Pineapple, melon, grapes', 'SNACK', 699, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d09', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'SNK-GARLIC-FRIES', 'Garlic Parmesan Fries', NULL, 'SNACK', 749, TRUE),
    -- Desserts
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d10', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'DST-FUNNEL-CAKE', 'Funnel Cake', 'Powdered sugar, strawberries', 'DESSERT', 999, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d11', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'DST-COTTON-CANDY', 'Cotton Candy', NULL, 'DESSERT', 599, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d12', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'DST-PINEAPPLE-SOFT', 'Pineapple Soft-Serve', 'Dairy-free', 'DESSERT', 699, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d13', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'DST-FROZEN-BANANA', 'Chocolate-Dipped Frozen Banana', NULL, 'DESSERT', 649, TRUE),
    -- Beverages
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d14', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'BEV-FOUNTAIN', 'Fountain Soda', 'Refillable cup', 'BEVERAGE', 449, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d15', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'BEV-WATER-LAKE', 'Bottled Water', NULL, 'BEVERAGE', 400, TRUE),
    ('6b7c8d9e-0f1a-4b2c-8d3e-4f5a6b7c8d16', '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11', 'BEV-COLD-BREW', 'Iced Cold Brew', NULL, 'BEVERAGE', 549, TRUE);
