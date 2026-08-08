package com.ypyit.neoelima.domain.subscription.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Formule d'abonnement à Nelima.
 *
 * <p>Le palier et le libellé vivaient dans une énumération : le raisonnement tenait tant que la
 * grille était figée. Il ne tient plus dès qu'on veut créer une offre de lancement ou un contrat
 * cadre sans passer par une livraison. Tout est ici, et rien n'est plus dans le code.
 *
 * <p>Le {@code code} reste la clé métier — c'est lui qui est écrit sur l'établissement et sur la
 * facture. Il ne change pas : renommer une formule change son libellé, jamais son code, sinon les
 * factures déjà émises désigneraient une formule qui n'existe plus.
 */
@Entity
@Table(name = "subscription_plan")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 32, updatable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(length = 160)
    private String description;

    /** Plafond d'effectif. Nul = sans plafond : le palier de tête, celui qui se négocie. */
    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    /** Une formule retirée cesse d'être proposée, mais reste lisible sur les contrats en cours. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private int position = 0;

    /**
     * Montrée sur le site public.
     *
     * <p>Fausse par défaut : une formule créée depuis la console est d'abord un tarif interne. La
     * publier est une décision, pas un effet de bord de sa création.
     */
    @Column(name = "public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    /** Mise en avant sur le site. Une seule à la fois — le service s'en charge. */
    @Column(nullable = false)
    @Builder.Default
    private boolean featured = false;

    /** Une formule sans plafond couvre tout effectif : c'est le repli quand aucune ne convient. */
    public boolean covers(long studentCount) {
        return Objects.isNull(this.maxStudents) || studentCount <= this.maxStudents;
    }
}
