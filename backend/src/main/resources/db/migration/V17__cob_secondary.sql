-- DentalCore PMS - V17: coordination of benefits (secondary insurance)
--
-- A secondary claim references the primary claim it coordinates with via
-- parent_claim_id. The partial unique index guarantees a primary claim can
-- have at most one secondary claim, even under concurrent requests (the
-- service-level duplicate check returns 409 first; this is the backstop).
ALTER TABLE claims
    ADD COLUMN parent_claim_id UUID NULL REFERENCES claims (id);

CREATE UNIQUE INDEX uq_claims_one_secondary_per_parent
    ON claims (parent_claim_id)
    WHERE parent_claim_id IS NOT NULL;
