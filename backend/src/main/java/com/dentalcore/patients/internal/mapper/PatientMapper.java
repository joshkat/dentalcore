package com.dentalcore.patients.internal.mapper;

import com.dentalcore.patients.api.PatientSummary;
import com.dentalcore.patients.internal.dto.MedicalAlertResponse;
import com.dentalcore.patients.internal.dto.PatientResponse;
import com.dentalcore.patients.internal.dto.PatientSummaryResponse;
import com.dentalcore.patients.internal.dto.PhoneDto;
import com.dentalcore.patients.internal.entity.MedicalAlert;
import com.dentalcore.patients.internal.entity.Patient;
import com.dentalcore.patients.internal.entity.PatientPhone;
import com.dentalcore.providers.api.ProviderApi;
import com.dentalcore.providers.api.ProviderSummary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PatientMapper {

    private final ProviderApi providerApi;

    public PatientMapper(ProviderApi providerApi) {
        this.providerApi = providerApi;
    }

    public PatientResponse toResponse(Patient patient) {
        ProviderSummary primaryProvider = patient.getPrimaryProviderId() == null
                ? null
                : providerApi.findSummary(patient.getPrimaryProviderId()).orElse(null);
        return new PatientResponse(
                patient.getId(),
                patient.getFirstName(),
                patient.getMiddleName(),
                patient.getLastName(),
                patient.getDateOfBirth(),
                patient.getSex().name(),
                patient.getEmail(),
                patient.getAddressLine1(),
                patient.getAddressLine2(),
                patient.getCity(),
                patient.getState(),
                patient.getPostalCode(),
                patient.getPreferredLanguage(),
                patient.getStatus().name(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactPhone(),
                patient.getEmergencyContactRelationship(),
                patient.getNotes(),
                toPhoneDtos(patient.getPhones()),
                patient.getPreferredName(),
                patient.getPronouns(),
                patient.getEmployer(),
                patient.getOccupation(),
                patient.getReferralSource(),
                patient.getPreferredContactMethod() == null
                        ? null : patient.getPreferredContactMethod().name(),
                patient.isSmsConsent(),
                patient.isEmailConsent(),
                patient.getPharmacyName(),
                patient.getPharmacyPhone(),
                patient.getPrimaryProviderId(),
                primaryProvider != null ? primaryProvider.firstName() : null,
                primaryProvider != null ? primaryProvider.lastName() : null,
                patient.getSmokingStatus().name(),
                patient.getRecallIntervalMonths(),
                patient.getNextRecallDate(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }

    public PatientSummaryResponse toSummaryResponse(Patient patient) {
        return new PatientSummaryResponse(
                patient.getId(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getDateOfBirth(),
                patient.getStatus().name(),
                patient.getPhones().stream()
                        .sorted(Comparator.comparing(PatientPhone::isPrimary).reversed())
                        .map(PatientPhone::getNumber)
                        .findFirst()
                        .orElse(null),
                patient.getEmail(),
                patient.getNextRecallDate()
        );
    }

    public PatientSummary toApiSummary(Patient patient) {
        return new PatientSummary(
                patient.getId(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getDateOfBirth(),
                patient.getStatus().name()
        );
    }

    public MedicalAlertResponse toAlertResponse(MedicalAlert alert) {
        return new MedicalAlertResponse(
                alert.getId(),
                alert.getType().name(),
                alert.getDescription(),
                alert.getSeverity().name(),
                alert.isActive(),
                alert.getCreatedAt(),
                alert.getUpdatedAt()
        );
    }

    public List<PhoneDto> toPhoneDtos(List<PatientPhone> phones) {
        return phones.stream()
                .map(p -> new PhoneDto(p.getType().name(), p.getNumber(), p.isPrimary()))
                .toList();
    }

    public List<PatientPhone> toPhoneEntities(List<PhoneDto> dtos) {
        return dtos.stream()
                .map(dto -> new PatientPhone(
                        PatientPhone.PhoneType.valueOf(dto.type()), dto.number(), dto.primary()))
                .toList();
    }
}
