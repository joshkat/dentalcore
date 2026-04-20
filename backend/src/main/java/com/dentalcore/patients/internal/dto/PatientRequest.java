package com.dentalcore.patients.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Shared shape for create and full update. */
public record PatientRequest(
        @NotBlank @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String middleName,

        @NotBlank @Size(max = 100)
        String lastName,

        @NotNull @Past
        LocalDate dateOfBirth,

        @NotNull @Pattern(regexp = "MALE|FEMALE|OTHER|UNKNOWN", message = "Unknown sex value")
        String sex,

        @Email @Size(max = 320)
        String email,

        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 50) String state,
        @Size(max = 20) String postalCode,
        @Size(max = 50) String preferredLanguage,

        @Size(max = 200) String emergencyContactName,
        @Size(max = 30) String emergencyContactPhone,
        @Size(max = 50) String emergencyContactRelationship,

        @Size(max = 10_000) String notes,

        @Valid
        List<PhoneDto> phones,

        // ---- profile depth ----
        @Size(max = 100) String preferredName,
        @Size(max = 30) String pronouns,
        @Size(max = 200) String employer,
        @Size(max = 100) String occupation,
        @Size(max = 200) String referralSource,

        @Pattern(regexp = "EMAIL|SMS|PHONE|MAIL", message = "Unknown contact method")
        String preferredContactMethod,

        Boolean smsConsent,
        Boolean emailConsent,

        @Size(max = 200) String pharmacyName,
        @Size(max = 30) String pharmacyPhone,

        java.util.UUID primaryProviderId,

        @Pattern(regexp = "NEVER|FORMER|CURRENT|UNKNOWN", message = "Unknown smoking status")
        String smokingStatus
) {
    public List<PhoneDto> phonesOrEmpty() {
        return phones == null ? List.of() : phones;
    }

    public boolean smsConsentOrDefault() {
        return Boolean.TRUE.equals(smsConsent);
    }

    public boolean emailConsentOrDefault() {
        return Boolean.TRUE.equals(emailConsent);
    }

    public String smokingStatusOrDefault() {
        return smokingStatus == null ? "UNKNOWN" : smokingStatus;
    }
}
