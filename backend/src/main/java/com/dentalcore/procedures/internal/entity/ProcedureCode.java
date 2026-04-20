package com.dentalcore.procedures.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "procedure_codes")
public class ProcedureCode extends BaseEntity {

    public enum Category {
        DIAGNOSTIC, PREVENTIVE, RESTORATIVE, ENDODONTICS, PERIODONTICS,
        PROSTHODONTICS, ORAL_SURGERY, ORTHODONTICS, ADJUNCTIVE, OTHER
    }

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private Category category;

    @Column(name = "standard_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal standardFee = BigDecimal.ZERO;

    @Column(name = "cdt_code", length = 10)
    private String cdtCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ProcedureCode() {
    }

    public ProcedureCode(String code, String description, Category category,
                         BigDecimal standardFee, String cdtCode) {
        this.code = code;
        this.description = description;
        this.category = category;
        this.standardFee = standardFee;
        this.cdtCode = cdtCode;
    }

    public void update(String code, String description, Category category,
                       BigDecimal standardFee, String cdtCode, boolean active) {
        this.code = code;
        this.description = description;
        this.category = category;
        this.standardFee = standardFee;
        this.cdtCode = cdtCode;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getStandardFee() {
        return standardFee;
    }

    public String getCdtCode() {
        return cdtCode;
    }

    public boolean isActive() {
        return active;
    }
}
