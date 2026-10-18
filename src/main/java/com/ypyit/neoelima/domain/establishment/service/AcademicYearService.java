package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.AcademicYearDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicPeriodEntity;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolYearEntity;
import com.ypyit.neoelima.domain.establishment.form.AcademicYearForm;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentEnrollmentRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Années scolaires.
 *
 * <p>La plateforme les définit, pour toutes les écoles : libellé, bornes, année en cours. Chaque
 * école y pose ses trimestres, et chaque utilisateur choisit l'année qu'il consulte.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final UserRepository userRepository;
    private final AcademicYearContext academicYearContext;
    private final CurrentUserProvider currentUserProvider;
    private final jakarta.persistence.EntityManager entityManager;

    /**
     * Les années de la plateforme, la plus récente en tête. Pour un compte d'école, chacune porte
     * les trimestres et l'état de l'école ; et celle que l'utilisateur consulte est signalée.
     */
    public List<AcademicYearDto> findAll() {
        List<AcademicYearEntity> years = this.academicYearRepository.findAllByOrderByStartDateDesc();
        UUID scope = this.currentUserProvider.isPlatformAdmin() ? null
                : this.currentUserProvider.resolveEstablishmentScope(null);
        Map<UUID, SchoolYearEntity> schoolYears = Objects.isNull(scope) ? Map.of()
                : this.schoolYearRepository.findByEstablishment_Id(scope).stream()
                        .collect(Collectors.toMap(sy -> sy.getAcademicYear().getId(), Function.identity()));
        UUID consulted = this.academicYearContext.consultedId();
        return years.stream()
                .map(year -> toDto(year, schoolYears.get(year.getId()), year.getId().equals(consulted)))
                .toList();
    }

    /** L'année en cours, avec les trimestres de l'école : le calendrier en tire ses repères. */
    public Optional<SchoolYearEntity> consultedSchoolYear(UUID establishmentId) {
        return this.academicYearContext.consulted()
                .flatMap(year -> this.schoolYearRepository
                        .findByEstablishment_IdAndAcademicYear_Id(establishmentId, year.getId()));
    }

    /* ---------------------------------------------------------------- Plateforme ---- */

    @Transactional
    public AcademicYearDto create(AcademicYearForm form) {
        this.assertPlatformAdmin();
        if (this.academicYearRepository.existsByLabelIgnoreCase(form.getLabel().trim())) {
            throw new BadRequestException(String.format("L'année %s existe déjà.", form.getLabel().trim()));
        }
        AcademicYearEntity entity = AcademicYearEntity.builder().build();
        this.apply(entity, form);
        AcademicYearEntity saved = this.academicYearRepository.saveAndFlush(entity);
        this.alignStudentsOn(saved);
        log.info("ACADEMIC_YEAR_CREATED: {} ({})", saved.getLabel(), saved.getId());
        return toDto(saved, null, false);
    }

    @Transactional
    public AcademicYearDto update(UUID id, AcademicYearForm form) {
        this.assertPlatformAdmin();
        AcademicYearEntity entity = this.load(id);
        if (this.academicYearRepository.existsByLabelIgnoreCaseAndIdNot(form.getLabel().trim(), id)) {
            throw new BadRequestException(String.format("L'année %s existe déjà.", form.getLabel().trim()));
        }
        if (entity.isActive() && !form.isActive()) {
            throw new BadRequestException(
                    "Il faut toujours une année en cours : désignez-en une autre plutôt que de retirer celle-ci.");
        }
        this.apply(entity, form);
        // Les trimestres des écoles doivent rester dans l'année : resserrer ses bornes ne doit pas
        // laisser un trimestre déborder sans que personne ne le voie.
        for (SchoolYearEntity schoolYear : this.schoolYearsOf(id)) {
            for (AcademicPeriodEntity period : schoolYear.getPeriods()) {
                if (period.getStartDate().isBefore(entity.getStartDate()) || period.getEndDate().isAfter(entity.getEndDate())) {
                    throw new BadRequestException(String.format(
                            "Le « %s » de %s sortirait de ces bornes : l'école doit d'abord l'ajuster.",
                            period.getLabel(), schoolYear.getEstablishment().getName()));
                }
            }
        }
        AcademicYearEntity saved = this.academicYearRepository.saveAndFlush(entity);
        this.alignStudentsOn(saved);
        return toDto(saved, null, false);
    }

    /**
     * L'année devient l'année en cours : la classe actuelle de chaque élève réinscrit devient celle
     * de son inscription pour cette année. Les élèves pas encore réinscrits gardent la leur.
     */
    private void alignStudentsOn(AcademicYearEntity year) {
        if (!year.isActive()) {
            return;
        }
        this.entityManager.createNativeQuery("""
                update student s
                   set school_class_id   = e.school_class_id,
                       level_of_study_id = coalesce(e.level_of_study_id, s.level_of_study_id)
                  from student_enrollment e
                 where e.student_id = s.id
                   and e.academic_year_id = :year
                """).setParameter("year", year.getId()).executeUpdate();
    }

    /** Supprimée seulement tant qu'aucune école ne s'en sert : ses données en dépendraient. */
    @Transactional
    public void delete(UUID id) {
        this.assertPlatformAdmin();
        AcademicYearEntity entity = this.load(id);
        if (entity.isActive()) {
            throw new BadRequestException("L'année en cours ne peut pas être supprimée : désignez-en une autre d'abord.");
        }
        if (this.schoolYearRepository.existsByAcademicYear_Id(id) || this.studentEnrollmentRepository.existsByAcademicYear_Id(id)) {
            throw new BadRequestException(String.format(
                    "Des écoles travaillent sur l'année %s : elle ne peut plus être supprimée.", entity.getLabel()));
        }
        this.academicYearRepository.delete(entity);
        log.info("ACADEMIC_YEAR_DELETED: {}", id);
    }

    /* ---------------------------------------------------------------- École ---- */

    /** Les trimestres de l'école pour une année : ses dates à elle, dans les bornes de l'année. */
    @Transactional
    public AcademicYearDto definePeriods(UUID id, List<AcademicYearForm.AcademicPeriodForm> periods) {
        AcademicYearEntity year = this.load(id);
        UUID scope = this.schoolScope();
        SchoolYearEntity schoolYear = this.academicYearContext.schoolYear(scope, year);
        schoolYear.getPeriods().clear();
        int position = 1;
        for (AcademicYearForm.AcademicPeriodForm period : periods) {
            assertRange(period.getStartDate(), period.getEndDate(), String.format(
                    "La période « %s » doit se terminer après avoir commencé.", period.getLabel()));
            if (period.getStartDate().isBefore(year.getStartDate()) || period.getEndDate().isAfter(year.getEndDate())) {
                throw new BadRequestException(String.format(
                        "La période « %s » sort des bornes de l'année scolaire (%s).", period.getLabel(), year.getLabel()));
            }
            schoolYear.getPeriods().add(AcademicPeriodEntity.builder()
                    .schoolYear(schoolYear)
                    .label(period.getLabel().trim())
                    .startDate(period.getStartDate())
                    .endDate(period.getEndDate())
                    .position(position++)
                    .build());
        }
        SchoolYearEntity saved = this.schoolYearRepository.saveAndFlush(schoolYear);
        return toDto(year, saved, year.getId().equals(this.academicYearContext.consultedId()));
    }

    /** L'année que l'utilisateur consulte ; nul revient à l'année en cours. */
    @Transactional
    public void consult(UUID id) {
        if (Objects.nonNull(id)) {
            this.load(id);
        }
        UserEntity user = this.currentUserProvider.currentUser();
        // L'année en cours n'a pas à être mémorisée : elle suivra d'elle-même le passage à la suivante.
        UUID current = this.academicYearContext.currentId();
        user.setConsultedAcademicYearId(Objects.equals(id, current) ? null : id);
        this.userRepository.save(user);
    }

    /* ---------------------------------------------------------------- Outils ---- */

    private void apply(AcademicYearEntity entity, AcademicYearForm form) {
        assertRange(form.getStartDate(), form.getEndDate(), "L'année scolaire doit se terminer après avoir commencé.");
        if (form.isActive()) {
            // Une seule année en cours, et la base le garantit par un index unique : l'ancienne doit
            // être désactivée en base AVANT que la nouvelle ne soit écrite active, sans quoi l'ordre
            // des écritures d'Hibernate peut faire passer la nouvelle d'abord, et l'index refuse.
            UUID keep = Objects.requireNonNullElse(entity.getId(), new UUID(0L, 0L));
            List<AcademicYearEntity> others = this.academicYearRepository.findByActiveTrueAndIdNot(keep);
            others.forEach(other -> other.setActive(false));
            this.academicYearRepository.saveAllAndFlush(others);
        }
        entity.setLabel(form.getLabel().trim());
        entity.setStartDate(form.getStartDate());
        entity.setEndDate(form.getEndDate());
        entity.setActive(form.isActive());
    }

    private List<SchoolYearEntity> schoolYearsOf(UUID yearId) {
        return this.schoolYearRepository.findAll().stream()
                .filter(sy -> sy.getAcademicYear().getId().equals(yearId)).toList();
    }

    private void assertPlatformAdmin() {
        if (!this.currentUserProvider.isPlatformAdmin()) {
            throw new AccessDeniedException("Les années scolaires sont définies par la plateforme.");
        }
    }

    private UUID schoolScope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }

    private static void assertRange(LocalDate from, LocalDate to, String message) {
        if (Objects.isNull(from) || Objects.isNull(to) || !to.isAfter(from)) {
            throw new BadRequestException(message);
        }
    }

    private AcademicYearEntity load(UUID id) {
        return this.academicYearRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Academic year with provided id %s not found", id)));
    }

    static AcademicYearDto toDto(AcademicYearEntity entity, SchoolYearEntity schoolYear, boolean consulted) {
        return AcademicYearDto.builder()
                .id(entity.getId().toString())
                .label(entity.getLabel())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .active(entity.isActive())
                .consulted(consulted)
                .status(Objects.isNull(schoolYear) ? null : schoolYear.getStatus())
                .periods(Objects.isNull(schoolYear) ? List.of() : schoolYear.getPeriods().stream()
                        .map(period -> AcademicYearDto.AcademicPeriodDto.builder()
                                .id(period.getId().toString())
                                .label(period.getLabel())
                                .startDate(period.getStartDate())
                                .endDate(period.getEndDate())
                                .position(period.getPosition())
                                .build())
                        .toList())
                .build();
    }
}
