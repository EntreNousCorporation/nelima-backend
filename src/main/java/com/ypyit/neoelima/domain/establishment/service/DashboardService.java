package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.DashboardSummaryDto;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QLevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.QReminderDeliveryEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QSchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.payment.entity.QReceiptEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        Instant startOfDay = today.atStartOfDay(ABIDJAN).toInstant();
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        return DashboardSummaryDto.builder()
                .studentCount(this.countStudents(scope))
                .collectedThisMonth(this.collectedBetween(scope, startOfMonth, null))
                .receiptsThisMonth(this.countReceiptsSince(scope, startOfMonth))
                .collectedToday(this.collectedBetween(scope, startOfDay, null))
                .paymentsToday(this.countReceiptsSince(scope, startOfDay))
                .expectedThisMonth(this.expectedBetween(scope, firstOfMonth, firstOfMonth.plusMonths(1)))
                .expectedPreviousMonth(this.expectedBetween(scope,
                        firstOfMonth.minusMonths(1), firstOfMonth))
                .collectedPreviousMonth(this.collectedBetween(scope,
                        firstOfMonth.minusMonths(1).atStartOfDay(ABIDJAN).toInstant(), startOfMonth))
                .monthly(this.monthlySeries(scope, firstOfMonth))
                .classFilling(this.classFilling(scope))
                .pendingAmount(this.pendingAmount(scope, null))
                .pendingCount(this.pendingCount(scope, null))
                .overdueAmount(this.pendingAmount(scope, today))
                .overdueCount(this.pendingCount(scope, today))
                .topOverdue(this.topOverdue(scope, today))
                .recentReceipts(this.recentReceipts(scope))
                .build();
    }

    /**
     * Série des six derniers mois, mois courant inclus.
     *
     * <p>Les deux grandeurs ne se lisent pas dans la même table : l'attendu vient de l'échéance des
     * tranches, l'encaissé de la date d'émission des reçus. Un mois sans mouvement doit tout de même
     * figurer, sans quoi le graphique sauterait des colonnes.
     */
    private List<DashboardSummaryDto.MonthlyPointDto> monthlySeries(UUID scope, LocalDate firstOfMonth) {
        List<DashboardSummaryDto.MonthlyPointDto> points = new ArrayList<>();
        for (int back = 5; back >= 0; back--) {
            LocalDate start = firstOfMonth.minusMonths(back);
            LocalDate end = start.plusMonths(1);
            points.add(DashboardSummaryDto.MonthlyPointDto.builder()
                    .month(start.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .expected(this.expectedBetween(scope, start, end))
                    .collected(this.collectedBetween(scope,
                            start.atStartOfDay(ABIDJAN).toInstant(),
                            end.atStartOfDay(ABIDJAN).toInstant()))
                    .build());
        }
        return points;
    }

    /** Montant attendu sur une période : toutes les tranches qui y échoient, réglées ou non. */
    private BigDecimal expectedBetween(UUID scope, LocalDate from, LocalDate to) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        BigDecimal total = this.queryFactory.select(installment.amount.sum()).from(installment)
                .where(installment.dueDate.goe(from), installment.dueDate.before(to),
                        scope == null ? null
                                : installment.studentFee.student.establishment.id.eq(scope))
                .fetchOne();
        return Objects.requireNonNullElse(total, BigDecimal.ZERO);
    }

    /**
     * Élèves aux plus gros retards, avec l'ancienneté de leur plus vieille échéance dépassée.
     *
     * <p>Agrégé par élève et non par tranche : une famille avec trois impayés doit apparaître une
     * fois, avec son solde total, sinon la liste des relances la répète et fausse le classement.
     */
    private List<DashboardSummaryDto.OverdueStudentDto> topOverdue(UUID scope, LocalDate today) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;

        // Jointures explicites, palier par palier : joindre directement `studentFee.student`
        // depuis `installment` ne traverse pas les deux niveaux et rend un résultat vide.
        QLevelOfStudyEntity level = QLevelOfStudyEntity.levelOfStudyEntity;

        // Jointure externe sur le niveau : le désigner par un chemin implicite en produirait une
        // interne, et un élève sans niveau renseigné disparaîtrait de la liste des relances — soit
        // exactement celui qu'une école risque d'oublier.
        List<Tuple> rows = this.queryFactory
                .select(student.id, student.firstName, student.lastName,
                        student.registrationNumber, level.code,
                        installment.amount.sum(), installment.dueDate.min())
                .from(installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .leftJoin(student.levelOfStudy, level)
                .where(installment.status.eq(InstallmentStatus.PENDING),
                        installment.dueDate.before(today),
                        scope == null ? null : student.establishment.id.eq(scope))
                .groupBy(student.id, student.firstName, student.lastName,
                        student.registrationNumber, level.code)
                .orderBy(installment.amount.sum().desc())
                .limit(6)
                .fetch();

        Map<UUID, Long> reminders = this.remindersByStudent(rows.stream()
                .map(row -> row.get(student.id)).filter(Objects::nonNull).toList());

        return rows.stream()
                .map(row -> DashboardSummaryDto.OverdueStudentDto.builder()
                        .reminderCount(reminders.getOrDefault(row.get(student.id), 0L))
                        .studentId(Objects.toString(row.get(student.id), null))
                        .label(String.join(" ",
                                Objects.toString(row.get(student.firstName), ""),
                                Objects.toString(row.get(student.lastName), "")).trim())
                        .registrationNumber(row.get(student.registrationNumber))
                        .levelCode(row.get(level.code))
                        .daysLate(ChronoUnit.DAYS.between(
                                Objects.requireNonNullElse(row.get(installment.dueDate.min()), today), today))
                        .amount(Objects.requireNonNullElse(row.get(installment.amount.sum()), BigDecimal.ZERO))
                        .build())
                .toList();
    }

    /**
     * Rappels déjà envoyés, par élève.
     *
     * <p>Une seule requête groupée pour toute la liste : une par ligne suffirait sur six lignes,
     * mais le jour où la liste s'allonge on ne s'en apercevrait qu'en production.
     */
    private Map<UUID, Long> remindersByStudent(List<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        QReminderDeliveryEntity delivery = QReminderDeliveryEntity.reminderDeliveryEntity;
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        return this.queryFactory
                .select(student.id, delivery.count())
                .from(delivery)
                .join(delivery.installment, installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(student.id.in(studentIds))
                .groupBy(student.id)
                .fetch().stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> row.get(student.id),
                        row -> Objects.requireNonNullElse(row.get(delivery.count()), 0L)));
    }

    /**
     * Effectif de chaque classe rapporté à sa capacité.
     *
     * <p>Jointure externe depuis la classe : une classe créée mais encore vide doit figurer, c'est
     * même celle dont l'école doit se souvenir avant la rentrée.
     */
    private List<DashboardSummaryDto.ClassFillingDto> classFilling(UUID scope) {
        if (Objects.isNull(scope)) {
            // Un administrateur YPYit lit les chiffres du parc : le remplissage des classes de
            // toutes les écoles confondues n'aurait aucun sens.
            return List.of();
        }
        QSchoolClassEntity schoolClass = QSchoolClassEntity.schoolClassEntity;
        QStudentEntity student = QStudentEntity.studentEntity;
        QLevelOfStudyEntity level = QLevelOfStudyEntity.levelOfStudyEntity;

        return this.queryFactory
                .select(schoolClass.id, schoolClass.name, level.code,
                        schoolClass.capacity, student.count())
                .from(schoolClass)
                .leftJoin(schoolClass.levelOfStudy, level)
                .leftJoin(student).on(student.schoolClass.id.eq(schoolClass.id))
                .where(schoolClass.establishment.id.eq(scope))
                .groupBy(schoolClass.id, schoolClass.name, level.code, schoolClass.capacity)
                .orderBy(schoolClass.name.asc())
                .fetch().stream()
                .map(row -> DashboardSummaryDto.ClassFillingDto.builder()
                        .id(Objects.toString(row.get(schoolClass.id), null))
                        .name(row.get(schoolClass.name))
                        .levelLabel(row.get(level.code))
                        .capacity(row.get(schoolClass.capacity))
                        .studentCount(Objects.requireNonNullElse(row.get(student.count()), 0L))
                        .build())
                .toList();
    }

    private long countStudents(UUID scope) {
        QStudentEntity student = QStudentEntity.studentEntity;
        Long count = this.queryFactory.select(student.count()).from(student)
                .where(scope == null ? null : student.establishment.id.eq(scope))
                .fetchOne();
        return Objects.requireNonNullElse(count, 0L);
    }

    /** @param until borne haute facultative ; absente, la période court jusqu'à maintenant */
    private BigDecimal collectedBetween(UUID scope, Instant since, Instant until) {
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        BigDecimal total = this.queryFactory.select(receipt.amount.sum()).from(receipt)
                .where(receipt.issuedAt.goe(since),
                        until == null ? null : receipt.issuedAt.before(until),
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
