package com.ypyit.neoelima.domain.transport.entity;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Objects;

/** Chauffeur de l'école : son permis et sa validité font son dossier. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transport_driver")
public class TransportDriverEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @Column(nullable = false, length = 64)
    private String firstName;

    @Column(nullable = false, length = 64)
    private String lastName;

    @Column(length = 32)
    private String phone;

    /** D, C ou B. */
    @Column(length = 2)
    private String licenseCategory;

    private LocalDate licenseExpiry;

    /** Pièces fournies, séparées par des virgules : LICENSE, ID, RECORD, MEDICAL. */
    @Column(length = 64)
    private String documents;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TransportDriverEntity that = (TransportDriverEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
