-- Bước mới: tài xế đã tới điểm đón (ACCEPTED -> ARRIVED -> IN_PROGRESS). CHECK inline của V2 có tên mặc định trips_status_check.
ALTER TABLE trips DROP CONSTRAINT IF EXISTS trips_status_check;
ALTER TABLE trips ADD CONSTRAINT trips_status_check CHECK (status IN ('REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'PAYMENT_PENDING', 'PAID', 'PAYMENT_FAILED', 'NO_DRIVER', 'CANCELLED'));
