package com.dentalcore.insurance.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(name = "insurance_carriers")
@SQLDelete(sql = "UPDATE insurance_carriers SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class InsuranceCarrier extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "payer_id", length = 20)
    private String payerId;

    @Column(name = "phone", length = 30)
    private String phone;

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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected InsuranceCarrier() {
    }

    public InsuranceCarrier(String name) {
        this.name = name;
    }

    public void update(String name, String payerId, String phone, String addressLine1,
                       String addressLine2, String city, String state, String postalCode) {
        this.name = name;
        this.payerId = payerId;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
    }

    public String getName() {
        return name;
    }

    public String getPayerId() {
        return payerId;
    }

    public String getPhone() {
        return phone;
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
}
