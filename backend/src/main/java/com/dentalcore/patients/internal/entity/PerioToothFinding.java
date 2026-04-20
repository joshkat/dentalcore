package com.dentalcore.patients.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Per-tooth findings that aren't site-specific: mobility (0-3), furcation (0-4). */
@Entity
@Table(name = "perio_tooth_findings")
public class PerioToothFinding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private PerioExam exam;

    @Column(name = "tooth", nullable = false, length = 2)
    private String tooth;

    @Column(name = "mobility")
    private Integer mobility;

    @Column(name = "furcation")
    private Integer furcation;

    protected PerioToothFinding() {
    }

    PerioToothFinding(PerioExam exam, String tooth, Integer mobility, Integer furcation) {
        this.exam = exam;
        this.tooth = tooth;
        update(mobility, furcation);
    }

    void update(Integer mobility, Integer furcation) {
        this.mobility = mobility;
        this.furcation = furcation;
    }

    public String getTooth() {
        return tooth;
    }

    public Integer getMobility() {
        return mobility;
    }

    public Integer getFurcation() {
        return furcation;
    }
}
