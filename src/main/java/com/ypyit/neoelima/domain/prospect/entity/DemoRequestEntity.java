package com.ypyit.neoelima.domain.prospect.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Demande de démonstration déposée depuis le site public.
 *
 * <p>Elle est enregistrée plutôt qu'envoyée par courriel : une boîte mail ne dit pas ce qui a déjà
 * été rappelé, et une demande y disparaît sous le reste du courrier. Le courriel de notification
 * reste, mais comme alerte, pas comme registre.
 *
 * <p>Rien ici n'est une donnée d'élève ni un compte : c'est une école qui se signale.
 */
@Entity
@Table(name = "demo_request")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DemoRequestEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "school_name", nullable = false, length = 160)
    private String schoolName;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 120)
    private String city;

    /** Effectif déclaré. Approximatif par nature : il prépare l'échange, il ne facture rien. */
    @Column(name = "student_count")
    private Integer studentCount;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private DemoRequestStatus status = DemoRequestStatus.PENDING;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "handled_note", length = 500)
    private String handledNote;

    /** Page d'où part la demande. Dit quelle page convertit, sans traceur ni cookie. */
    @Column(name = "source_page", length = 120)
    private String sourcePage;
}
