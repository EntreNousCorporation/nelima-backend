package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.YearPreparationDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.ReenrollmentForm;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.entity.StaffClassYearEntity;
import com.ypyit.neoelima.domain.establishment.repository.StaffClassYearRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La rentrée : ouvrir les classes de l'année, puis réinscrire les élèves de l'année passée.
 *
 * <p>Rien ne se recrée. Les classes de l'an passé se rouvrent d'un geste, titulaire repris ; celles
 * qui ne fonctionnent plus restent fermées. Les élèves se réinscrivent par classe, vers une classe
 * du niveau suivant proposée d'office ; ceux qui partent ne sont simplement pas réinscrits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class YearPreparationService {

    private final AcademicYearRepository academicYearRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SchoolClassYearRepository schoolClassYearRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final StudentFeeAttacher studentFeeAttacher;
    private final AcademicYearContext academicYearContext;
    private final SchoolYearGuard schoolYearGuard;
    private final CurrentUserProvider currentUserProvider;
    private final StaffClassYearRepository staffClassYearRepository;

    public YearPreparationDto preparation(UUID targetYearId) {
        UUID scope = this.scope();
        AcademicYearEntity target = this.load(targetYearId);
        AcademicYearEntity previous = this.previousOf(target).orElse(null);

        List<SchoolClassEntity> classes = this.schoolClassRepository.findByEstablishment_IdOrderByNameAsc(scope);
        Map<UUID, SchoolClassYearEntity> targetOpenings = this.openings(target, scope);
        Map<UUID, SchoolClassYearEntity> previousOpenings = Objects.isNull(previous) ? Map.of() : this.openings(previous, scope);

        List<YearPreparationDto.ClassLine> lines = classes.stream()
                .map(schoolClass -> YearPreparationDto.ClassLine.builder()
                        .classId(schoolClass.getId().toString())
                        .name(schoolClass.getName())
                        .levelCode(Objects.isNull(schoolClass.getLevelOfStudy()) ? null : schoolClass.getLevelOfStudy().getCode())
                        .levelLabel(labelOf(schoolClass.getLevelOfStudy()))
                        .openedInPrevious(previousOpenings.containsKey(schoolClass.getId()))
                        .openedInTarget(targetOpenings.containsKey(schoolClass.getId()))
                        .previousTeacherName(nameOf(Optional.ofNullable(previousOpenings.get(schoolClass.getId()))
                                .map(SchoolClassYearEntity::getMainTeacher).orElse(null)))
                        .targetTeacherName(nameOf(Optional.ofNullable(targetOpenings.get(schoolClass.getId()))
                                .map(SchoolClassYearEntity::getMainTeacher).orElse(null)))
                        .enrolledInTarget(this.studentEnrollmentRepository
                                .countByAcademicYear_IdAndSchoolClass_Id(target.getId(), schoolClass.getId()))
                        .build())
                .toList();

        return YearPreparationDto.builder()
                .targetYearId(target.getId().toString())
                .targetLabel(target.getLabel())
                .previousYearId(Objects.isNull(previous) ? null : previous.getId().toString())
                .previousLabel(Objects.isNull(previous) ? null : previous.getLabel())
                .classes(lines)
                .groups(Objects.isNull(previous) ? List.of() : this.groups(scope, previous, target, classes, targetOpenings))
                .build();
    }

    /** Ouvre ces classes pour l'année, avec le titulaire qu'elles avaient l'an passé. */
    @Transactional
    public void openClasses(UUID targetYearId, List<UUID> classIds) {
        UUID scope = this.scope();
        AcademicYearEntity target = this.load(targetYearId);
        this.schoolYearGuard.assertWritable(scope, target);
        this.academicYearContext.schoolYear(scope, target);
        AcademicYearEntity previous = this.previousOf(target).orElse(null);
        for (UUID classId : classIds) {
            SchoolClassEntity schoolClass = this.loadClass(classId, scope);
            if (this.schoolClassYearRepository.existsBySchoolClass_IdAndAcademicYear_Id(classId, target.getId())) {
                continue;
            }
            StaffEntity teacher = Objects.isNull(previous) ? null : this.schoolClassYearRepository
                    .findBySchoolClass_IdAndAcademicYear_Id(classId, previous.getId())
                    .map(SchoolClassYearEntity::getMainTeacher)
                    .filter(StaffEntity::isActive)
                    .orElse(null);
            this.schoolClassYearRepository.save(SchoolClassYearEntity.builder()
                    .schoolClass(schoolClass).academicYear(target).mainTeacher(teacher).build());
            this.carryTeachers(schoolClass, previous, target, teacher);
        }
        log.info("CLASSES_OPENED: {} class(es) for {} in establishment {}", classIds.size(), target.getLabel(), scope);
    }

    /**
     * Reprend les enseignants de l'an passé dans la classe rouverte, ceux qui sont toujours en
     * poste, et y rattache le titulaire : on ajuste ensuite plutôt que de tout ressaisir.
     */
    private void carryTeachers(SchoolClassEntity schoolClass, AcademicYearEntity previous, AcademicYearEntity target, StaffEntity mainTeacher) {
        java.util.Set<UUID> attached = new java.util.HashSet<>();
        if (Objects.nonNull(previous)) {
            this.staffClassYearRepository.findBySchoolClass_IdAndAcademicYear_Id(schoolClass.getId(), previous.getId()).stream()
                    .map(StaffClassYearEntity::getStaff)
                    .filter(StaffEntity::isActive)
                    .forEach(staff -> {
                        if (attached.add(staff.getId())) {
                            this.attach(staff, schoolClass, target);
                        }
                    });
        }
        if (Objects.nonNull(mainTeacher) && attached.add(mainTeacher.getId())) {
            this.attach(mainTeacher, schoolClass, target);
        }
    }

    private void attach(StaffEntity staff, SchoolClassEntity schoolClass, AcademicYearEntity year) {
        if (!this.staffClassYearRepository.existsByStaff_IdAndSchoolClass_IdAndAcademicYear_Id(staff.getId(), schoolClass.getId(), year.getId())) {
            this.staffClassYearRepository.save(StaffClassYearEntity.builder()
                    .staff(staff).schoolClass(schoolClass).academicYear(year).build());
        }
    }

    /** Ne pas ouvrir cette classe cette année : possible tant que personne n'y est inscrit. */
    @Transactional
    public void closeClass(UUID targetYearId, UUID classId) {
        UUID scope = this.scope();
        AcademicYearEntity target = this.load(targetYearId);
        this.schoolYearGuard.assertWritable(scope, target);
        SchoolClassEntity schoolClass = this.loadClass(classId, scope);
        long enrolled = this.studentEnrollmentRepository.countByAcademicYear_IdAndSchoolClass_Id(target.getId(), classId);
        if (enrolled > 0) {
            throw new BadRequestException(String.format(
                    "%s compte %d élève(s) inscrit(s) en %s : répartissez-les avant de la fermer.",
                    schoolClass.getName(), enrolled, target.getLabel()));
        }
        this.schoolClassYearRepository.findBySchoolClass_IdAndAcademicYear_Id(classId, target.getId())
                .ifPresent(this.schoolClassYearRepository::delete);
    }

    /**
     * Réinscrit ces élèves pour l'année, chacun dans sa classe de rentrée, avec les frais de son
     * nouveau niveau.
     *
     * <p>Se fait depuis l'année visée : c'est elle dont on rattache les frais. Un élève déjà
     * réinscrit est laissé tel quel — réinscrire deux fois le facturerait deux fois.
     */
    @Transactional
    public int reenroll(UUID targetYearId, ReenrollmentForm form) {
        UUID scope = this.scope();
        AcademicYearEntity target = this.load(targetYearId);
        if (!target.getId().equals(this.academicYearContext.consultedId())) {
            throw new BadRequestException(String.format(
                    "Passez sur l'année %s dans Paramètres pour y réinscrire les élèves.", target.getLabel()));
        }
        this.schoolYearGuard.assertWritable(scope, target);
        this.academicYearContext.schoolYear(scope, target);
        AcademicYearEntity previous = this.previousOf(target).orElse(null);
        boolean isCurrent = target.isActive();

        int done = 0;
        for (ReenrollmentForm.Item item : form.getItems()) {
            StudentEntity student = this.studentRepository.findById(item.getStudentId())
                    .orElseThrow(() -> new NotFoundException(String.format("Student with provided id %s not found", item.getStudentId())));
            this.currentUserProvider.assertCanAccessStudent(student);
            if (this.studentEnrollmentRepository.findByStudent_IdAndAcademicYear_Id(student.getId(), target.getId()).isPresent()) {
                continue;
            }
            SchoolClassEntity schoolClass = null;
            if (Objects.nonNull(item.getSchoolClassId())) {
                schoolClass = this.loadClass(item.getSchoolClassId(), scope);
                if (!this.schoolClassYearRepository.existsBySchoolClass_IdAndAcademicYear_Id(schoolClass.getId(), target.getId())) {
                    throw new BadRequestException(String.format(
                            "%s n'est pas ouverte en %s : ouvrez-la d'abord.", schoolClass.getName(), target.getLabel()));
                }
            }
            // Le niveau de la classe d'arrivée ; à défaut, celui de l'an passé (réinscrit sans classe).
            LevelOfStudyEntity level = Objects.nonNull(schoolClass) && Objects.nonNull(schoolClass.getLevelOfStudy())
                    ? schoolClass.getLevelOfStudy()
                    : Optional.ofNullable(previous)
                            .flatMap(year -> this.studentEnrollmentRepository.findByStudent_IdAndAcademicYear_Id(student.getId(), year.getId()))
                            .map(StudentEnrollmentEntity::getLevelOfStudy)
                            .orElse(student.getLevelOfStudy());

            this.studentEnrollmentRepository.save(StudentEnrollmentEntity.builder()
                    .student(student).academicYear(target).schoolClass(schoolClass).levelOfStudy(level).build());
            this.studentFeeAttacher.attachFees(student, scope, Objects.isNull(level) ? null : level.getCode());
            if (isCurrent) {
                // Réinscrit dans l'année en cours : sa classe actuelle est celle-ci.
                student.setSchoolClass(schoolClass);
                student.setLevelOfStudy(level);
                this.studentRepository.save(student);
            }
            done++;
        }
        log.info("STUDENTS_REENROLLED: {} for {} in establishment {}", done, target.getLabel(), scope);
        return done;
    }

    private List<YearPreparationDto.Group> groups(UUID scope, AcademicYearEntity previous, AcademicYearEntity target,
                                                  List<SchoolClassEntity> classes,
                                                  Map<UUID, SchoolClassYearEntity> targetOpenings) {
        Set<UUID> alreadyIn = this.studentEnrollmentRepository
                .findByAcademicYear_IdAndStudent_Establishment_Id(target.getId(), scope).stream()
                .map(enrollment -> enrollment.getStudent().getId())
                .collect(Collectors.toSet());
        List<StudentEnrollmentEntity> previousEnrollments = this.studentEnrollmentRepository
                .findByAcademicYear_IdAndStudent_Establishment_Id(previous.getId(), scope).stream()
                .filter(enrollment -> !alreadyIn.contains(enrollment.getStudent().getId()))
                .toList();

        Map<String, LevelOfStudyEntity> levelsByCode = classes.stream()
                .map(SchoolClassEntity::getLevelOfStudy).filter(Objects::nonNull)
                .collect(Collectors.toMap(LevelOfStudyEntity::getCode, Function.identity(), (a, b) -> a));
        List<SchoolClassEntity> openTargets = classes.stream()
                .filter(schoolClass -> targetOpenings.containsKey(schoolClass.getId())).toList();

        Map<UUID, List<StudentEnrollmentEntity>> bySource = new LinkedHashMap<>();
        previousEnrollments.stream()
                .sorted(Comparator.comparing((StudentEnrollmentEntity e) -> Objects.isNull(e.getSchoolClass()) ? "~" : e.getSchoolClass().getName())
                        .thenComparing(e -> e.getStudent().getLastName()))
                .forEach(e -> bySource.computeIfAbsent(Objects.isNull(e.getSchoolClass()) ? null : e.getSchoolClass().getId(),
                        k -> new java.util.ArrayList<>()).add(e));

        return bySource.entrySet().stream().map(entry -> {
            StudentEnrollmentEntity first = entry.getValue().getFirst();
            SchoolClassEntity source = first.getSchoolClass();
            LevelOfStudyEntity level = Objects.nonNull(source) ? source.getLevelOfStudy() : first.getLevelOfStudy();
            return YearPreparationDto.Group.builder()
                    .sourceClassId(Objects.isNull(source) ? null : source.getId().toString())
                    .sourceClassName(Objects.isNull(source) ? "Sans classe" : source.getName())
                    .levelLabel(labelOf(level))
                    .suggestedTargetClassId(this.suggest(level, openTargets, levelsByCode))
                    .students(entry.getValue().stream().map(e -> YearPreparationDto.StudentLine.builder()
                            .studentId(e.getStudent().getId().toString())
                            .name(String.format("%s %s", e.getStudent().getLastName(), e.getStudent().getFirstName()))
                            .registrationNumber(e.getStudent().getRegistrationNumber())
                            .build()).toList())
                    .build();
        }).toList();
    }

    /** Une classe ouverte au niveau suivant ; à défaut, rien — l'école choisira. */
    private String suggest(LevelOfStudyEntity level, List<SchoolClassEntity> openTargets,
                           Map<String, LevelOfStudyEntity> levelsByCode) {
        if (Objects.isNull(level) || StringUtils.isBlank(level.getNext())) {
            return null;
        }
        return openTargets.stream()
                .filter(schoolClass -> Objects.nonNull(schoolClass.getLevelOfStudy())
                        && level.getNext().equals(schoolClass.getLevelOfStudy().getCode()))
                .map(schoolClass -> schoolClass.getId().toString())
                .findFirst().orElse(null);
    }

    private Map<UUID, SchoolClassYearEntity> openings(AcademicYearEntity year, UUID scope) {
        return this.schoolClassYearRepository.findByAcademicYear_IdAndSchoolClass_Establishment_Id(year.getId(), scope)
                .stream().collect(Collectors.toMap(opening -> opening.getSchoolClass().getId(), Function.identity()));
    }

    private Optional<AcademicYearEntity> previousOf(AcademicYearEntity year) {
        return this.academicYearRepository.findAllByOrderByStartDateDesc().stream()
                .filter(candidate -> candidate.getStartDate().isBefore(year.getStartDate()))
                .findFirst();
    }

    private SchoolClassEntity loadClass(UUID classId, UUID scope) {
        return this.schoolClassRepository.findById(classId)
                .filter(found -> found.getEstablishment().getId().equals(scope))
                .orElseThrow(() -> new NotFoundException(String.format("Class with provided id %s not found", classId)));
    }

    private AcademicYearEntity load(UUID id) {
        return this.academicYearRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Academic year with provided id %s not found", id)));
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }

    private static String nameOf(StaffEntity staff) {
        return Objects.isNull(staff) ? null : String.format("%s %s", staff.getLastName(), staff.getFirstName());
    }

    private static String labelOf(LevelOfStudyEntity level) {
        if (Objects.isNull(level)) {
            return null;
        }
        return Objects.nonNull(level.getName()) && StringUtils.isNotBlank(level.getName().getFr())
                ? level.getName().getFr() : level.getCode();
    }
}
