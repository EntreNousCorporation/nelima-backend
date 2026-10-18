package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentDocumentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.StudentDocumentKind;
import com.ypyit.neoelima.domain.establishment.enums.StudentPaymentStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Les définitions qui classent un élève — statut de paiement, dossier complet — écrites une seule
 * fois.
 *
 * <p>La liste filtrée, ses onglets, les cartes de tête et les graphiques comptent tous les mêmes
 * élèves : si chacun réécrivait « en retard » à sa façon, l'onglet afficherait 214 et la carte 213,
 * et l'école ne saurait plus lequel croire.
 */
public final class StudentFilters {

    /** Pièces sans lesquelles un dossier est incomplet : celles que toutes les écoles réclament. */
    public static final List<StudentDocumentKind> REQUIRED_DOCUMENTS =
            List.of(StudentDocumentKind.BIRTH_CERTIFICATE, StudentDocumentKind.PHOTO);

    private StudentFilters() {
    }

    /** Au moins une tranche non réglée dont l'échéance est passée. */
    public static BooleanExpression overdue(QStudentEntity student, LocalDate today) {
        QInstallmentEntity installment = new QInstallmentEntity("overdueInstallment");
        return JPAExpressions.selectOne().from(installment)
                .where(installment.studentFee.student.id.eq(student.id),
                        installment.status.eq(InstallmentStatus.PENDING),
                        installment.dueDate.lt(today))
                .exists();
    }

    /** Un solde restant, dont une partie a déjà été réglée — et rien d'échu. */
    public static BooleanExpression deposit(QStudentEntity student, LocalDate today) {
        QInstallmentEntity pending = new QInstallmentEntity("depositPending");
        QInstallmentEntity paid = new QInstallmentEntity("depositPaid");
        return overdue(student, today).not()
                .and(JPAExpressions.selectOne().from(pending)
                        .where(pending.studentFee.student.id.eq(student.id),
                                pending.status.eq(InstallmentStatus.PENDING))
                        .exists())
                .and(JPAExpressions.selectOne().from(paid)
                        .where(paid.studentFee.student.id.eq(student.id),
                                paid.status.eq(InstallmentStatus.PAID))
                        .exists());
    }

    public static BooleanExpression paymentStatus(QStudentEntity student, StudentPaymentStatus status,
                                                  LocalDate today) {
        return switch (status) {
            case OVERDUE -> overdue(student, today);
            case DEPOSIT -> deposit(student, today);
            case UP_TO_DATE -> overdue(student, today).not().and(deposit(student, today).not());
        };
    }

    /** Il manque au moins une des pièces requises. */
    public static BooleanExpression incompleteDossier(QStudentEntity student) {
        BooleanExpression missing = null;
        for (StudentDocumentKind kind : REQUIRED_DOCUMENTS) {
            QStudentDocumentEntity document = new QStudentDocumentEntity("required" + kind.name());
            BooleanExpression lacks = JPAExpressions.selectOne().from(document)
                    .where(document.student.id.eq(student.id), document.kind.eq(kind))
                    .notExists();
            missing = missing == null ? lacks : missing.or(lacks);
        }
        return missing;
    }
}
