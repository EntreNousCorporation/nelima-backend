package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.StudentClaimForm;
import com.ypyit.neoelima.domain.establishment.mapper.StudentMapper;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Rattache un enfant au compte d'un parent.
 *
 * <p>C'est la porte d'entrée du parcours parent : sans rattachement, l'application mobile n'a
 * aucun élève à afficher, et surtout personne ne reçoit les reçus ni les rappels d'échéance.
 *
 * <p>La preuve exigée est le triplet établissement + matricule + date de naissance, revérifié ici.
 * Accepter un simple identifiant d'élève laisserait n'importe quel compte se rattacher à n'importe
 * quel enfant en devinant ou en interceptant un UUID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentClaimService {

    private final StudentRepository studentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final StudentMapper studentMapper;

    @Transactional
    public StudentEntity claim(StudentClaimForm form) {
        UserEntity claimant = this.currentUserProvider.currentUser();

        // Un compte d'établissement gère ses élèves par le portail ; se rattacher comme tuteur
        // n'aurait pas de sens et le ferait apparaître comme destinataire des notifications.
        if (claimant instanceof EstablishmentUserEntity) {
            throw new AccessDeniedException("Un compte d'établissement ne peut pas se rattacher à un élève");
        }

        Optional<StudentEntity> found = this.studentRepository
                .findByEstablishment_IdAndRegistrationNumber(
                        form.getEstablishmentId(), form.getRegistrationNumber().trim());

        // Message volontairement identique pour un matricule inconnu et une date qui ne
        // correspond pas : distinguer les deux permettrait de confirmer l'existence d'un
        // matricule par tâtonnement.
        StudentEntity student = found
                .filter(candidate -> form.getBirthDay().equals(candidate.getBirthDay()))
                .orElseThrow(() -> {
                    log.warn("STUDENT_CLAIM_REJECTED: échec de rattachement pour le matricule {} "
                            + "dans l'établissement {}", form.getRegistrationNumber(), form.getEstablishmentId());
                    return new NotFoundException(
                            "Aucun élève ne correspond à ce matricule et à cette date de naissance");
                });

        boolean alreadyLinked = student.getParentUsers().stream()
                .anyMatch(parent -> Objects.equals(parent.getId(), claimant.getId()));
        if (alreadyLinked) {
            throw new BadRequestException("Cet élève est déjà rattaché à votre compte");
        }

        student.getParentUsers().add(claimant);
        StudentEntity saved = this.studentRepository.saveAndFlush(student);

        log.info("STUDENT_CLAIMED: élève {} rattaché au compte {}",
                student.getRegistrationNumber(), claimant.getId());
        return saved;
    }

    /** Élèves déjà rattachés au compte appelant. Base de l'écran d'accueil du parent. */
    @Transactional(readOnly = true)
    public java.util.List<StudentEntity> myChildren() {
        UserEntity claimant = this.currentUserProvider.currentUser();
        return this.studentRepository.findByParentUsers_Id(claimant.getId());
    }

    /**
     * Les enfants du parent appelant, prêts pour l'application, coordonnées des co-tuteurs masquées.
     *
     * <p>Le mapping se fait ici, dans la transaction, et non dans le contrôleur : les collections
     * {@code parentUsers} et leurs contacts sont chargées à la demande, session ouverte.
     */
    @Transactional(readOnly = true)
    public List<StudentDto> myChildrenView() {
        return TutorContactPrivacy.hideAll(this.studentMapper.toDtos(this.myChildren()));
    }

    /** Rattache puis rend l'élève prêt pour l'application, coordonnées des co-tuteurs masquées. */
    @Transactional
    public StudentDto claimView(StudentClaimForm form) {
        return TutorContactPrivacy.hide(this.studentMapper.toDto(this.claim(form)));
    }
}
