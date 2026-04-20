package com.dentalcore.insurance.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fee_schedules")
public class FeeSchedule extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(mappedBy = "feeSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FeeScheduleFee> fees = new ArrayList<>();

    protected FeeSchedule() {
    }

    public FeeSchedule(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void upsertFee(UUID procedureCodeId, BigDecimal fee) {
        FeeScheduleFee existing = fees.stream()
                .filter(f -> f.getProcedureCodeId().equals(procedureCodeId))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.setFee(fee);
        } else {
            fees.add(new FeeScheduleFee(this, procedureCodeId, fee));
        }
    }

    public void removeFee(UUID procedureCodeId) {
        fees.removeIf(f -> f.getProcedureCodeId().equals(procedureCodeId));
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<FeeScheduleFee> getFees() {
        return fees;
    }
}
