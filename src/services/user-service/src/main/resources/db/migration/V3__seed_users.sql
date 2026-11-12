-- Tài khoản demo, mật khẩu chung: demo1234 (dữ liệu seed, không phải secret).
-- driver_id trỏ sang drivers.id của driver-service bằng ID (R1: không FK chéo DB).
ALTER TABLE users ADD COLUMN driver_id UUID;

INSERT INTO users (id, email, full_name, role, driver_id, password_hash) VALUES
    ('bbbbbbbb-0000-4000-8000-000000000001', 'passenger1@rideflow.demo', 'Passenger One', 'PASSENGER', NULL, '$2a$10$RvBCtHif/p4sgZ/NOerhWOOAWFHf0BMTIZgh6SH5wZRPufAkEa.Um'),
    ('bbbbbbbb-0000-4000-8000-000000000002', 'passenger2@rideflow.demo', 'Passenger Two', 'PASSENGER', NULL, '$2a$10$dK27jVb8zU/qrs.QnMp8tuA56TRSqVgEOOhvNI98cvxw1mIl0DHbC'),
    ('dddddddd-0000-4000-8000-000000000001', 'driver1@rideflow.demo', 'D01 Nguyen Van A', 'DRIVER', '00000000-0000-0000-0000-0000000000d1', '$2a$10$y8uvkNRjNbPWrsvHa.oa4.q/wopT.Wk2v3niW1BXLYhJ070S/acEm'),
    ('dddddddd-0000-4000-8000-000000000002', 'driver2@rideflow.demo', 'D02 Tran Thi B', 'DRIVER', '00000000-0000-0000-0000-0000000000d2', '$2a$10$oa6Xzw1hz/4NPu8vH1ZgMOnqA0W/9oOP7hJSsDoxLKuaJLsQC.aoa'),
    ('dddddddd-0000-4000-8000-000000000003', 'driver3@rideflow.demo', 'D03 Le Van C', 'DRIVER', '00000000-0000-0000-0000-0000000000d3', '$2a$10$RvBCtHif/p4sgZ/NOerhWOOAWFHf0BMTIZgh6SH5wZRPufAkEa.Um');
