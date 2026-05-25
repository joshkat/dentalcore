package com.dentalcore.billing.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One guarantor account included in a statement run. */
@Entity
@Table(name = "statement_run_items")
public class StatementRunItem extends BaseEntity {

    @Column(name = "guarantor_patient_id", nullable = false)
    private UUID guarantorPatientId;

    @Column(name = "balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal balance;

    @Column(name = "document_id")
    private UUID documentId;

    protected StatementRunItem() {
    }

    StatementRunItem(UUID guarantorPatientId, BigDecimal balance, UUID documentId) {
        this.guarantorPatientId = guarantorPatientId;
        this.balance = balance;
        this.documentId = documentId;
    }

    public UUID getGuarantorPatientId() {
        return guarantorPatientId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}
