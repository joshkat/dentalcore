package com.dentalcore.patients.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_phones")
public class PatientPhone extends BaseEntity {

    public enum PhoneType {
        HOME, MOBILE, WORK
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private PhoneType type;

    @Column(name = "number", nullable = false, length = 30)
    private String number;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected PatientPhone() {
    }

    public PatientPhone(PhoneType type, String number, boolean primary) {
        this.type = type;
        this.number = number;
        this.primary = primary;
    }

    void attachTo(Patient patient) {
        this.patient = patient;
    }

    public PhoneType getType() {
        return type;
    }

    public String getNumber() {
        return number;
    }

    public boolean isPrimary() {
        return primary;
    }
}
