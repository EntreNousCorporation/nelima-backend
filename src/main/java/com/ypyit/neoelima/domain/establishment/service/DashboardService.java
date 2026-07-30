package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.DashboardSummaryDto;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.payment.entity.QReceiptEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Chiffres d'accueil de l'espace établissement.
 *
 * <p>Tout est agrégé en base. L'alternative — additionner côté client une page de résultats —
 * donnerait des totaux justes tant que les données tiennent sur une page, puis faux sans prévenir,
 * ce qui est la pire des deux situations pour des montants d'argent.
 *
 * <p>La portée vient de l'utilisateur authentifié, comme partout ailleurs : une école ne voit que
 * ses propres chiffres, un admin YPYit peut cibler un établissement.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /**
     * Le mois courant s'entend à l'heure d'Abidjan, pas à celle du serveur : un encaissement du
     * 1er au matin ne doit pas être compté sur le mois précédent parce que la machine est en UTC.
     */
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final JPAQueryFactory queryFactory;
    private final CurrentUserProvider currentUserProvider;

    public DashboardSummaryDto summaryOf(UUID requestedEstablishmentId) {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(requestedEstablishmentId);

        Instant startOfMonth = LocalDate.now(ABIDJAN).withDayOfMonth(1)
                .atStartOfDay(ABIDJAN).toInstant();
        LocalDate today = LocalDate.now(ABIDJAN);

        return DashboardSummaryDto.builder()
                .studentCount(this.countStudents(scope))
                .collectedThisMonth(this.collectedSince(scope, startOfMonth))
                .receiptsThisMonth(this.countReceiptsSince(scope, startOfMonth))
                .pendingAmount(this.pendingAmount(scope, null))
                .pendingCount(this.pendingCount(scope, null))
                .overdueAmount(this.pendingAmount(scope, today))
                .overdueCount(this.pendingCount(scope, today))
                .recentReceipts(this.recentReceipts(scope))
                .build();
    }

    private long countStudents(UUID scope) {
        QStudentEntity student = QStudentEntity.studentEntity;
        Long count = this.queryFactory.select(student.count()).from(student)
                .where(scope == null ? null : student.establishment.id.eq(scope))
                .fetchOne();
        return Objects.requireNonNullElse(count, 0L);
    }

    private BigDecimal collectedSince(UUID scope, Instant since) {
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        BigDecimal total = this.queryFactory.select(receipt.amount.sum()).from(receipt)
                .where(receipt.issuedAt.goe(since),
                        scope == null ? null : receipt.establishment.id.eq(scope))
                .fetchOne();
        // `sum()` rend null sur un ensemble vide : une école sans encaissement affiche 0, pas rien.
        return Objects.requireNonNullElse(total, BigDecimal.ZERO);
    }

    private long countReceiptsSince(UUID scope, Instant since) {
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        Long count = this.queryFactory.select(receipt.count()).from(receipt)
                .where(receipt.issuedAt.goe(since),
                        scope == null ? null : receipt.establishment.id.eq(scope))
                .fetchOne();
        return Objects.requireNonNullElse(count, 0L);
    }

    /** @param dueBefore borne facultative : renseignée, ne retient que les échéances dépassées */
    private BigDecimal pendingAmount(UUID scope, LocalDate dueBefore) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        BigDecimal total = this.queryFactory.select(installment.amount.sum()).from(installment)
                .where(installment.status.eq(InstallmentStatus.PENDING),
                        dueBefore == null ? null : installment.dueDate.before(dueBefore),
                        scope == null ? null
                                : installment.studentFee.student.establishment.id.eq(scope))
                .fetchOne();
        return Objects.requireNonNullElse(total, BigDecimal.ZERO);
    }

    private long pendingCount(UUID scope, LocalDate dueBefore) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        Long count = this.queryFactory.select(installment.count()).from(installment)
                .where(installment.status.eq(InstallmentStatus.PENDING),
                        dueBefore == null ? null : installment.dueDate.before(dueBefore),
                        scope == null ? null
                                : installment.studentFee.student.establishment.id.eq(scope))
                .fetchOne();
        return Objects.requireNonNullElse(count, 0L);
    }

    /**
     * Cinq derniers reçus, projetés colonne par colonne.
     *
     * <p>Charger les entités tirerait toute la chaîne paiement → tranche → élève → établissement
     * pour n'afficher qu'un numéro, un montant et un nom.
     */
    private List<DashboardSummaryDto.ReceiptSummaryDto> recentReceipts(UUID scope) {
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        List<Tuple> rows = this.queryFactory
                .select(receipt.id, receipt.number, receipt.amount,
                        receipt.studentLabel, receipt.issuedAt)
                .from(receipt)
                .where(scope == null ? null : receipt.establishment.id.eq(scope))
                .orderBy(receipt.issuedAt.desc())
                .limit(5)
                .fetch();

        return rows.stream()
                .map(row -> DashboardSummaryDto.ReceiptSummaryDto.builder()
                        .id(Objects.toString(row.get(receipt.id), null))
                        .number(row.get(receipt.number))
                        .amount(row.get(receipt.amount))
                        .studentLabel(row.get(receipt.studentLabel))
                        .issuedAt(row.get(receipt.issuedAt))
                        .build())
                .toList();
    }
}
