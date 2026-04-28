-- DentalCore PMS - V13: cumulative deductible tracking on claims
--
-- The portion of the plan deductible consumed by a claim, recorded when the
-- claim is PAID. Estimates sum this per coverage and benefit year instead of
-- treating the deductible as met after any paid benefits. Legacy rows default
-- to 0, which makes estimates conservative (deductible appears unmet).
ALTER TABLE claims
    ADD COLUMN deductible_applied NUMERIC(10,2) NOT NULL DEFAULT 0;
