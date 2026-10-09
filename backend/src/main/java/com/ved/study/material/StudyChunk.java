package com.ved.study.material;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A ~3.5k-character slice of the document's text; the unit lessons and questions are grounded in. */
@Entity
@Table(name = "study_chunks")
public class StudyChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String heading;

    @Column(nullable = false)
    private String content;

    @Column(name = "page_from", nullable = false)
    private int pageFrom;

    @Column(name = "page_to", nullable = false)
    private int pageTo;

    protected StudyChunk() {
        // for JPA
    }

    public StudyChunk(UUID materialId, int position, String heading, String content, int pageFrom, int pageTo) {
        this.materialId = materialId;
        this.position = position;
        this.heading = heading;
        this.content = content;
        this.pageFrom = pageFrom;
        this.pageTo = pageTo;
    }

    public int getPosition() {
        return position;
    }

    public String getHeading() {
        return heading;
    }

    public String getContent() {
        return content;
    }
}
