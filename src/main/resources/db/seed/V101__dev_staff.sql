-- Demo staff (local/dev only). PINs are hashed here with bcrypt via pgcrypto; the API
-- verifies them with Spring Security's BCryptPasswordEncoder ($2a$ hashes).
--
--   C2001  Alex P.   cashier  PIN 1111
--   M1001  Jamie D.  manager  PIN 2468
--   M1002  Riley S.  manager  PIN 1357
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO staff (id, employee_id, name, role, pin_hash) VALUES
    ('a1b2c3d4-0000-4000-8000-000000002001', 'C2001', 'Alex P.',  'CASHIER', crypt('1111', gen_salt('bf', 10))),
    ('a1b2c3d4-0000-4000-8000-000000001001', 'M1001', 'Jamie D.', 'MANAGER', crypt('2468', gen_salt('bf', 10))),
    ('a1b2c3d4-0000-4000-8000-000000001002', 'M1002', 'Riley S.', 'MANAGER', crypt('1357', gen_salt('bf', 10)));
