package com.ypyit.neoelima.domain.payment.entity;

import com.querydsl.core.annotations.QueryInit;
import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Reçu émis pour tout encaissement, en ligne comme au guichet.
 *
 * <p>Le numéro est séquentiel <strong>par établissement</strong> et sans trou : c'est une exigence
 * comptable, l'école doit pouvoir justifier la continuité de sa numérotation. La contrainte
 * d'unicité {@code (establishment_id, sequence_number)} en est le dernier rempart ; l'allocation
 * elle-même est sérialisée par {@link ReceiptCounterEntity}.
 *
 * <p>Les libellés d'élève et de payeur sont recopiés à l'émission plutôt que résolus par jointure :
 * un reçu est une pièce figée, il ne doit pas changer si l'élève est renommé ou change d'école.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "receipt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_receipt_establishment_sequence",
                columnNames = {"establishment_id", "sequence_number"})
})
public class ReceiptEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    /** Numéro présenté au parent, dérivé de la séquence (par exemple {@code 2026-000042}). */
    @Column(nullable = false, length = 64)
    private String number;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant issuedAt;

    private String studentLabel;

    private String studentRegistrationNumber;

    private String payerLabel;

    /**
     * Mode de règlement, recopié depuis la tentative de paiement à l'émission.
     *
     * <p>Le lire à travers {@code paymentIntent} coûterait une requête par ligne du journal de
     * caisse, la relation étant paresseuse à dessein. Un reçu fige déjà ce qu'il affiche.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentChannel channel;

    /**
     * Chargement paresseux volontaire. Un reçu porte déjà les libellés dont il a besoin, recopiés
     * à l'émission : rien ne justifie de joindre l'établissement pour l'afficher.
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    /**
     * Paresseux impérativement. En chargement immédiat — le défaut pour OneToOne — lire un reçu
     * entraînait la tentative de paiement, puis la tranche, la dette, l'élève, l'établissement et
     * ses collections : Hibernate produisait une seule requête au produit cartésien qui ne rendait
     * jamais la main. Le simple affichage de la liste des reçus bloquait le serveur.
     *
     * <p>{@code @QueryInit} est indispensable pour filtrer les reçus d'un élève : QueryDSL
     * n'initialise les chemins imbriqués que sur deux niveaux, donc
     * {@code paymentIntent.installment.studentFee} serait {@code null} et la construction de la
     * requête échouerait sur un {@code NullPointerException}, sans que la compilation ne s'en plaigne.
     *
     * <p>{@code payer} doit être énuméré explicitement : la liste fournie ici <em>remplace</em>
     * l'initialisation par défaut au lieu de s'y ajouter, et l'omettre casse le filtre sur le
     * payeur — que le compilateur accepte pourtant sans broncher.
     */
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @QueryInit({"installment.studentFee.student", "payer"})
    @JoinColumn(name = "payment_intent_id", referencedColumnName = "id")
    private PaymentIntentEntity paymentIntent;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ReceiptEntity that = (ReceiptEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
