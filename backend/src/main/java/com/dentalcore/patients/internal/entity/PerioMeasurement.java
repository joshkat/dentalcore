package com.dentalcore.patients.internal.entity;

import com.dentalcore.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One probing site. Sites: 1=DB 2=B 3=MB (facial), 4=DL 5=L 6=ML (lingual). */
@Entity
@Table(name = "perio_measurements")
public class PerioMeasurement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private PerioExam exam;

    @Column(name = "tooth", nullable = false, length = 2)
    private String tooth;

    @Column(name = "site", nullable = false)
    private int site;

    @Column(name = "pocket_depth")
    private Integer pocketDepth;

    @Column(name = "recession")
    private Integer recession;

    @Column(name = "bleeding", nullable = false)
    private boolean bleeding;

    @Column(name = "suppuration", nullable = false)
    private boolean suppuration;

    protected PerioMeasurement() {
    }

    PerioMeasurement(PerioExam exam, String tooth, int site, Integer pocketDepth,
                     Integer recession, boolean bleeding, boolean suppuration) {
        this.exam = exam;
        this.tooth = tooth;
        this.site = site;
        update(pocketDepth, recession, bleeding, suppuration);
    }

    void update(Integer pocketDepth, Integer recession, boolean bleeding, boolean suppuration) {
        this.pocketDepth = pocketDepth;
        this.recession = recession;
        this.bleeding = bleeding;
        this.suppuration = suppuration;
    }

    public String getTooth() {
        return tooth;
    }

    public int getSite() {
        return site;
    }

    public Integer getPocketDepth() {
        return pocketDepth;
    }

    public Integer getRecession() {
        return recession;
    }

    public boolean isBleeding() {
        return bleeding;
    }

    public boolean isSuppuration() {
        return suppuration;
    }
}
