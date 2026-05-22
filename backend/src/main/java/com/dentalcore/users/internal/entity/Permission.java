package com.dentalcore.users.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    protected Permission() {
    }

    public Permission(String code, String description, String category) {
        this.code = code;
        this.description = description;
        this.category = category;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }
}
