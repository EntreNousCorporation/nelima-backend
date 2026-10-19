package com.ypyit.neoelima.domain.canteen.entity;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import java.time.LocalDate;
import com.ypyit.neoelima.common.entity.BaseEntity;
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

/** Le menu d'un jour ; publié, les familles le voient dans l'application. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "canteen_menu_day")
public class CanteenMenuDayEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @Column(nullable = false)
    private LocalDate day;

    @Column(length = 128)
    private String starter;

    @Column(length = 128)
    private String mainCourse;

    @Column(length = 128)
    private String dessert;

    @Column(length = 128)
    private String vegetarianAlternative;

    /** Allergènes présents, séparés par des virgules. */
    @Column(length = 160)
    private String allergens;

    @Column(nullable = false)
    private boolean published;

    /** Pas de service ce jour-là (férié, sortie) : aucun abonné n'est attendu. */
    @Column(nullable = false)
    private boolean closed;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CanteenMenuDayEntity that = (CanteenMenuDayEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
