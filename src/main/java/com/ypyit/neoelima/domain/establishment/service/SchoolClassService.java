package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.SchoolClassDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.establishment.form.SchoolClassForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.payment.entity.QReceiptEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Classes de l'établissement.
 *
 * <p>La portée vient de l'utilisateur authentifié, comme partout ailleurs : une école ne voit et
 * ne modifie que ses propres classes, et l'identifiant d'établissement ne circule jamais depuis le
 * formulaire client.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolClassService {

    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final LevelOfStudyRepository levelOfStudyRepository;
    private final StaffRepository staffRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final JPAQueryFactory queryFactory;

    public List<SchoolClassDto> findAll() {
        UUID scope = this.scope();
        List<SchoolClassEntity> classes = this.schoolClassRepository
                .findByEstablishment_IdOrderByNameAsc(scope);

        // Trois agrégats groupés plutôt qu'une requête par classe : un tableau de vingt classes
        // aurait sinon coûté soixante allers-retours à l'affichage.
        Map<UUID, Long> headcounts = this.headcounts(scope);
        Map<UUID, BigDecimal> outstanding = this.outstandingByClass(scope);
        Map<UUID, BigDecimal> collected = this.collectedByClass(scope);

        return classes.stream().map(entity -> toDto(entity,
                headcounts.getOrDefault(entity.getId(), 0L),
                outstanding.getOrDefault(entity.getId(), BigDecimal.ZERO),
                collected.getOrDefault(entity.getId(), BigDecimal.ZERO))).toList();
    }

    public SchoolClassDto findById(UUID id) {
        SchoolClassEntity entity = this.load(id);
        return toDto(entity,
                this.headcounts(this.scope()).getOrDefault(id, 0L),
                this.outstandingByClass(this.scope()).getOrDefault(id, BigDecimal.ZERO),
                this.collectedByClass(this.scope()).getOrDefault(id, BigDecimal.ZERO));
    }

    @Transactional
    public SchoolClassDto create(SchoolClassForm form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.findById(scope)
                .orElseThrow(() -> new NotFoundException("Establishment not found"));

        SchoolClassEntity entity = SchoolClassEntity.builder()
                .name(form.getName().trim())
                .room(trimToNull(form.getRoom()))
                .capacity(form.getCapacity())
                .mainTeacher(this.resolveMainTeacher(form.getMainTeacherId(), establishment))
                .mainTeacherName(trimToNull(form.getMainTeacherName()))
                .levelOfStudy(this.resolveLevel(form.getLevelOfStudyCode(), establishment))
                .establishment(establishment)
                .build();

        SchoolClassEntity saved = this.schoolClassRepository.saveAndFlush(entity);
        log.info("SCHOOL_CLASS_CREATED: {} in establishment {}", saved.getName(), scope);
        return toDto(saved, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public SchoolClassDto update(UUID id, SchoolClassForm form) {
        SchoolClassEntity entity = this.load(id);
        entity.setName(form.getName().trim());
        entity.setRoom(trimToNull(form.getRoom()));
        entity.setCapacity(form.getCapacity());
        entity.setMainTeacher(this.resolveMainTeacher(form.getMainTeacherId(), entity.getEstablishment()));
        entity.setMainTeacherName(trimToNull(form.getMainTeacherName()));
        entity.setLevelOfStudy(this.resolveLevel(form.getLevelOfStudyCode(), entity.getEstablishment()));
        this.schoolClassRepository.saveAndFlush(entity);
        return this.findById(id);
    }

    /**
     * Suppression refusée tant que la classe compte des élèves.
     *
     * <p>La détacher en cascade laisserait des élèves sans classe sans que personne ne l'ait
     * demandé, et c'est exactement le genre de perte qu'on ne remarque qu'à la rentrée suivante.
     */
    @Transactional
    public void delete(UUID id) {
        SchoolClassEntity entity = this.load(id);
        long headcount = this.headcounts(this.scope()).getOrDefault(id, 0L);
        if (headcount > 0) {
            throw new BadRequestException(String.format(
                    "La classe %s compte %d élève(s) : retirez-les avant de la supprimer.",
                    entity.getName(), headcount));
        }
        this.schoolClassRepository.delete(entity);
    }

    @Transactional
    public void assign(UUID id, List<UUID> studentIds) {
        SchoolClassEntity entity = this.load(id);
        for (UUID studentId : studentIds) {
            StudentEntity student = this.studentRepository.findById(studentId)
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Student with provided id %s not found", studentId)));
            // Contrôle explicite : un identifiant d'élève arrive du client, et rien n'empêcherait
            // sinon d'affecter à sa classe l'élève d'une autre école.
            this.currentUserProvider.assertCanAccessStudent(student);
            student.setSchoolClass(entity);
            this.studentRepository.save(student);
        }
    }

    @Transactional
    public void unassign(UUID id, UUID studentId) {
        this.load(id);
        StudentEntity student = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Student with provided id %s not found", studentId)));
        this.currentUserProvider.assertCanAccessStudent(student);
        student.setSchoolClass(null);
        this.studentRepository.save(student);
    }

    private SchoolClassEntity load(UUID id) {
        SchoolClassEntity entity = this.schoolClassRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Class with provided id %s not found", id)));
        UUID scope = this.scope();
        if (Objects.nonNull(scope) && !entity.getEstablishment().getId().equals(scope)) {
            throw new NotFoundException(String.format("Class with provided id %s not found", id));
        }
        return entity;
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            // Un administrateur YPYit n'a pas de classes à lui : la console du parc ne les
            // manipule pas, et rendre toutes celles de toutes les écoles n'aurait aucun sens.
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }

    /**
     * Titulaire désigné, s'il appartient bien à l'établissement.
     *
     * <p>L'identifiant arrive du client : sans ce contrôle, une école pourrait désigner titulaire
     * l'enseignant d'une autre, et lire son nom sur sa propre grille de classes.
     */
    private StaffEntity resolveMainTeacher(UUID staffId, EstablishmentEntity establishment) {
        if (Objects.isNull(staffId)) {
            return null;
        }
        StaffEntity member = this.staffRepository.findById(staffId)
                .orElseThrow(() -> new BadRequestException("Ce membre du personnel n'existe pas."));
        if (!member.getEstablishment().getId().equals(establishment.getId())) {
            throw new BadRequestException("Ce membre du personnel n'existe pas.");
        }
        if (!member.isActive()) {
            throw new BadRequestException(String.format(
                    "%s ne fait plus partie du personnel actif.", member.getLastName()));
        }
        // Seul un enseignant tient une classe : un administratif ou un membre de la direction n'a
        // pas vocation à en être titulaire, et le proposer brouillait la liste des candidats.
        if (member.getRole() != StaffRole.TEACHER) {
            throw new BadRequestException(String.format(
                    "%s n'a pas le profil enseignant : seul un enseignant peut être titulaire d'une classe.",
                    member.getLastName()));
        }
        return member;
    }

    private LevelOfStudyEntity resolveLevel(String code, EstablishmentEntity establishment) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        LevelOfStudyEntity level = this.levelOfStudyRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException(
                        String.format("Le niveau %s n'existe pas.", code)));
        // Le niveau doit être déclaré par l'établissement : accepter n'importe quel code du
        // catalogue permettrait de créer une classe de Terminale dans une école maternelle.
        boolean declared = establishment.getLevelOfStudies().stream()
                .anyMatch(declaredLevel -> declaredLevel.getCode().equals(code));
        if (!declared) {
            throw new BadRequestException(String.format(
                    "Le niveau %s n'est pas déclaré par votre établissement.", code));
        }
        return level;
    }

    private Map<UUID, Long> headcounts(UUID scope) {
        QStudentEntity student = QStudentEntity.studentEntity;
        Map<UUID, Long> counts = new HashMap<>();
        this.queryFactory.select(student.schoolClass.id, student.count())
                .from(student)
                .where(student.establishment.id.eq(scope), student.schoolClass.isNotNull())
                .groupBy(student.schoolClass.id)
                .fetch()
                .forEach(row -> counts.put(row.get(student.schoolClass.id),
                        Objects.requireNonNullElse(row.get(student.count()), 0L)));
        return counts;
    }

    private Map<UUID, BigDecimal> outstandingByClass(UUID scope) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        Map<UUID, BigDecimal> amounts = new HashMap<>();
        for (Tuple row : this.queryFactory
                .select(student.schoolClass.id, installment.amount.sum())
                .from(installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(installment.status.eq(InstallmentStatus.PENDING),
                        student.establishment.id.eq(scope), student.schoolClass.isNotNull())
                .groupBy(student.schoolClass.id)
                .fetch()) {
            amounts.put(row.get(student.schoolClass.id),
                    Objects.requireNonNullElse(row.get(installment.amount.sum()), BigDecimal.ZERO));
        }
        return amounts;
    }

    private Map<UUID, BigDecimal> collectedByClass(UUID scope) {
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        Map<UUID, BigDecimal> amounts = new HashMap<>();
        // Le reçu ne porte pas la classe : on la retrouve par l'élève de la tranche réglée, ce qui
        // suit l'élève s'il change de classe en cours d'année — la classe actuelle est bien celle
        // dont l'école veut voir le recouvrement.
        for (Tuple row : this.queryFactory
                .select(student.schoolClass.id, receipt.amount.sum())
                .from(receipt)
                .join(receipt.paymentIntent.installment.studentFee.student, student)
                .where(receipt.establishment.id.eq(scope), student.schoolClass.isNotNull())
                .groupBy(student.schoolClass.id)
                .fetch()) {
            amounts.put(row.get(student.schoolClass.id),
                    Objects.requireNonNullElse(row.get(receipt.amount.sum()), BigDecimal.ZERO));
        }
        return amounts;
    }

    private static SchoolClassDto toDto(SchoolClassEntity entity, long studentCount,
                                        BigDecimal outstanding, BigDecimal collected) {
        LevelOfStudyEntity level = entity.getLevelOfStudy();
        StaffEntity mainTeacher = entity.getMainTeacher();
        return SchoolClassDto.builder()
                .id(entity.getId().toString())
                .name(entity.getName())
                .room(entity.getRoom())
                .capacity(entity.getCapacity())
                .mainTeacherId(Objects.isNull(mainTeacher) ? null : mainTeacher.getId().toString())
                // La référence prime sur le nom hérité : quand les deux existent, c'est qu'une
                // école a désigné son titulaire dans le répertoire après l'avoir tapé à la main.
                .mainTeacherName(Objects.isNull(mainTeacher)
                        ? entity.getMainTeacherName()
                        : String.format("%s %s", mainTeacher.getLastName(), mainTeacher.getFirstName()))
                .levelCode(Objects.isNull(level) ? null : level.getCode())
                .levelLabel(Objects.isNull(level) ? null : labelOf(level))
                .cycle(Objects.isNull(level) ? null : level.getCycle())
                .studentCount(studentCount)
                .outstandingAmount(outstanding)
                .collectedAmount(collected)
                .build();
    }

    /** Libellé français du niveau, avec repli sur le code technique quand il manque. */
    private static String labelOf(LevelOfStudyEntity level) {
        if (Objects.nonNull(level.getName()) && StringUtils.isNotBlank(level.getName().getFr())) {
            return level.getName().getFr();
        }
        return level.getCode();
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
