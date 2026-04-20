package com.dentalcore.insurance.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "claim_procedures")
public class ClaimProcedure extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "procedure_code_id", nullable = false)
    private UUID procedureCodeId;

    @Column(name = "billed_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal billedAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    protected ClaimProcedure() {
    }

    public ClaimProcedure(UUID procedureCodeId, BigDecimal billedAmount) {
        this.procedureCodeId = procedureCodeId;
        this.billedAmount = billedAmount;
    }

    void attachTo(Claim claim) {
        this.claim = claim;
    }

    public void recordPayment(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public Claim getClaim() {
        return claim;
    }

    public UUID getProcedureCodeId() {
        return procedureCodeId;
    }

    public BigDecimal getBilledAmount() {
        return billedAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }
}
