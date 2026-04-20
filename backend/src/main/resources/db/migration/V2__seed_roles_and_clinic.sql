-- DentalCore PMS - V2: seed roles and default clinic
-- The initial admin user is bootstrapped at application startup (AdminBootstrap)
-- so no password hash is ever committed to a migration.

INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('DENTIST'),
    ('HYGIENIST'),
    ('FRONT_DESK'),
    ('BILLING'),
    ('READ_ONLY');

INSERT INTO clinics (id, name, timezone)
VALUES ('00000000-0000-0000-0000-000000000001', 'Main Clinic', 'America/New_York');
