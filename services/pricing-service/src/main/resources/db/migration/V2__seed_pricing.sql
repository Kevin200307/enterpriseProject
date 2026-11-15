INSERT INTO zones (id, name, code, active) VALUES
('10000000-0000-0000-0000-000000000001', 'Colombo', 'CMB', TRUE),
('10000000-0000-0000-0000-000000000002', 'Gampaha', 'GAM', TRUE),
('10000000-0000-0000-0000-000000000003', 'Kalutara', 'KAL', TRUE),
('10000000-0000-0000-0000-000000000004', 'Kandy', 'KAN', TRUE),
('10000000-0000-0000-0000-000000000005', 'Galle', 'GAL', TRUE),
('10000000-0000-0000-0000-000000000006', 'Jaffna', 'JAF', TRUE),
('10000000-0000-0000-0000-000000000007', 'Matara', 'MAT', TRUE),
('10000000-0000-0000-0000-000000000008', 'Kurunegala', 'KUR', TRUE);

INSERT INTO zone_cities (zone_id, city_name, normalized_city) VALUES
('10000000-0000-0000-0000-000000000001', 'Colombo', 'colombo'),
('10000000-0000-0000-0000-000000000002', 'Gampaha', 'gampaha'),
('10000000-0000-0000-0000-000000000003', 'Kalutara', 'kalutara'),
('10000000-0000-0000-0000-000000000004', 'Kandy', 'kandy'),
('10000000-0000-0000-0000-000000000005', 'Galle', 'galle'),
('10000000-0000-0000-0000-000000000006', 'Jaffna', 'jaffna'),
('10000000-0000-0000-0000-000000000007', 'Matara', 'matara'),
('10000000-0000-0000-0000-000000000008', 'Kurunegala', 'kurunegala');

-- All 64 ordered routes and all size bands are covered. UUIDs are stable across clean installs.
INSERT INTO pricing_rules (id, zone_from_id, zone_to_id, size_band, base_fee, per_kg, min_fee,
    max_weight_kg, included_weight_kg, express_surcharge_percent, active, effective_from)
SELECT (substr(md5(concat(f.code, ':', t.code, ':', s.band)), 1, 8) || '-' ||
        substr(md5(concat(f.code, ':', t.code, ':', s.band)), 9, 4) || '-' ||
        substr(md5(concat(f.code, ':', t.code, ':', s.band)), 13, 4) || '-' ||
        substr(md5(concat(f.code, ':', t.code, ':', s.band)), 17, 4) || '-' ||
        substr(md5(concat(f.code, ':', t.code, ':', s.band)), 21, 12))::uuid,
    f.id, t.id, s.band,
    (CASE WHEN f.code = t.code THEN 350 ELSE 650 END + s.offset_fee + (f.ordinal + t.ordinal) * 25)::numeric(10,2),
    (CASE s.band WHEN 'SMALL' THEN 45 WHEN 'MEDIUM' THEN 70 ELSE 100 END)::numeric(10,2),
    (CASE WHEN f.code = t.code THEN 350 ELSE 650 END + s.offset_fee)::numeric(10,2),
    (CASE s.band WHEN 'SMALL' THEN 5 WHEN 'MEDIUM' THEN 15 ELSE 30 END)::numeric(10,3),
    1.00, 15.00, TRUE, '2020-01-01T00:00:00Z'::timestamptz
FROM (VALUES
 ('10000000-0000-0000-0000-000000000001'::uuid, 'CMB', 0),
 ('10000000-0000-0000-0000-000000000002'::uuid, 'GAM', 1),
 ('10000000-0000-0000-0000-000000000003'::uuid, 'KAL', 2),
 ('10000000-0000-0000-0000-000000000004'::uuid, 'KAN', 3),
 ('10000000-0000-0000-0000-000000000005'::uuid, 'GAL', 4),
 ('10000000-0000-0000-0000-000000000006'::uuid, 'JAF', 5),
 ('10000000-0000-0000-0000-000000000007'::uuid, 'MAT', 6),
 ('10000000-0000-0000-0000-000000000008'::uuid, 'KUR', 7)
) AS f(id, code, ordinal)
CROSS JOIN (VALUES
 ('10000000-0000-0000-0000-000000000001'::uuid, 'CMB', 0),
 ('10000000-0000-0000-0000-000000000002'::uuid, 'GAM', 1),
 ('10000000-0000-0000-0000-000000000003'::uuid, 'KAL', 2),
 ('10000000-0000-0000-0000-000000000004'::uuid, 'KAN', 3),
 ('10000000-0000-0000-0000-000000000005'::uuid, 'GAL', 4),
 ('10000000-0000-0000-0000-000000000006'::uuid, 'JAF', 5),
 ('10000000-0000-0000-0000-000000000007'::uuid, 'MAT', 6),
 ('10000000-0000-0000-0000-000000000008'::uuid, 'KUR', 7)
) AS t(id, code, ordinal)
CROSS JOIN (VALUES ('SMALL', 0), ('MEDIUM', 250), ('LARGE', 550)) AS s(band, offset_fee);