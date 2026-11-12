-- Liên kết D01..D03 với tài khoản user-service (seed V3__seed_users.sql) bằng ID, không FK chéo DB.
UPDATE drivers SET user_id = 'dddddddd-0000-4000-8000-000000000001' WHERE id = '00000000-0000-0000-0000-0000000000d1';
UPDATE drivers SET user_id = 'dddddddd-0000-4000-8000-000000000002' WHERE id = '00000000-0000-0000-0000-0000000000d2';
UPDATE drivers SET user_id = 'dddddddd-0000-4000-8000-000000000003' WHERE id = '00000000-0000-0000-0000-0000000000d3';
