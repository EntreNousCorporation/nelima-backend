package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.SchoolEventKind;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Événement du calendrier scolaire : conseil de classe, réunion, fermeture, examen.
 *
 * <p>Ne couvre pas les échéances financières. Celles-ci sont déduites des tranches dues et ne se
 * saisissent jamais : les stocker ici créerait une seconde vérité à côté de la dette réelle, et
 * rien ne garantirait qu'elles restent d'accord.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "school_event")
public class SchoolEventEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SchoolEventKind kind;

    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    /** Sans horaire précis : le calendrier n'affiche alors aucune heure. */
    @Builder.Default
    @Column(nullable = false)
    private boolean allDay = false;

    private LocalTime startTime;

    private LocalTime endTime;

    /** Précision libre : « Équipe pédagogique », « Salle polyvalente »… */
    @Column(length = 255)
    private String details;

    /** Concerne tout l'établissement, y compris les classes ouvertes par la suite. */
    @Builder.Default
    @Column(nullable = false)
    private boolean wholeSchool = false;

    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinTable(
            name = "school_event_class",
            joinColumns = @JoinColumn(name = "school_event_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "school_class_id", referencedColumnName = "id")
    )
    private Set<SchoolClassEntity> classes = new HashSet<>();

    /**
     * L'événement paraît dans l'application des familles.
     *
     * <p><strong>N'envoie rien.</strong> Rendre visible n'est pas interrompre : sans cette
     * distinction, corriger une faute de frappe dans un titre notifierait de nouveau toute l'école.
     * L'envoi est un geste explicite, tracé par {@link #lastNotifiedAt}.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean visibleToFamilies = false;

    /** Dernier envoi aux familles ; nul tant qu'aucun n'est parti. */
    private Instant lastNotifiedAt;

    @ManyToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        SchoolEventEntity that = (SchoolEventEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
