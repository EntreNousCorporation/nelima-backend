package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.ActivityDto;
import com.ypyit.neoelima.domain.establishment.dto.ActivityEnrollmentDto;
import com.ypyit.neoelima.domain.establishment.entity.ActivityEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.ActivityEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.ActivityStatus;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentSource;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.ActivityEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.ActivityRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Inscriptions aux activités extra-scolaires.
 *
 * <p>Deux règles portent tout le reste. On ne facture pas une place qu'on n'a pas : une inscription
 * en liste d'attente ne produit aucune dette, celle-ci naît à la bascule. Et on n'annule pas ce qui
 * a été encaissé : les remboursements sont hors V1, une annulation laisserait un paiement sans
 * contrepartie et personne pour le rendre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityEnrollmentService {

    private final ActivityRepository activityRepository;
    private final ActivityEnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final InstallmentRepository installmentRepository;
    private final InstallmentGenerator installmentGenerator;
    private final ActivityService activityService;
    private final CurrentUserProvider currentUserProvider;

    /** Inscriptions de l'établissement, la plus récente en tête. */
    public List<ActivityEnrollmentDto> findAll() {
        return this.enrollmentRepository
                .findByActivity_Establishment_IdOrderByRequestedAtDesc(this.activityService.scope())
                .stream().map(this::toDto).toList();
    }

    /**
     * Activités auxquelles cet élève peut prétendre.
     *
     * <p>Sert l'application parent : la liste ne montre que ce qui est ouvert à sa classe, et
     * exclut les brouillons comme les activités suspendues.
     */
    public List<ActivityDto> openTo(UUID studentId) {
        StudentEntity student = this.loadStudent(studentId);
        return this.activityRepository
                .findByEstablishment_IdOrderByNameAsc(student.getEstablishment().getId()).stream()
                .filter(activity -> ActivityStatus.ACTIVE.equals(activity.getStatus()))
                .filter(activity -> isEligible(activity, student))
                .map(this.activityService::toDto)
                .toList();
    }

    @Transactional
    public List<ActivityEnrollmentDto> enroll(UUID activityId, List<UUID> studentIds,
                                              EnrollmentSource source) {
        ActivityEntity activity = this.activityService.load(activityId);
        return studentIds.stream()
                .map(studentId -> this.toDto(this.enrollOne(activity, this.loadStudent(studentId), source)))
                .toList();
    }

    @Transactional
    public ActivityEnrollmentDto enrollMyChild(UUID activityId, UUID studentId) {
        ActivityEntity activity = this.activityService.load(activityId);
        StudentEntity student = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Student with provided id %s not found", studentId)));
        // Un parent n'a pas de périmètre établissement : son accès se vérifie enfant par enfant.
        this.currentUserProvider.assertCanAccessStudent(student);
        return this.toDto(this.enrollOne(activity, student, EnrollmentSource.PARENT));
    }

    @Transactional
    public void cancel(UUID activityId, UUID studentId) {
        ActivityEntity activity = this.activityService.load(activityId);
        this.cancelOne(activity, studentId);
    }

    @Transactional
    public void cancelMyChild(UUID activityId, UUID studentId) {
        ActivityEntity activity = this.activityService.load(activityId);
        StudentEntity student = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Student with provided id %s not found", studentId)));
        this.currentUserProvider.assertCanAccessStudent(student);
        this.cancelOne(activity, studentId);
    }

    private ActivityEnrollmentEntity enrollOne(ActivityEntity activity, StudentEntity student,
                                               EnrollmentSource source) {
        if (!ActivityStatus.ACTIVE.equals(activity.getStatus())) {
            throw new BadRequestException(String.format(
                    "L'activité %s n'accepte pas d'inscription.", activity.getName()));
        }
        if (!student.getEstablishment().getId().equals(activity.getEstablishment().getId())) {
            // L'identifiant d'élève vient du client. Sans ce contrôle, on inscrirait l'élève d'une
            // autre école — et on lui créerait une dette.
            throw new NotFoundException(String.format(
                    "Student with provided id %s not found", student.getId()));
        }
        if (!isEligible(activity, student)) {
            throw new BadRequestException(String.format(
                    "%s n'est pas dans une classe conviée à cette activité.", student.getLastName()));
        }

        ActivityEnrollmentEntity enrollment = this.enrollmentRepository
                .findByActivity_IdAndStudent_Id(activity.getId(), student.getId())
                .orElseGet(() -> ActivityEnrollmentEntity.builder()
                        .activity(activity).student(student).build());

        if (EnrollmentStatus.ENROLLED.equals(enrollment.getStatus())
                || EnrollmentStatus.WAITLISTED.equals(enrollment.getStatus())) {
            throw new BadRequestException(String.format(
                    "%s est déjà inscrit à cette activité.", student.getLastName()));
        }

        enrollment.setSource(source);
        enrollment.setRequestedAt(Instant.now());

        long enrolled = this.enrollmentRepository
                .countByActivity_IdAndStatus(activity.getId(), EnrollmentStatus.ENROLLED);
        if (enrolled < activity.getCapacity()) {
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setStudentFee(this.bill(activity, student));
        } else {
            enrollment.setStatus(EnrollmentStatus.WAITLISTED);
            enrollment.setStudentFee(null);
        }

        ActivityEnrollmentEntity saved = this.enrollmentRepository.saveAndFlush(enrollment);
        log.info("ACTIVITY_ENROLLMENT_{}: activity {} student {} source {}",
                saved.getStatus(), activity.getId(), student.getId(), source);
        return saved;
    }

    private void cancelOne(ActivityEntity activity, UUID studentId) {
        ActivityEnrollmentEntity enrollment = this.enrollmentRepository
                .findByActivity_IdAndStudent_Id(activity.getId(), studentId)
                .orElseThrow(() -> new NotFoundException("Cet élève n'est pas inscrit à cette activité."));
        if (EnrollmentStatus.CANCELLED.equals(enrollment.getStatus())) {
            throw new BadRequestException("Cette inscription est déjà annulée.");
        }

        boolean wasEnrolled = EnrollmentStatus.ENROLLED.equals(enrollment.getStatus());
        this.releaseDebt(enrollment);
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        this.enrollmentRepository.saveAndFlush(enrollment);
        log.info("ACTIVITY_ENROLLMENT_CANCELLED: activity {} student {}", activity.getId(), studentId);

        // Une place ne se libère que si elle était occupée : annuler une demande en attente ne
        // fait avancer personne.
        if (wasEnrolled) {
            this.promoteFromWaitlist(activity);
        }
    }

    /**
     * Rend la place et efface la dette qu'elle avait produite.
     *
     * <p>Refusé dès qu'une tranche a été réglée : les remboursements sont hors V1, et effacer une
     * dette partiellement encaissée laisserait un paiement sans contrepartie.
     */
    private void releaseDebt(ActivityEnrollmentEntity enrollment) {
        StudentFeeEntity studentFee = enrollment.getStudentFee();
        if (Objects.isNull(studentFee)) {
            return;
        }
        if (this.installmentRepository.existsByStudentFee_IdAndStatus(
                studentFee.getId(), InstallmentStatus.PAID)) {
            throw new BadRequestException(
                    "Cette inscription a déjà donné lieu à un encaissement : elle ne peut plus être "
                            + "annulée. Le remboursement se traite hors de la plateforme.");
        }
        this.installmentRepository.deleteAll(
                this.installmentRepository.findByStudentFee_Id(studentFee.getId()));
        enrollment.setStudentFee(null);
        this.enrollmentRepository.saveAndFlush(enrollment);
        this.studentFeeRepository.delete(studentFee);
    }

    /** La première place libérée revient au premier demandeur, et sa dette naît à cet instant. */
    private void promoteFromWaitlist(ActivityEntity activity) {
        long enrolled = this.enrollmentRepository
                .countByActivity_IdAndStatus(activity.getId(), EnrollmentStatus.ENROLLED);
        if (enrolled >= activity.getCapacity()) {
            return;
        }
        Optional<ActivityEnrollmentEntity> next = this.enrollmentRepository
                .findByActivity_IdAndStatusOrderByRequestedAtAsc(
                        activity.getId(), EnrollmentStatus.WAITLISTED).stream().findFirst();
        if (next.isEmpty()) {
            return;
        }
        ActivityEnrollmentEntity promoted = next.get();
        promoted.setStatus(EnrollmentStatus.ENROLLED);
        promoted.setStudentFee(this.bill(activity, promoted.getStudent()));
        this.enrollmentRepository.saveAndFlush(promoted);
        log.info("ACTIVITY_ENROLLMENT_PROMOTED: activity {} student {}",
                activity.getId(), promoted.getStudent().getId());
    }

    /**
     * Crée la dette de l'élève pour cette activité, et ses tranches.
     *
     * <p>Réutilise le générateur des frais de scolarité : avec un échéancier défini sur le frais,
     * l'activité s'étale comme le reste ; sans échéancier, elle est due en une fois.
     */
    private StudentFeeEntity bill(ActivityEntity activity, StudentEntity student) {
        if (Objects.isNull(activity.getFee())) {
            return null;
        }
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student)
                .fee(activity.getFee())
                .name(activity.getName())
                .build());
        List<InstallmentEntity> generated = this.installmentGenerator.generateFor(studentFee);
        log.debug("Generated {} installments for activity {} and student {}",
                generated.size(), activity.getId(), student.getId());
        return studentFee;
    }

    /** Ouverte à tous, ou la classe de l'élève figure parmi celles qui sont conviées. */
    private static boolean isEligible(ActivityEntity activity, StudentEntity student) {
        if (activity.isOpenToAll()) {
            return true;
        }
        SchoolClassEntity schoolClass = student.getSchoolClass();
        if (Objects.isNull(schoolClass)) {
            // Un élève sans classe ne peut être convié que par une activité ouverte à tous : il
            // n'appartient à aucun des ensembles que l'école a désignés.
            return false;
        }
        return activity.getEligibleClasses().stream()
                .anyMatch(eligible -> eligible.getId().equals(schoolClass.getId()));
    }

    private StudentEntity loadStudent(UUID studentId) {
        StudentEntity student = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Student with provided id %s not found", studentId)));
        this.currentUserProvider.assertCanAccessStudent(student);
        return student;
    }

    private ActivityEnrollmentDto toDto(ActivityEnrollmentEntity entity) {
        StudentEntity student = entity.getStudent();
        StudentFeeEntity studentFee = entity.getStudentFee();
        SchoolClassEntity schoolClass = student.getSchoolClass();

        BigDecimal due = null;
        boolean partiallyPaid = false;
        if (Objects.nonNull(studentFee)) {
            List<InstallmentEntity> installments = this.installmentRepository
                    .findByStudentFee_Id(studentFee.getId());
            due = installments.stream()
                    .filter(installment -> !InstallmentStatus.CANCELLED.equals(installment.getStatus()))
                    .map(InstallmentEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            partiallyPaid = installments.stream()
                    .anyMatch(installment -> InstallmentStatus.PAID.equals(installment.getStatus()));
        }

        return ActivityEnrollmentDto.builder()
                .id(entity.getId().toString())
                .status(entity.getStatus())
                .source(entity.getSource())
                .requestedAt(entity.getRequestedAt())
                .activityId(entity.getActivity().getId().toString())
                .activityName(entity.getActivity().getName())
                .studentId(student.getId().toString())
                .studentFirstName(student.getFirstName())
                .studentLastName(student.getLastName())
                .studentRegistrationNumber(student.getRegistrationNumber())
                .className(Objects.isNull(schoolClass) ? null : schoolClass.getName())
                .amountDue(due)
                .partiallyPaid(partiallyPaid)
                .build();
    }
}
