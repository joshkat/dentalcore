package com.dentalcore.insurance.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "insurance_plans")
@SQLDelete(sql = "UPDATE insurance_plans SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class InsurancePlan extends BaseEntity {

    public enum PlanType {
        PPO, HMO, INDEMNITY, MEDICAID, DISCOUNT, OTHER
    }

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Column(name = "plan_name", nullable = false, length = 200)
    private String planName;

    @Column(name = "group_number", length = 50)
    private String groupNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType = PlanType.PPO;

    @Column(name = "annual_max", precision = 10, scale = 2)
    private BigDecimal annualMax;

    @Column(name = "deductible", precision = 10, scale = 2)
    private BigDecimal deductible;

    @Column(name = "coverage_notes", length = 2000)
    private String coverageNotes;

    @Column(name = "fee_schedule_id")
    private UUID feeScheduleId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected InsurancePlan() {
    }

    public InsurancePlan(UUID carrierId, String planName, PlanType planType) {
        this.carrierId = carrierId;
        this.planName = planName;
        this.planType = planType;
    }

    public void update(String planName, String groupNumber, PlanType planType,
                       BigDecimal annualMax, BigDecimal deductible, String coverageNotes) {
        this.planName = planName;
        this.groupNumber = groupNumber;
        this.planType = planType;
        this.annualMax = annualMax;
        this.deductible = deductible;
        this.coverageNotes = coverageNotes;
    }

    public void linkFeeSchedule(UUID feeScheduleId) {
        this.feeScheduleId = feeScheduleId;
    }

    public UUID getFeeScheduleId() {
        return feeScheduleId;
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public String getPlanName() {
        return planName;
    }

    public String getGroupNumber() {
        return groupNumber;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public BigDecimal getAnnualMax() {
        return annualMax;
    }

    public BigDecimal getDeductible() {
        return deductible;
    }

    public String getCoverageNotes() {
        return coverageNotes;
    }
}
