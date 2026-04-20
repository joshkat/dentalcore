package com.dentalcore.insurance.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Coverage percentage for a procedure category under a plan. */
@Entity
@Table(name = "coverage_rules")
public class CoverageRule extends BaseEntity {

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "coverage_percent", nullable = false)
    private int coveragePercent;

    protected CoverageRule() {
    }

    public CoverageRule(UUID planId, String category, int coveragePercent) {
        this.planId = planId;
        this.category = category;
        this.coveragePercent = coveragePercent;
    }

    public void setCoveragePercent(int coveragePercent) {
        this.coveragePercent = coveragePercent;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getCategory() {
        return category;
    }

    public int getCoveragePercent() {
        return coveragePercent;
    }
}
