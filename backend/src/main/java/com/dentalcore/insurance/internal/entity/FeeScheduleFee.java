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
@Table(name = "fee_schedule_fees")
public class FeeScheduleFee extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_schedule_id", nullable = false)
    private FeeSchedule feeSchedule;

    @Column(name = "procedure_code_id", nullable = false)
    private UUID procedureCodeId;

    @Column(name = "fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    protected FeeScheduleFee() {
    }

    FeeScheduleFee(FeeSchedule feeSchedule, UUID procedureCodeId, BigDecimal fee) {
        this.feeSchedule = feeSchedule;
        this.procedureCodeId = procedureCodeId;
        this.fee = fee;
    }

    void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public UUID getProcedureCodeId() {
        return procedureCodeId;
    }

    public BigDecimal getFee() {
        return fee;
    }
}
