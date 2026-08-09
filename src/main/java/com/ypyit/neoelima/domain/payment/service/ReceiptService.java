package com.ypyit.neoelima.domain.payment.service;

import com.querydsl.core.BooleanBuilder;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.QReceiptEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.repository.ReceiptRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Consultation des reçus.
 *
 * <p>Lecture seule : un reçu est une pièce comptable, il ne se modifie ni ne se supprime. Une
 * correction passe par une écriture d'annulation, prévue après la V1.
 *
 * <p>Deux profils de lecture. Une école voit les reçus qu'elle a émis. Un parent voit ceux de ses
 * enfants et ceux qu'il a lui-même réglés — les deux, car le tutorat et le paiement sont découplés :
 * un oncle qui règle une tranche doit retrouver sa pièce sans être tuteur déclaré.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserProvider currentUserProvider;

    public Page<ReceiptEntity> search(UUID requestedEstablishmentId, Pageable pageable) {
        return this.search(requestedEstablishmentId, null, null, null, null, null, pageable);
    }

    /**
     * Reçus visibles par l'appelant, éventuellement bornés dans le temps.
     *
     * <p>Les bornes servent le journal de caisse : l'école clôture sa journée sur les reçus émis
     * entre deux instants. Filtrer côté client aurait supposé que la journée tienne dans la page
     * demandée, ce qui cesse d'être vrai dès qu'un établissement encaisse normalement.
     */
    public Page<ReceiptEntity> search(UUID requestedEstablishmentId, Instant issuedFrom,
                                      Instant issuedTo, PaymentChannel channel, String keyword,
                                      UUID studentId, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;

        if (Objects.nonNull(issuedFrom)) {
            builder.and(receipt.issuedAt.goe(issuedFrom));
        }
        if (Objects.nonNull(issuedTo)) {
            builder.and(receipt.issuedAt.lt(issuedTo));
        }
        if (Objects.nonNull(channel)) {
            builder.and(receipt.channel.eq(channel));
        }
        // Filtre sur l'élève de la tranche réglée, et non sur le libellé recopié : deux homonymes
        // dans une même école rendraient la fiche de l'un truffée des reçus de l'autre.
        if (Objects.nonNull(studentId)) {
            builder.and(receipt.paymentIntent.installment.studentFee.student.id.eq(studentId));
        }
        // Le tri est fait par le serveur pour la même raison que les bornes : chercher dans la
        // page affichée aurait rendu « aucun résultat » sur un reçu qui existe deux pages plus loin.
        if (Objects.nonNull(keyword) && !keyword.isBlank()) {
            String term = keyword.trim();
            builder.and(receipt.number.containsIgnoreCase(term)
                    .or(receipt.studentLabel.containsIgnoreCase(term))
                    .or(receipt.studentRegistrationNumber.containsIgnoreCase(term))
                    .or(receipt.payerLabel.containsIgnoreCase(term)));
        }

        if (this.currentUserProvider.hasEstablishmentScope()) {
            UUID scope = this.currentUserProvider.resolveEstablishmentScope(requestedEstablishmentId);
            if (Objects.nonNull(scope)) {
                builder.and(receipt.establishment.id.eq(scope));
            }
        } else {
            builder.and(this.ownReceiptsOf(this.currentUserProvider.currentUser(), receipt));
        }
        return this.receiptRepository.findAll(builder, pageable);
    }

    /**
     * Reçus d'un établissement (ou de tout le parc), pour la console opérateur YPYit.
     *
     * <p>Volontairement distinct de {@link #search} : ici pas de portée d'appelant à résoudre — un
     * opérateur n'a pas d'établissement propre, et son droit de tout voir est garanti en amont par
     * le filtre de sécurité ({@code PLATFORM_CONSOLE_RESOURCES}). On borne seulement à
     * {@code establishmentId} quand il est fourni. Le payeur ({@code payerLabel}) est déjà porté par
     * chaque reçu.
     */
    public Page<ReceiptEntity> searchForPlatform(UUID establishmentId, Instant issuedFrom,
                                                 Instant issuedTo, PaymentChannel channel,
                                                 String keyword, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        if (Objects.nonNull(issuedFrom)) {
            builder.and(receipt.issuedAt.goe(issuedFrom));
        }
        if (Objects.nonNull(issuedTo)) {
            builder.and(receipt.issuedAt.lt(issuedTo));
        }
        if (Objects.nonNull(channel)) {
            builder.and(receipt.channel.eq(channel));
        }
        if (Objects.nonNull(keyword) && !keyword.isBlank()) {
            String term = keyword.trim();
            builder.and(receipt.number.containsIgnoreCase(term)
                    .or(receipt.studentLabel.containsIgnoreCase(term))
                    .or(receipt.studentRegistrationNumber.containsIgnoreCase(term))
                    .or(receipt.payerLabel.containsIgnoreCase(term)));
        }
        if (Objects.nonNull(establishmentId)) {
            builder.and(receipt.establishment.id.eq(establishmentId));
        }
        return this.receiptRepository.findAll(builder, pageable);
    }

    public ReceiptEntity findById(UUID id) {
        ReceiptEntity receipt = this.receiptRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Receipt with provided id %s not found", id)));

        // Le payeur retrouve toujours sa pièce, même s'il n'est pas tuteur de l'élève.
        UserEntity caller = this.currentUserProvider.currentUser();
        UserEntity payer = receipt.getPaymentIntent().getPayer();
        if (Objects.nonNull(payer) && payer.getId().equals(caller.getId())) {
            return receipt;
        }

        // Sinon la règle est celle de l'élève concerné : admin YPYit, école de l'élève, ou tuteur
        // rattaché. Plus juste qu'une comparaison d'établissement, qui excluait tout parent.
        this.currentUserProvider.assertCanAccessStudent(studentOf(receipt));
        return receipt;
    }

    /**
     * Reçus des enfants rattachés à l'appelant, plus ceux qu'il a réglés lui-même.
     *
     * <p>Les identifiants des enfants sont résolus par une requête séparée plutôt que par une
     * jointure sur {@code parentUsers} : la table de rattachement est une association plusieurs à
     * plusieurs, et la joindre ici dupliquerait les lignes de reçus dans une requête paginée.
     */
    private BooleanBuilder ownReceiptsOf(UserEntity caller, QReceiptEntity receipt) {
        List<UUID> childrenIds = this.studentRepository.findByParentUsers_Id(caller.getId())
                .stream().map(StudentEntity::getId).toList();

        BooleanBuilder scope = new BooleanBuilder(
                receipt.paymentIntent.payer.id.eq(caller.getId()));
        if (!childrenIds.isEmpty()) {
            scope.or(receipt.paymentIntent.installment.studentFee.student.id.in(childrenIds));
        }
        return scope;
    }

    private static StudentEntity studentOf(ReceiptEntity receipt) {
        PaymentIntentEntity intent = receipt.getPaymentIntent();
        return intent.getInstallment().getStudentFee().getStudent();
    }
}
