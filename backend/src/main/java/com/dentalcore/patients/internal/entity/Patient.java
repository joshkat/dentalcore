package com.dentalcore.patients.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "patients")
@SQLDelete(sql = "UPDATE patients SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Patient extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 10)
    private Sex sex = Sex.UNKNOWN;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "preferred_language", length = 50)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PatientStatus status = PatientStatus.ACTIVE;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relationship", length = 50)
    private String emergencyContactRelationship;

    @Column(name = "notes")
    private String notes;

    // ---- profile depth (V7) ----

    public enum ContactMethod {
        EMAIL, SMS, PHONE, MAIL
    }

    public enum SmokingStatus {
        NEVER, FORMER, CURRENT, UNKNOWN
    }

    @Column(name = "preferred_name", length = 100)
    private String preferredName;

    @Column(name = "pronouns", length = 30)
    private String pronouns;

    @Column(name = "employer", length = 200)
    private String employer;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "referral_source", length = 200)
    private String referralSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_method", length = 10)
    private ContactMethod preferredContactMethod;

    @Column(name = "sms_consent", nullable = false)
    private boolean smsConsent;

    @Column(name = "email_consent", nullable = false)
    private boolean emailConsent;

    @Column(name = "pharmacy_name", length = 200)
    private String pharmacyName;

    @Column(name = "pharmacy_phone", length = 30)
    private String pharmacyPhone;

    @Column(name = "primary_provider_id")
    private UUID primaryProviderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "smoking_status", nullable = false, length = 10)
    private SmokingStatus smokingStatus = SmokingStatus.UNKNOWN;

    @Column(name = "recall_interval_months", nullable = false)
    private int recallIntervalMonths = 6;

    @Column(name = "next_recall_date")
    private LocalDate nextRecallDate;

    /**
     * Account guarantor (V15): the patient whose statement this account rolls
     * up to. NULL means the patient guarantees their own account. One level
     * only — a guarantor never has a guarantor of their own.
     */
    @Column(name = "guarantor_id")
    private UUID guarantorId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PatientPhone> phones = new ArrayList<>();

    protected Patient() {
    }

    public Patient(UUID clinicId, String firstName, String lastName, LocalDate dateOfBirth, Sex sex) {
        this.clinicId = clinicId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.sex = sex;
    }

    public void replacePhones(List<PatientPhone> newPhones) {
        phones.clear();
        newPhones.forEach(phone -> {
            phone.attachTo(this);
            phones.add(phone);
        });
    }

    public void setStatus(PatientStatus status) {
        this.status = status;
    }

    public String fullName() {
        return lastName + ", " + firstName;
    }

    // demographics update
    public void updateDemographics(String firstName, String middleName, String lastName,
                                   LocalDate dateOfBirth, Sex sex, String email,
                                   String addressLine1, String addressLine2, String city,
                                   String state, String postalCode, String preferredLanguage,
                                   String emergencyContactName, String emergencyContactPhone,
                                   String emergencyContactRelationship, String notes) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.sex = sex;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.preferredLanguage = preferredLanguage;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.emergencyContactRelationship = emergencyContactRelationship;
        this.notes = notes;
    }

    public void updateProfileExtras(String preferredName, String pronouns, String employer,
                                    String occupation, String referralSource,
                                    ContactMethod preferredContactMethod, boolean smsConsent,
                                    boolean emailConsent, String pharmacyName,
                                    String pharmacyPhone, UUID primaryProviderId,
                                    SmokingStatus smokingStatus) {
        this.preferredName = preferredName;
        this.pronouns = pronouns;
        this.employer = employer;
        this.occupation = occupation;
        this.referralSource = referralSource;
        this.preferredContactMethod = preferredContactMethod;
        this.smsConsent = smsConsent;
        this.emailConsent = emailConsent;
        this.pharmacyName = pharmacyName;
        this.pharmacyPhone = pharmacyPhone;
        this.primaryProviderId = primaryProviderId;
        this.smokingStatus = smokingStatus;
    }

    public void setGuarantorId(UUID guarantorId) {
        this.guarantorId = guarantorId;
    }

    public UUID getGuarantorId() {
        return guarantorId;
    }

    public void updateRecall(int intervalMonths, LocalDate nextRecallDate) {
        this.recallIntervalMonths = intervalMonths;
        this.nextRecallDate = nextRecallDate;
    }

    public String getPreferredName() {
        return preferredName;
    }

    public String getPronouns() {
        return pronouns;
    }

    public String getEmployer() {
        return employer;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getReferralSource() {
        return referralSource;
    }

    public ContactMethod getPreferredContactMethod() {
        return preferredContactMethod;
    }

    public boolean isSmsConsent() {
        return smsConsent;
    }

    public boolean isEmailConsent() {
        return emailConsent;
    }

    public String getPharmacyName() {
        return pharmacyName;
    }

    public String getPharmacyPhone() {
        return pharmacyPhone;
    }

    public UUID getPrimaryProviderId() {
        return primaryProviderId;
    }

    public SmokingStatus getSmokingStatus() {
        return smokingStatus;
    }

    public int getRecallIntervalMonths() {
        return recallIntervalMonths;
    }

    public LocalDate getNextRecallDate() {
        return nextRecallDate;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Sex getSex() {
        return sex;
    }

    public String getEmail() {
        return email;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public PatientStatus getStatus() {
        return status;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public String getEmergencyContactRelationship() {
        return emergencyContactRelationship;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public List<PatientPhone> getPhones() {
        return phones;
    }
}
