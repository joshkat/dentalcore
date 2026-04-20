package com.dentalcore.providers.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "providers")
@SQLDelete(sql = "UPDATE providers SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Provider extends BaseEntity {

    public enum ProviderType {
        DENTIST, HYGIENIST, ASSISTANT
    }

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ProviderType type;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "npi", length = 10)
    private String npi;

    @Column(name = "specialty", length = 100)
    private String specialty;

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    @Column(name = "license_state", length = 50)
    private String licenseState;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "color", nullable = false, length = 7)
    private String color = "#3b82f6";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Provider() {
    }

    public Provider(UUID clinicId, ProviderType type, String firstName, String lastName) {
        this.clinicId = clinicId;
        this.type = type;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void update(ProviderType type, String firstName, String lastName, String npi,
                       String specialty, String licenseNumber, String licenseState,
                       String email, String phone, String color, boolean active) {
        this.type = type;
        this.firstName = firstName;
        this.lastName = lastName;
        this.npi = npi;
        this.specialty = specialty;
        this.licenseNumber = licenseNumber;
        this.licenseState = licenseState;
        this.email = email;
        this.phone = phone;
        this.color = color;
        this.active = active;
    }

    public String fullName() {
        return lastName + ", " + firstName;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ProviderType getType() {
        return type;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getNpi() {
        return npi;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public String getLicenseState() {
        return licenseState;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getColor() {
        return color;
    }

    public boolean isActive() {
        return active;
    }
}
