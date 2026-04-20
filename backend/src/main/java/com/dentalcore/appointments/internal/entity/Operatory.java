package com.dentalcore.appointments.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "operatories")
public class Operatory extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Operatory() {
    }

    public Operatory(UUID clinicId, String name) {
        this.clinicId = clinicId;
        this.name = name;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
