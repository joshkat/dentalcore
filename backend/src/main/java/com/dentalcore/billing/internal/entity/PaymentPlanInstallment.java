package com.dentalcore.billing.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One scheduled installment of a {@link PaymentPlan}. */
@Entity
@Table(name = "payment_plan_installments")
public class PaymentPlanInstallment extends BaseEntity {

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    protected PaymentPlanInstallment() {
    }

    PaymentPlanInstallment(LocalDate dueDate, BigDecimal amount) {
        this.dueDate = dueDate;
        this.amount = amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
