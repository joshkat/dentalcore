package com.dentalcore.billing.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A schedule for paying a balance off over time: an optional down payment plus
 * fixed installments until the plan total is reached (the last installment may
 * be smaller). The schedule is fixed at creation; only the status changes.
 */
@Entity
@Table(name = "payment_plans")
public class PaymentPlan extends BaseEntity {

    public enum Frequency {
        MONTHLY, BIWEEKLY
    }

    public enum Status {
        ACTIVE, COMPLETED, DEFAULTED, CANCELLED
    }

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "down_payment", nullable = false, precision = 10, scale = 2)
    private BigDecimal downPayment;

    @Column(name = "installment_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal installmentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 10)
    private Frequency frequency;

    @Column(name = "first_due_date", nullable = false)
    private LocalDate firstDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "notes")
    private String notes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "payment_plan_id", nullable = false)
    @OrderBy("dueDate ASC")
    private List<PaymentPlanInstallment> installments = new ArrayList<>();

    protected PaymentPlan() {
    }

    public PaymentPlan(UUID clinicId, UUID patientId, BigDecimal totalAmount,
                       BigDecimal downPayment, BigDecimal installmentAmount,
                       Frequency frequency, LocalDate firstDueDate, String notes) {
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.totalAmount = totalAmount;
        this.downPayment = downPayment;
        this.installmentAmount = installmentAmount;
        this.frequency = frequency;
        this.firstDueDate = firstDueDate;
        this.notes = notes;
    }

    public void addInstallment(LocalDate dueDate, BigDecimal amount) {
        installments.add(new PaymentPlanInstallment(dueDate, amount));
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getDownPayment() {
        return downPayment;
    }

    public BigDecimal getInstallmentAmount() {
        return installmentAmount;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public LocalDate getFirstDueDate() {
        return firstDueDate;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public List<PaymentPlanInstallment> getInstallments() {
        return installments;
    }
}
