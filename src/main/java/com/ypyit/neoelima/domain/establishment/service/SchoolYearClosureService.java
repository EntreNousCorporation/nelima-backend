package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.SchoolYearClosureDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolYearEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.SchoolYearStatus;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolYearRepository;
import com.ypyit.neoelima.domain.payment.entity.QPaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Clôture d'une année, école par école.
 *
 * <p>Une seule condition, et elle est stricte : tout ce que l'année a engagé est encaissé. Pas une
 * tranche en attente, pas un paiement en ligne en cours de confirmation. Clôturer avec un reste dû
 * figerait une dette que plus personne ne pourrait encaisser.
 *
 * <p>L'année en cours ne se clôture pas : l'école y travaille. Elle le pourra une fois la plateforme
 * passée à l'année suivante, quand ses derniers impayés seront soldés.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolYearClosureService {

    private static final int OUTSTANDING_SHOWN = 50;

    private final AcademicYearRepository academicYearRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final AcademicYearContext academicYearContext;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;
    private final JPAQueryFactory queryFactory;
    private final DebtCarryOverService debtCarryOverService;

    public SchoolYearClosureDto preview(UUID academicYearId) {
        UUID scope = this.scope();
        AcademicYearEntity year = this.load(academicYearId);
        SchoolYearEntity schoolYear = this.schoolYearRepository
                .findByEstablishment_IdAndAcademicYear_Id(scope, year.getId()).orElse(null);
        return this.describe(scope, year, schoolYear);
    }

    @Transactional
    public SchoolYearClosureDto close(UUID academicYearId) {
        UUID scope = this.scope();
        AcademicYearEntity year = this.load(academicYearId);
        SchoolYearEntity schoolYear = this.academicYearContext.schoolYear(scope, year);
        SchoolYearClosureDto state = this.describe(scope, year, schoolYear);
        if (!state.isClosable()) {
            throw new BadRequestException(state.getBlocker());
        }
        schoolYear.setStatus(SchoolYearStatus.CLOSED);
        schoolYear.setClosedAt(Instant.now());
        schoolYear.setClosedBy(this.currentUserProvider.currentUser().getUsername());
        this.schoolYearRepository.saveAndFlush(schoolYear);
        // Geste irréversible depuis l'école : il se relit au journal.
        this.auditService.record(scope, AuditAction.YEAR_CLOSED, year.getLabel(), "Année clôturée, tout étant encaissé");
        log.info("SCHOOL_YEAR_CLOSED: {} in establishment {}", year.getLabel(), scope);
        return this.describe(scope, year, schoolYear);
    }

    /**
     * Rouvre l'année d'une école. Réservé à la plateforme : l'école a clôturé en connaissance de
     * cause, et rouvrir ses comptes d'une année se fait sur demande, tracé au journal de l'école.
     */
    @Transactional
    public void reopen(UUID academicYearId, UUID establishmentId) {
        if (!this.currentUserProvider.isPlatformAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Seule la plateforme rouvre une année clôturée.");
        }
        AcademicYearEntity year = this.load(academicYearId);
        SchoolYearEntity schoolYear = this.schoolYearRepository
                .findByEstablishment_IdAndAcademicYear_Id(establishmentId, academicYearId)
                .filter(SchoolYearEntity::isClosed)
                .orElseThrow(() -> new BadRequestException("Cette année n'est pas clôturée dans cette école."));
        schoolYear.setStatus(SchoolYearStatus.OPEN);
        schoolYear.setClosedAt(null);
        schoolYear.setClosedBy(null);
        this.schoolYearRepository.saveAndFlush(schoolYear);
        this.auditService.record(establishmentId, AuditAction.YEAR_REOPENED, year.getLabel(),
                "Année rouverte par la plateforme (" + this.currentUserProvider.currentUser().getUsername() + ")");
        log.info("SCHOOL_YEAR_REOPENED: {} in establishment {}", year.getLabel(), establishmentId);
    }

    /** Les écoles et leur état pour une année : la vue de la plateforme. */
    public List<SchoolYearClosureDto.SchoolState> schools(UUID academicYearId) {
        if (!this.currentUserProvider.isPlatformAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Vue réservée à la plateforme.");
        }
        this.load(academicYearId);
        return this.schoolYearRepository.findAll().stream()
                .filter(sy -> sy.getAcademicYear().getId().equals(academicYearId))
                .map(sy -> SchoolYearClosureDto.SchoolState.builder()
                        .establishmentId(sy.getEstablishment().getId().toString())
                        .name(sy.getEstablishment().getName())
                        .status(sy.getStatus())
                        .closedAt(sy.getClosedAt())
                        .closedBy(sy.getClosedBy())
                        .build())
                .sorted(java.util.Comparator.comparing(SchoolYearClosureDto.SchoolState::getName))
                .toList();
    }

    private SchoolYearClosureDto describe(UUID scope, AcademicYearEntity year, SchoolYearEntity schoolYear) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        List<Tuple> rows = this.queryFactory
                .select(student.id, student.lastName, student.firstName, student.registrationNumber,
                        installment.count(), installment.amount.sum())
                .from(installment)
                .join(installment.studentFee.student, student)
                .where(student.establishment.id.eq(scope),
                        installment.studentFee.fee.academicYear.id.eq(year.getId()),
                        installment.status.eq(InstallmentStatus.PENDING))
                .groupBy(student.id, student.lastName, student.firstName, student.registrationNumber)
                .orderBy(installment.amount.sum().desc())
                .fetch();

        long pendingCount = rows.stream().mapToLong(row -> Objects.requireNonNullElse(row.get(4, Long.class), 0L)).sum();
        BigDecimal pendingAmount = rows.stream()
                .map(row -> Objects.requireNonNullElse(row.get(5, BigDecimal.class), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        QPaymentIntentEntity intent = QPaymentIntentEntity.paymentIntentEntity;
        QInstallmentEntity paid = new QInstallmentEntity("intentInstallment");
        Long inProgress = this.queryFactory.select(intent.count()).from(intent)
                .join(intent.installment, paid)
                .where(paid.studentFee.student.establishment.id.eq(scope),
                        paid.studentFee.fee.academicYear.id.eq(year.getId()),
                        intent.status.eq(PaymentIntentStatus.PENDING))
                .fetchOne();
        long payments = Objects.requireNonNullElse(inProgress, 0L);

        boolean closed = Objects.nonNull(schoolYear) && schoolYear.isClosed();
        String blocker = closed ? "L'année est déjà clôturée."
                : year.isActive() ? "L'année en cours ne se clôture pas : elle le pourra une fois l'année suivante commencée."
                : pendingCount > 0 ? String.format("Il reste %d tranche(s) à encaisser.", pendingCount)
                : payments > 0 ? String.format("%d paiement(s) en ligne attendent leur confirmation.", payments)
                : null;

        return SchoolYearClosureDto.builder()
                .academicYearId(year.getId().toString())
                .label(year.getLabel())
                .status(Objects.isNull(schoolYear) ? SchoolYearStatus.OPEN : schoolYear.getStatus())
                .closedAt(Objects.isNull(schoolYear) ? null : schoolYear.getClosedAt())
                .closedBy(Objects.isNull(schoolYear) ? null : schoolYear.getClosedBy())
                .current(year.isActive())
                .closable(Objects.isNull(blocker))
                .blocker(blocker)
                .pendingCount(pendingCount)
                .pendingAmount(pendingAmount)
                .paymentsInProgress(payments)
                .nextYearLabel(this.debtCarryOverService.nextYear(year).map(AcademicYearEntity::getLabel).orElse(null))
                .outstanding(rows.stream().limit(OUTSTANDING_SHOWN).map(row -> SchoolYearClosureDto.Outstanding.builder()
                        .studentId(Objects.requireNonNull(row.get(student.id)).toString())
                        .studentName(String.format("%s %s", row.get(student.lastName), row.get(student.firstName)))
                        .registrationNumber(row.get(student.registrationNumber))
                        .installmentCount(Objects.requireNonNullElse(row.get(4, Long.class), 0L))
                        .amount(Objects.requireNonNullElse(row.get(5, BigDecimal.class), BigDecimal.ZERO))
                        .build()).toList())
                .build();
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
}
