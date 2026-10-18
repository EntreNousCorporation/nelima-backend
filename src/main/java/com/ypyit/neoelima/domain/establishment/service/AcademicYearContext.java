package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.UnAuthenticatedUserException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolYearEntity;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolYearRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * L'année dont il est question, pour la requête en cours.
 *
 * <p>Deux années à ne pas confondre : l'année <em>en cours</em>, la même pour toute la plateforme,
 * où l'on inscrit et où l'on facture ; l'année <em>consultée</em>, propre à chaque utilisateur,
 * que ses écrans affichent. Elles coïncident tant que l'utilisateur n'a rien choisi d'autre dans
 * Paramètres.
 *
 * <p>Sans année déclarée sur la plateforme, tout reste sans année : les écrans montrent tout, comme
 * avant les années, plutôt que rien.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicYearContext {

    private final AcademicYearRepository academicYearRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CurrentUserProvider currentUserProvider;

    /** L'année en cours de la plateforme. */
    public Optional<AcademicYearEntity> current() {
        return this.academicYearRepository.findByActiveTrue();
    }

    /** L'année que l'utilisateur connecté consulte : son choix, ou l'année en cours. */
    public Optional<AcademicYearEntity> consulted() {
        UUID chosen = this.consultedChoice();
        if (Objects.nonNull(chosen)) {
            Optional<AcademicYearEntity> year = this.academicYearRepository.findById(chosen);
            if (year.isPresent()) {
                return year;
            }
        }
        return this.current();
    }

    /** Le choix de l'utilisateur ; aucun hors requête authentifiée (tâches planifiées). */
    private UUID consultedChoice() {
        try {
            UserEntity user = this.currentUserProvider.currentUser();
            return user.getConsultedAcademicYearId();
        } catch (UnAuthenticatedUserException e) {
            return null;
        }
    }

    /** Identifiant de l'année consultée, nul sans année déclarée : un filtre nul ne filtre pas. */
    public UUID consultedId() {
        return this.consulted().map(AcademicYearEntity::getId).orElse(null);
    }

    /**
     * Cette donnée appartient-elle à l'année consultée ? Sans année déclarée, tout en fait partie ;
     * une donnée sans année (antérieure aux années de plateforme) n'appartient à aucune.
     */
    public boolean inConsultedYear(AcademicYearEntity year) {
        UUID consulted = this.consultedId();
        return Objects.isNull(consulted) || (Objects.nonNull(year) && consulted.equals(year.getId()));
    }

    /** Identifiant de l'année en cours, nul sans année déclarée. */
    public UUID currentId() {
        return this.current().map(AcademicYearEntity::getId).orElse(null);
    }

    /**
     * L'année d'une école, créée ouverte à la première demande : une école qui entre dans une
     * année n'a rien à déclarer pour y travailler.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public SchoolYearEntity schoolYear(UUID establishmentId, AcademicYearEntity year) {
        return this.schoolYearRepository.findByEstablishment_IdAndAcademicYear_Id(establishmentId, year.getId())
                .orElseGet(() -> {
                    EstablishmentEntity establishment = this.establishmentRepository.getReferenceById(establishmentId);
                    return this.schoolYearRepository.saveAndFlush(SchoolYearEntity.builder()
                            .establishment(establishment).academicYear(year).build());
                });
    }
}
