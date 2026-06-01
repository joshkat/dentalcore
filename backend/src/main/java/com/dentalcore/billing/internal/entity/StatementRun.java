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
 * One batch statement run: which period and minimum balance it covered, the
 * outcome, and one item per guarantor account that received a statement.
 */
@Entity
@Table(name = "statement_runs")
public class StatementRun extends BaseEntity {

    public enum Status {
        COMPLETED, FAILED
    }

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "min_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal minBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.COMPLETED;

    @Column(name = "total_accounts", nullable = false)
    private int totalAccounts;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "run_id", nullable = false)
    @OrderBy("balance DESC")
    private List<StatementRunItem> items = new ArrayList<>();

    protected StatementRun() {
    }

    public StatementRun(UUID clinicId, LocalDate fromDate, LocalDate toDate,
                        BigDecimal minBalance, UUID createdBy) {
        this.clinicId = clinicId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.minBalance = minBalance;
        this.createdBy = createdBy;
    }

    public void addItem(UUID guarantorPatientId, BigDecimal balance, UUID documentId) {
        items.add(new StatementRunItem(guarantorPatientId, balance, documentId));
    }

    public void finish(Status status, BigDecimal totalAmount) {
        this.status = status;
        this.totalAccounts = items.size();
        this.totalAmount = totalAmount;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public BigDecimal getMinBalance() {
        return minBalance;
    }

    public Status getStatus() {
        return status;
    }

    public int getTotalAccounts() {
        return totalAccounts;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<StatementRunItem> getItems() {
        return items;
    }
}
