package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.ParentSummaryDto;
import com.ypyit.neoelima.domain.establishment.entity.QEstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QLevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.QSchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.user.entity.QTranslateEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Chiffres d'accueil de l'application parent.
 *
 * <p>Trois requêtes, quel que soit le nombre d'enfants : la liste des enfants, un agrégat groupé
 * par enfant, un total du mois. Jamais une requête par enfant — la forme se tient à deux enfants et
 * se voit à dix, et c'est précisément le genre de code qu'on n'a plus l'occasion de reprendre.
 *
 * <p>La portée ne vient d'aucun paramètre : un parent n'a pas de périmètre établissement, elle se
 * dérive de ses enfants rattachés. Accepter un identifiant du client ouvrirait les chiffres d'une
 * autre famille à qui les demanderait.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentDashboardService {

    /**
     * Le mois courant s'entend à l'heure d'Abidjan, pas à celle du serveur.
     *
     * <p>Sans quoi une échéance du 1er serait comptée sur le mois précédent chaque fois que la
     * machine tourne en UTC — c'est-à-dire toujours, en production.
     */
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final JPAQueryFactory queryFactory;
    private final CurrentUserProvider currentUserProvider;

    public ParentSummaryDto summary() {
        UUID parentId = this.currentUserProvider.currentUser().getId();
        List<ParentSummaryDto.ParentChildDto> children = this.children(parentId);

        // Un compte fraîchement créé n'a encore rattaché personne, et c'est le premier écran qu'il
        // voit : des zéros, pas une erreur.
        if (children.isEmpty()) {
            return ParentSummaryDto.builder()
                    .dueThisMonth(BigDecimal.ZERO)
                    .dueThisMonthCount(0)
                    .outstandingAmount(BigDecimal.ZERO)
                    .overdueAmount(BigDecimal.ZERO)
                    .childrenCount(0)
                    .children(List.of())
                    .build();
        }

        LocalDate today = LocalDate.now(ABIDJAN);
        List<UUID> ids = children.stream().map(ParentSummaryDto.ParentChildDto::getId).toList();
        BigDecimal overdue = this.attachBalances(children, ids, today);

        LocalDate firstOfMonth = today.withDayOfMonth(1);
        Tuple month = this.dueBetween(ids, firstOfMonth, firstOfMonth.plusMonths(1));

        return ParentSummaryDto.builder()
                .dueThisMonth(amountAt(month, 0))
                .dueThisMonthCount(countAt(month, 1))
                .nextDueDate(children.stream()
                        .map(ParentSummaryDto.ParentChildDto::getNextDueDate)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null))
                .outstandingAmount(children.stream()
                        .map(ParentSummaryDto.ParentChildDto::getOutstandingAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .overdueAmount(overdue)
                .childrenCount(children.size())
                // Le plus urgent en tête : c'est l'ordre dans lequel un parent lit la liste, et
                // celui qui met sous ses yeux l'enfant pour lequel il doit agir aujourd'hui.
                .children(children.stream()
                        .sorted(Comparator.comparing(ParentSummaryDto.ParentChildDto::getNextDueDate,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(ParentSummaryDto.ParentChildDto::getLastName,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList())
                .build();
    }

    /**
     * Les enfants rattachés au parent, avec de quoi les reconnaître.
     *
     * <p>Toutes les jointures sont externes : un élève sans classe, sans niveau ou dont
     * l'établissement a été détaché doit rester visible. Le faire disparaître de sa propre liste
     * serait la pire réponse possible à un défaut de saisie de l'école.
     */
    private List<ParentSummaryDto.ParentChildDto> children(UUID parentId) {
        QStudentEntity student = QStudentEntity.studentEntity;
        QSchoolClassEntity schoolClass = QSchoolClassEntity.schoolClassEntity;
        QLevelOfStudyEntity level = QLevelOfStudyEntity.levelOfStudyEntity;
        QTranslateEntity levelName = QTranslateEntity.translateEntity;
        QEstablishmentEntity establishment = QEstablishmentEntity.establishmentEntity;

        return this.queryFactory
                .select(student.id, student.firstName, student.lastName, student.registrationNumber,
                        schoolClass.name, levelName.fr, establishment.name)
                .from(student)
                .leftJoin(student.schoolClass, schoolClass)
                .leftJoin(student.levelOfStudy, level)
                .leftJoin(level.name, levelName)
                .leftJoin(student.establishment, establishment)
                .where(student.parentUsers.any().id.eq(parentId))
                .fetch().stream()
                .map(row -> ParentSummaryDto.ParentChildDto.builder()
                        .id(row.get(student.id))
                        .firstName(row.get(student.firstName))
                        .lastName(row.get(student.lastName))
                        .registrationNumber(row.get(student.registrationNumber))
                        .className(row.get(schoolClass.name))
                        .levelLabel(row.get(levelName.fr))
                        .establishmentName(row.get(establishment.name))
                        .outstandingAmount(BigDecimal.ZERO)
                        .build())
                .toList();
    }

    /**
     * Attache à chaque enfant son reste dû et sa prochaine échéance, et rend le retard total.
     *
     * <p>Une seule requête groupée pour toute la fratrie, sur le modèle de
     * {@code StudentServiceImpl.attachBalances} : sur une page d'élèves, une requête par ligne se
     * remarque en production et pas avant.
     *
     * @return la part du reste dû dont l'échéance est déjà passée, tous enfants confondus
     */
    private BigDecimal attachBalances(List<ParentSummaryDto.ParentChildDto> children,
                                      List<UUID> ids, LocalDate today) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        List<Tuple> rows = this.queryFactory
                .select(student.id, installment.amount.sum(),
                        new CaseBuilder().when(installment.dueDate.before(today))
                                .then(installment.amount).otherwise(BigDecimal.ZERO).sum(),
                        installment.dueDate.min())
                .from(installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(student.id.in(ids), installment.status.eq(InstallmentStatus.PENDING))
                .groupBy(student.id)
                .fetch();

        BigDecimal overdue = BigDecimal.ZERO;
        for (Tuple row : rows) {
            UUID id = row.get(student.id);
            overdue = overdue.add(amountAt(row, 2));
            children.stream().filter(child -> Objects.equals(child.getId(), id)).findFirst()
                    .ifPresent(child -> {
                        child.setOutstandingAmount(amountAt(row, 1));
                        child.setNextDueDate(row.get(3, LocalDate.class));
                    });
        }
        // Les enfants absents des résultats gardent le zéro posé à la construction : la requête a
        // bien porté sur eux, ils ne doivent simplement rien.
        return overdue;
    }

    /** Montant et nombre des tranches encore dues dont l'échéance tombe dans la période. */
    private Tuple dueBetween(List<UUID> ids, LocalDate from, LocalDate to) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        return this.queryFactory
                .select(installment.amount.sum(), installment.count())
                .from(installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(student.id.in(ids),
                        installment.status.eq(InstallmentStatus.PENDING),
                        installment.dueDate.goe(from),
                        installment.dueDate.before(to))
                .fetchOne();
    }

    private static BigDecimal amountAt(Tuple tuple, int index) {
        if (Objects.isNull(tuple)) {
            return BigDecimal.ZERO;
        }
        return Objects.requireNonNullElse(tuple.get(index, BigDecimal.class), BigDecimal.ZERO);
    }

    private static long countAt(Tuple tuple, int index) {
        if (Objects.isNull(tuple)) {
            return 0L;
        }
        return Objects.requireNonNullElse(tuple.get(index, Long.class), 0L);
    }
}
