-- Demo drivers around District 1, HCMC. Fixed IDs so demo scripts / Flow B can target D01.
INSERT INTO drivers (id, user_id, full_name, vehicle_plate, status, lat, lng) VALUES
    ('00000000-0000-0000-0000-0000000000d1', NULL, 'D01 Nguyen Van A', '59A-001.01', 'OFFLINE', 10.7769, 106.7009),
    ('00000000-0000-0000-0000-0000000000d2', NULL, 'D02 Tran Thi B',   '59A-002.02', 'OFFLINE', 10.7790, 106.6990),
    ('00000000-0000-0000-0000-0000000000d3', NULL, 'D03 Le Van C',     '59A-003.03', 'OFFLINE', 10.7720, 106.7050);
