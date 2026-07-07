package com.dentalcore.infrastructure.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional demo dataset for evaluation environments (SEED_DEMO_DATA=true).
 * Seeds with plain SQL and fixed UUIDs so it is idempotent and respects
 * module boundaries the same way Flyway does. Never enable in production.
 */
@Component
@Order(10) // after AdminBootstrap
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public DemoDataSeeder(JdbcTemplate jdbc,
                          @Value("${SEED_DEMO_DATA:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        log.info("Seeding demo data (SEED_DEMO_DATA=true)…");

        // ---- providers ----
        jdbc.update("""
                INSERT INTO providers (id, clinic_id, type, first_name, last_name, npi, specialty, color)
                VALUES
                  ('d0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
                   'DENTIST',   'Diane',  'Demo-Dentist', '1999999991', 'General Dentistry', '#2563eb'),
                  ('d0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
                   'HYGIENIST', 'Harold', 'Demo-Hygienist', '1999999992', NULL, '#16a34a')
                ON CONFLICT (id) DO NOTHING
                """);

        // ---- operatory ----
        jdbc.update("""
                INSERT INTO operatories (id, clinic_id, name)
                VALUES ('d0000000-0000-0000-0000-00000000000a',
                        '00000000-0000-0000-0000-000000000001', 'Demo Operatory')
                ON CONFLICT (id) DO NOTHING
                """);

        // ---- patients ----
        jdbc.update("""
                INSERT INTO patients (id, clinic_id, first_name, last_name, date_of_birth, sex,
                                      email, status, preferred_name, smoking_status)
                VALUES
                  ('da000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
                   'Emma', 'Demoson', '1985-03-14', 'FEMALE', 'emma.demoson@example.com', 'ACTIVE',
                   'Em', 'NEVER'),
                  ('da000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
                   'Liam', 'Demoson', '1982-07-22', 'MALE', 'liam.demoson@example.com', 'ACTIVE',
                   NULL, 'FORMER'),
                  ('da000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001',
                   'Olivia', 'Sample', '2014-11-02', 'FEMALE', NULL, 'ACTIVE', NULL, 'UNKNOWN'),
                  ('da000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001',
                   'Noah', 'Specimen', '1965-01-30', 'MALE', 'noah.specimen@example.com', 'ACTIVE',
                   NULL, 'CURRENT')
                ON CONFLICT (id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO patient_phones (id, patient_id, type, number, is_primary)
                VALUES
                  ('db000000-0000-0000-0000-000000000001', 'da000000-0000-0000-0000-000000000001',
                   'MOBILE', '555-201-0001', TRUE),
                  ('db000000-0000-0000-0000-000000000002', 'da000000-0000-0000-0000-000000000002',
                   'MOBILE', '555-201-0002', TRUE)
                ON CONFLICT (id) DO NOTHING
                """);

        // family: Olivia is Emma's child (both directions)
        jdbc.update("""
                INSERT INTO family_links (id, patient_id, related_patient_id, relationship)
                VALUES
                  ('dc000000-0000-0000-0000-000000000001', 'da000000-0000-0000-0000-000000000001',
                   'da000000-0000-0000-0000-000000000003', 'CHILD'),
                  ('dc000000-0000-0000-0000-000000000002', 'da000000-0000-0000-0000-000000000003',
                   'da000000-0000-0000-0000-000000000001', 'PARENT')
                ON CONFLICT (id) DO NOTHING
                """);

        // ---- medical alerts ----
        jdbc.update("""
                INSERT INTO medical_alerts (id, patient_id, type, description, severity)
                VALUES
                  ('dd000000-0000-0000-0000-000000000001', 'da000000-0000-0000-0000-000000000001',
                   'ALLERGY', 'Penicillin', 'HIGH'),
                  ('dd000000-0000-0000-0000-000000000002', 'da000000-0000-0000-0000-000000000004',
                   'MEDICATION', 'Warfarin 5mg daily', 'HIGH')
                ON CONFLICT (id) DO NOTHING
                """);

        // ---- tooth conditions ----
        jdbc.update("""
                INSERT INTO tooth_conditions (id, patient_id, tooth, surfaces, condition, status, notes)
                VALUES
                  ('de000000-0000-0000-0000-000000000001', 'da000000-0000-0000-0000-000000000001',
                   '26', 'MOD', 'CARIES', 'ACTIVE', 'Deep distal decay'),
                  ('de000000-0000-0000-0000-000000000002', 'da000000-0000-0000-0000-000000000001',
                   '36', NULL, 'CROWN', 'ACTIVE', 'PFM crown 2019'),
                  ('de000000-0000-0000-0000-000000000003', 'da000000-0000-0000-0000-000000000004',
                   '18', NULL, 'MISSING', 'ACTIVE', NULL)
                ON CONFLICT (id) DO NOTHING
                """);

        // ---- appointments today + upcoming (own provider/operatory: no overlap risk) ----
        jdbc.update("""
                INSERT INTO appointments (id, clinic_id, patient_id, provider_id, operatory_id,
                                          starts_at, ends_at, status, notes)
                VALUES
                  ('df000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
                   'da000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001',
                   'd0000000-0000-0000-0000-00000000000a',
                   date_trunc('day', now()) + interval '9 hours',
                   date_trunc('day', now()) + interval '10 hours', 'COMPLETED', 'Crown prep #26'),
                  ('df000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
                   'da000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000002',
                   'd0000000-0000-0000-0000-00000000000a',
                   date_trunc('day', now()) + interval '10 hours',
                   date_trunc('day', now()) + interval '11 hours', 'SCHEDULED', 'Prophy + exam'),
                  ('df000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001',
                   'da000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000002',
                   'd0000000-0000-0000-0000-00000000000a',
                   date_trunc('day', now()) + interval '7 days 14 hours',
                   date_trunc('day', now()) + interval '7 days 14 hours 30 minutes',
                   'SCHEDULED', 'Child prophy')
                ON CONFLICT (id) DO NOTHING
                """);

        // ---- treatment plan for Emma's crown ----
        jdbc.update("""
                INSERT INTO treatment_plans (id, clinic_id, patient_id, provider_id, title, status)
                VALUES ('e0000000-0000-0000-0000-000000000001',
                        '00000000-0000-0000-0000-000000000001',
                        'da000000-0000-0000-0000-000000000001',
                        'd0000000-0000-0000-0000-000000000001',
                        'Restore upper left', 'PRESENTED')
                ON CONFLICT (id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO planned_procedures (id, treatment_plan_id, procedure_code_id, tooth,
                                                surface, priority, status, estimated_cost)
                SELECT 'e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
                       pc.id, '26', 'MOD', 1, 'PLANNED', pc.standard_fee
                FROM procedure_codes pc WHERE pc.code = 'D2740'
                ON CONFLICT (id) DO NOTHING
                """);

        // ---- ledger: today's production for the completed visit ----
        jdbc.update("""
                INSERT INTO ledger_entries (id, clinic_id, patient_id, type, amount, description,
                                            procedure_code_id, appointment_id, entry_date)
                SELECT 'e2000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
                       'da000000-0000-0000-0000-000000000001', 'CHARGE', pc.standard_fee,
                       pc.code || ' — ' || pc.description, pc.id,
                       'df000000-0000-0000-0000-000000000001', CURRENT_DATE
                FROM procedure_codes pc WHERE pc.code = 'D2950'
                ON CONFLICT (id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO ledger_entries (id, clinic_id, patient_id, type, amount, description,
                                            method, entry_date)
                VALUES ('e2000000-0000-0000-0000-000000000002',
                        '00000000-0000-0000-0000-000000000001',
                        'da000000-0000-0000-0000-000000000001', 'PAYMENT', -100.00,
                        'Patient payment (CARD)', 'CARD', CURRENT_DATE)
                ON CONFLICT (id) DO NOTHING
                """);

        log.info("Demo data seeded (idempotent — fixed ids).");
    }
}
