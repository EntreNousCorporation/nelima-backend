package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Qui doit être informé d'un encaissement.
 *
 * <p>La règle est tenue ici et nulle part ailleurs : elle sert au courriel comme à la notification
 * push, et la recopier dans chaque canal la ferait fatalement diverger — un canal finirait par
 * prévenir quelqu'un que l'autre ignore.
 *
 * <p>Le destinataire naturel est le <strong>tuteur</strong> de l'élève, rattaché dans le système et
 * dont les coordonnées y sont tenues à jour. S'y ajoute la personne qui a réglé lorsqu'elle est
 * identifiée : le paiement étant découplé du tutorat, un oncle ou un bienfaiteur a droit à sa
 * quittance.
 *
 * <p>Le compte d'un utilisateur d'établissement est <strong>toujours exclu</strong> : l'agent qui
 * saisit une opération au comptoir n'a pas payé, et lui adresser la pièce comptable d'une famille
 * exposerait ses données sans raison.
 */
final class ReceiptAudience {

    private ReceiptAudience() {
    }

    /**
     * Comptes concernés, dans l'ordre tuteurs puis payeur.
     *
     * <p>Dédoublonné sur l'identifiant et non sur l'objet : le tuteur qui règle lui-même est chargé
     * deux fois par Hibernate selon le chemin d'accès, et l'égalité d'instance ne suffirait pas à
     * l'empêcher d'être prévenu deux fois.
     */
    static Collection<UserEntity> accountsOf(PaymentIntentEntity intent) {
        Map<UUID, UserEntity> accounts = new LinkedHashMap<>();

        StudentEntity student = intent.getInstallment().getStudentFee().getStudent();
        student.getParentUsers().forEach(parent -> put(accounts, parent));

        if (!(intent.getPayer() instanceof EstablishmentUserEntity)) {
            put(accounts, intent.getPayer());
        }
        return accounts.values();
    }

    private static void put(Map<UUID, UserEntity> accounts, UserEntity user) {
        if (Objects.nonNull(user) && Objects.nonNull(user.getId())) {
            accounts.putIfAbsent(user.getId(), user);
        }
    }
}
