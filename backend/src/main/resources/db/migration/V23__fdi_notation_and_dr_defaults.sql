-- DentalCore PMS - V23: FDI tooth notation + Dominican Republic defaults
--
-- The product is deployed in the Dominican Republic, where dentistry uses the
-- FDI/ISO 3950 two-digit notation (quadrant digit + tooth digit: 11-48
-- permanent, 51-85 primary). Earlier versions charted in US Universal
-- notation (1-32 permanent, A-T primary); convert any existing data.
--
-- Also: new clinics default to the America/Santo_Domingo timezone, and the
-- seeded default clinic is moved off the old America/New_York default.

-- ---- Universal -> FDI mapping ----
CREATE TEMPORARY TABLE universal_to_fdi (
    universal VARCHAR(2) PRIMARY KEY,
    fdi       VARCHAR(2) NOT NULL
) ON COMMIT DROP;

INSERT INTO universal_to_fdi (universal, fdi) VALUES
    -- permanent maxillary, patient right to left (Universal 1-16)
    ('1', '18'), ('2', '17'), ('3', '16'), ('4', '15'),
    ('5', '14'), ('6', '13'), ('7', '12'), ('8', '11'),
    ('9', '21'), ('10', '22'), ('11', '23'), ('12', '24'),
    ('13', '25'), ('14', '26'), ('15', '27'), ('16', '28'),
    -- permanent mandibular, patient left to right (Universal 17-32)
    ('17', '38'), ('18', '37'), ('19', '36'), ('20', '35'),
    ('21', '34'), ('22', '33'), ('23', '32'), ('24', '31'),
    ('25', '41'), ('26', '42'), ('27', '43'), ('28', '44'),
    ('29', '45'), ('30', '46'), ('31', '47'), ('32', '48'),
    -- primary maxillary (Universal A-J)
    ('A', '55'), ('B', '54'), ('C', '53'), ('D', '52'), ('E', '51'),
    ('F', '61'), ('G', '62'), ('H', '63'), ('I', '64'), ('J', '65'),
    -- primary mandibular (Universal K-T)
    ('K', '75'), ('L', '74'), ('M', '73'), ('N', '72'), ('O', '71'),
    ('P', '81'), ('Q', '82'), ('R', '83'), ('S', '84'), ('T', '85');

-- Both notations share tokens ('14' is valid in each), so every table is
-- converted in a single joined UPDATE: each row is rewritten exactly once.
-- The perio unique constraints are rebuilt around the update because the
-- bijective rename can collide row-by-row mid-statement (e.g. Universal 14
-- becomes FDI 26 while a Universal 26 row still exists in the same exam).

ALTER TABLE perio_measurements DROP CONSTRAINT uq_perio_site;
UPDATE perio_measurements pm
SET tooth = m.fdi
FROM universal_to_fdi m
WHERE pm.tooth = m.universal;
ALTER TABLE perio_measurements
    ADD CONSTRAINT uq_perio_site UNIQUE (exam_id, tooth, site);

ALTER TABLE perio_tooth_findings DROP CONSTRAINT uq_perio_tooth;
UPDATE perio_tooth_findings pf
SET tooth = m.fdi
FROM universal_to_fdi m
WHERE pf.tooth = m.universal;
ALTER TABLE perio_tooth_findings
    ADD CONSTRAINT uq_perio_tooth UNIQUE (exam_id, tooth);

UPDATE tooth_conditions tc
SET tooth = m.fdi
FROM universal_to_fdi m
WHERE tc.tooth = m.universal;

-- Free-text tooth fields (treatment planning / completed work): convert exact
-- Universal tokens only, leave anything else untouched.
UPDATE planned_procedures pp
SET tooth = m.fdi
FROM universal_to_fdi m
WHERE pp.tooth = m.universal;

UPDATE completed_procedures cp
SET tooth = m.fdi
FROM universal_to_fdi m
WHERE cp.tooth = m.universal;

-- ---- Dominican Republic clinic timezone ----
ALTER TABLE clinics ALTER COLUMN timezone SET DEFAULT 'America/Santo_Domingo';

UPDATE clinics
SET timezone = 'America/Santo_Domingo'
WHERE id = '00000000-0000-0000-0000-000000000001'
  AND timezone = 'America/New_York';
