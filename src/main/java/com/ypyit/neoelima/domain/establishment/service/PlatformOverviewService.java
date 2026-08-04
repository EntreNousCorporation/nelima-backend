package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.domain.establishment.dto.PlatformOverviewDto;
import com.ypyit.neoelima.domain.establishment.entity.QEstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.payment.entity.QPaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.QReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Agrégats du parc, pour la console YPYit.
 *
 * <p>Tout est calculé en base, pour la raison qui vaut déjà pour le tableau de bord d'une école :
 * additionner une page de résultats donne des totaux justes tant que les données tiennent sur une
 * page, puis faux sans prévenir.
 *
 * <p>La ventilation par établissement se fait en quatre requêtes groupées, recomposées en mémoire,
 * plutôt qu'en une requête par école. Avec cent écoles au catalogue, la seconde forme ferait quatre
 * cents allers-retours pour afficher un tableau.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformOverviewService {

    /** Le mois s'entend à l'heure d'Abidjan, comme partout ailleurs dans les agrégats. */
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final JPAQueryFactory queryFactory;

    public PlatformOverviewDto overview() {
        LocalDate today = LocalDate.now(ABIDJAN);
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        Instant startOfMonth = firstOfMonth.atStartOfDay(ABIDJAN).toInstant();
        Instant startOfPreviousMonth = firstOfMonth.minusMonths(1).atStartOfDay(ABIDJAN).toInstant();

        QEstablishmentEntity establishment = QEstablishmentEntity.establishmentEntity;

        Map<UUID, Long> students = this.studentsByEstablishment();
        Map<UUID, BigDecimal> collected = this.collectedByEstablishment(startOfMonth, null);
        Map<UUID, BigDecimal> commissions = this.commissionByEstablishment(startOfMonth, null);
        Map<UUID, BigDecimal> overdueAmounts = new HashMap<>();
        Map<UUID, Long> overdueCounts = new HashMap<>();
        this.overdueByEstablishment(today, overdueAmounts, overdueCounts);

        // Seuls les établissements principaux figurent au parc : une antenne rattachée à un réseau
        // n'est pas un client de plus, et la compter en doublerait le nombre.
        List<Tuple> schools = this.queryFactory
                .select(establishment.id, establishment.name, establishment.active)
                .from(establishment)
                .where(establishment.isPrimary.isTrue())
                .orderBy(establishment.name.asc())
                .fetch();

        List<PlatformOverviewDto.SchoolRowDto> rows = schools.stream()
                .map(row -> {
                    UUID id = row.get(establishment.id);
                    return PlatformOverviewDto.SchoolRowDto.builder()
                            .id(Objects.toString(id, null))
                            .name(row.get(establishment.name))
                            .active(Boolean.TRUE.equals(row.get(establishment.active)))
                            .studentCount(students.getOrDefault(id, 0L))
                            .collectedThisMonth(collected.getOrDefault(id, BigDecimal.ZERO))
                            .commissionThisMonth(commissions.getOrDefault(id, BigDecimal.ZERO))
                            .overdueAmount(overdueAmounts.getOrDefault(id, BigDecimal.ZERO))
                            .overdueCount(overdueCounts.getOrDefault(id, 0L))
                            .build();
                })
                .toList();

        return PlatformOverviewDto.builder()
                .schoolCount(rows.size())
                .activeSchoolCount(rows.stream().filter(PlatformOverviewDto.SchoolRowDto::isActive).count())
                .commissionThisMonth(this.totalCommission(startOfMonth, null))
                .commissionPreviousMonth(this.totalCommission(startOfPreviousMonth, startOfMonth))
                .onlineCollectedThisMonth(this.onlineCollected(startOfMonth))
                .schools(rows)
                .build();
    }

    private Map<UUID, Long> studentsByEstablishment() {
        QStudentEntity student = QStudentEntity.studentEntity;
        return this.queryFactory
                .select(student.establishment.id, student.count())
                .from(student)
                .groupBy(student.establishment.id)
                .fetch().stream()
                .collect(HashMap::new,
                        (map, row) -> map.put(row.get(student.establishment.id),
                                Objects.requireNonNullElse(row.get(student.count()), 0L)),
                        HashMap::putAll);
    }

    private Map<UUID, BigDecimal> collectedByEstablishment(Instant since, Instant until) {
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        return this.queryFactory
                .select(receipt.establishment.id, receipt.amount.sum())
                .from(receipt)
                .where(receipt.issuedAt.goe(since), until == null ? null : receipt.issuedAt.before(until))
                .groupBy(receipt.establishment.id)
                .fetch().stream()
                .collect(HashMap::new,
                        (map, row) -> map.put(row.get(receipt.establishment.id),
                                Objects.requireNonNullElse(row.get(receipt.amount.sum()), BigDecimal.ZERO)),
                        HashMap::putAll);
    }

    /**
     * Commission par établissement.
     *
     * <p>Elle se lit sur la tentative de paiement et non sur le reçu, qui ne porte que le montant
     * réglé. L'établissement, lui, n'est atteignable qu'à travers la tranche puis l'élève : d'où
     * les jointures explicites, un chemin implicite sur trois niveaux ne traversant pas.
     */
    private Map<UUID, BigDecimal> commissionByEstablishment(Instant since, Instant until) {
        QPaymentIntentEntity intent = QPaymentIntentEntity.paymentIntentEntity;
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        return this.queryFactory
                .select(student.establishment.id, intent.amountCommission.sum())
                .from(intent)
                .join(intent.installment, installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(intent.status.eq(PaymentIntentStatus.SUCCEEDED),
                        intent.settledAt.goe(since),
                        until == null ? null : intent.settledAt.before(until))
                .groupBy(student.establishment.id)
                .fetch().stream()
                .collect(HashMap::new,
                        (map, row) -> map.put(row.get(student.establishment.id),
                                Objects.requireNonNullElse(row.get(intent.amountCommission.sum()), BigDecimal.ZERO)),
                        HashMap::putAll);
    }

    private void overdueByEstablishment(LocalDate today, Map<UUID, BigDecimal> amounts,
                                        Map<UUID, Long> counts) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        this.queryFactory
                .select(student.establishment.id, installment.amount.sum(), installment.count())
                .from(installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(installment.status.eq(InstallmentStatus.PENDING),
                        installment.dueDate.before(today))
                .groupBy(student.establishment.id)
                .fetch()
                .forEach(row -> {
                    UUID id = row.get(student.establishment.id);
                    amounts.put(id, Objects.requireNonNullElse(
                            row.get(installment.amount.sum()), BigDecimal.ZERO));
                    counts.put(id, Objects.requireNonNullElse(row.get(installment.count()), 0L));
                });
    }

    private BigDecimal totalCommission(Instant since, Instant until) {
        QPaymentIntentEntity intent = QPaymentIntentEntity.paymentIntentEntity;
        BigDecimal total = this.queryFactory.select(intent.amountCommission.sum()).from(intent)
                .where(intent.status.eq(PaymentIntentStatus.SUCCEEDED),
                        intent.settledAt.goe(since),
                        until == null ? null : intent.settledAt.before(until))
                .fetchOne();
        // `sum()` rend null sur un ensemble vide : un mois sans paiement en ligne affiche 0.
        return Objects.requireNonNullElse(total, BigDecimal.ZERO);
    }

    private BigDecimal onlineCollected(Instant since) {
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        BigDecimal total = this.queryFactory.select(receipt.amount.sum()).from(receipt)
                .where(receipt.issuedAt.goe(since), receipt.channel.eq(PaymentChannel.ONLINE))
                .fetchOne();
        return Objects.requireNonNullElse(total, BigDecimal.ZERO);
    }
}
