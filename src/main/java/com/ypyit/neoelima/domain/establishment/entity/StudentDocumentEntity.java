package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.StudentDocumentKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.util.Objects;

/** Pièce du dossier d'un élève ; le fichier lui-même vit dans le stockage privé. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "student_document")
public class StudentDocumentEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @ToString.Exclude
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    private StudentEntity student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StudentDocumentKind kind;

    /** Précision libre, obligatoire pour une pièce « Autre » : sans elle on ne sait pas ce que c'est. */
    @Column(length = 128)
    private String label;

    /** Nom du fichier tel que l'école l'a envoyé, rendu au téléchargement. */
    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 128)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, unique = true)
    private String objectKey;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        StudentDocumentEntity that = (StudentDocumentEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
