package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.utils.XofFormat;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.CarryOverDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.payment.entity.QPaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Report des impayés d'une année sur la suivante.
 *
 * <p>Une année ne se clôture que tout encaissé. Quand une famille doit encore à la rentrée, on ne
 * renonce pas à la dette : elle passe sur l'année qui commence, sous un frais « Report 2025-2026 »,
 * une tranche pour chaque tranche due, au même montant et payable dès la rentrée. La tranche
 * d'origine est marquée reportée et pointe vers sa remplaçante ; l'année d'origine peut se clôturer.
 *
 * <p>Un paiement en ligne en cours de confirmation n'est pas reporté : il va peut-être aboutir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DebtCarryOverService {

    private final AcademicYearRepository academicYearRepository;
    private final EstablishmentRepository establishmentRepository;
    private final FeeRepository feeRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final InstallmentRepository installmentRepository;
    private final SchoolYearGuard schoolYearGuard;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final JPAQueryFactory queryFactory;

    /** L'année qui suit, par date de début ; vide si la plateforme ne l'a pas encore définie. */
    public Optional<AcademicYearEntity> nextYear(AcademicYearEntity year) {
        return this.academicYearRepository.findAll().stream()
                .filter(other -> Objects.nonNull(other.getStartDate()) && other.getStartDate().isAfter(year.getStartDate()))
                .min(Comparator.comparing(AcademicYearEntity::getStartDate));
    }

    @Transactional
    public CarryOverDto carryOver(UUID academicYearId) {
        UUID scope = this.scope();
        AcademicYearEntity year = this.academicYearRepository.findById(academicYearId)
                .orElseThrow(() -> new NotFoundException("Année introuvable."));
        if (year.isActive()) {
            throw new BadRequestException("L'année en cours ne se reporte pas : ses impayés se reportent une fois l'année suivante commencée.");
        }
        this.schoolYearGuard.assertWritable(scope, year);
        AcademicYearEntity target = this.nextYear(year)
                .orElseThrow(() -> new BadRequestException("L'année suivante n'est pas encore définie par la plateforme."));
        this.schoolYearGuard.assertWritable(scope, target);

        List<InstallmentEntity> pending = this.pendingInstallments(scope, year);
        if (pending.isEmpty()) {
            throw new BadRequestException("Aucune tranche à reporter : tout est encaissé.");
        }

        String feeName = "Report " + year.getLabel();
        FeeEntity fee = this.feeRepository.findByEstablishment_IdAndAcademicYear_IdAndName(scope, target.getId(), feeName)
                .orElseGet(() -> this.feeRepository.saveAndFlush(FeeEntity.builder()
                        .name(feeName).price(BigDecimal.ZERO)
                        // Facultatif : il ne s'applique à personne d'office, seulement aux élèves reportés.
                        .optional(true).academical(false)
                        .establishment(this.establishmentRepository.getReferenceById(scope))
                        .academicYear(target).build()));

        Map<UUID, StudentFeeEntity> byStudent = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (InstallmentEntity installment : pending) {
            StudentEntity student = installment.getStudentFee().getStudent();
            StudentFeeEntity studentFee = byStudent.computeIfAbsent(student.getId(), id ->
                    this.studentFeeRepository.findByStudent_IdAndFee_Id(id, fee.getId())
                            .orElseGet(() -> this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                                    .student(student).fee(fee).name(feeName).build())));
            InstallmentEntity carried = this.installmentRepository.save(InstallmentEntity.builder()
                    .studentFee(studentFee)
                    .label(labelOf(installment))
                    .amount(installment.getAmount())
                    .dueDate(target.getStartDate())
                    .status(InstallmentStatus.PENDING)
                    .build());
            installment.setStatus(InstallmentStatus.CARRIED_OVER);
            installment.setCarriedTo(carried);
            this.installmentRepository.save(installment);
            total = total.add(Objects.requireNonNullElse(installment.getAmount(), BigDecimal.ZERO));
        }
        this.installmentRepository.flush();

        this.auditService.record(scope, AuditAction.DEBTS_CARRIED_OVER, year.getLabel(),
                String.format("%d tranche(s) de %d élève(s), %s, reportées sur %s",
                        pending.size(), byStudent.size(), XofFormat.format(total), target.getLabel()));
        log.info("DEBTS_CARRIED_OVER: {} installments ({}) from {} to {} in establishment {}",
                pending.size(), total, year.getLabel(), target.getLabel(), scope);
        return CarryOverDto.builder()
                .installments(pending.size())
                .students(byStudent.size())
                .amount(total)
                .targetLabel(target.getLabel())
                .build();
    }

    /** Tranches dues de l'année, sauf celles dont un paiement en ligne attend sa confirmation. */
    private List<InstallmentEntity> pendingInstallments(UUID scope, AcademicYearEntity year) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QPaymentIntentEntity intent = QPaymentIntentEntity.paymentIntentEntity;
        Set<UUID> inProgress = Set.copyOf(this.queryFactory.select(intent.installment.id).from(intent)
                .where(intent.status.eq(PaymentIntentStatus.PENDING), intent.installment.isNotNull())
                .fetch());
        return this.queryFactory.selectFrom(installment)
                .where(installment.studentFee.student.establishment.id.eq(scope),
                        installment.studentFee.fee.academicYear.id.eq(year.getId()),
                        installment.status.eq(InstallmentStatus.PENDING))
                .fetch().stream()
                .filter(i -> !inProgress.contains(i.getId()))
                .toList();
    }

    /** « Scolarité — 2e tranche » : on doit reconnaître d'où vient la dette. */
    private static String labelOf(InstallmentEntity installment) {
        FeeEntity fee = installment.getStudentFee().getFee();
        String feeName = Objects.isNull(fee) ? installment.getStudentFee().getName() : fee.getName();
        String label = Objects.toString(installment.getLabel(), "");
        return StringUtils.isBlank(feeName) ? label : String.format("%s — %s", feeName, label);
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
