package com.dentalcore.forms.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "form_templates")
public class FormTemplate extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description")
    private String description;

    /** Ordered field definitions: {key, label, type, required, options?}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> fields;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected FormTemplate() {
    }

    public FormTemplate(UUID clinicId, String name, String description,
                        List<Map<String, Object>> fields) {
        this.clinicId = clinicId;
        this.name = name;
        this.description = description;
        this.fields = fields;
    }

    public void edit(String name, String description, List<Map<String, Object>> fields) {
        this.name = name;
        this.description = description;
        this.fields = fields;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Map<String, Object>> getFields() {
        return fields;
    }

    public boolean isActive() {
        return active;
    }
}
