package com.ypyit.neoelima.domain.payment.service;

import com.ypy.paygw.payswitch.api.Customer;
import com.ypy.paygw.payswitch.api.InitiatePaymentRequest;
import com.ypy.paygw.payswitch.api.InitiatePaymentResponse;
import com.ypy.paygw.payswitch.api.PaymentService;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.properties.BillingProperties;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.payment.dto.PaymentInitiationDto;
import com.ypyit.neoelima.domain.payment.dto.PaymentQuoteDto;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Initie le règlement en ligne d'une tranche.
 *
 * <p>Le paiement est découplé du tutorat : tout utilisateur authentifié peut régler pour un élève,
 * il n'a pas à en être le tuteur déclaré. C'est un choix produit — un oncle, un grand frère ou un
 * bienfaiteur doivent pouvoir payer une scolarité.
 *
 * <p>Le parent est débité de la tranche augmentée de la commission YPYit. Les deux parts sont
 * stockées séparément pour que l'école et YPYit soient réconciliables indépendamment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    /** Le franc CFA n'a pas de subdivision : les montants sont des entiers. */
    private static final int XOF_SCALE = 0;

    private final PaymentService paymentService;
    private final InstallmentRepository installmentRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final BillingProperties billingProperties;

    /**
     * Détail de ce que coûtera le règlement d'une tranche, sans rien engager.
     *
     * <p>Existe pour que l'application affiche la commission avant validation. Elle ne peut pas la
     * calculer elle-même : le taux est une donnée de configuration du serveur, et une duplication
     * côté client afficherait un total différent de celui débité au premier changement de taux.
     *
     * <p>Ouvert aux mêmes appelants que {@link #initiate(UUID, String)} — payer pour un élève ne
     * suppose pas d'en être le tuteur — et ne révèle qu'un montant, jamais l'identité de l'élève.
     */
    @Transactional(readOnly = true)
    public PaymentQuoteDto quote(UUID installmentId) {
        InstallmentEntity installment = this.payableInstallment(installmentId);
        BigDecimal amountSchool = this.amountSchoolOf(installment);
        BigDecimal commission = this.commissionOn(amountSchool);

        return PaymentQuoteDto.builder()
                .amountSchool(amountSchool)
                .amountCommission(commission)
                .totalAmount(amountSchool.add(commission))
                .currency(this.billingProperties.getCurrency())
                .build();
    }

    @Transactional
    public PaymentInitiationDto initiate(UUID installmentId, String paymentMethod) {
        InstallmentEntity installment = this.payableInstallment(installmentId);

        UserEntity payer = this.currentUserProvider.currentUser();
        StudentEntity student = installment.getStudentFee().getStudent();
        EstablishmentEntity establishment = student.getEstablishment();

        BigDecimal amountSchool = this.amountSchoolOf(installment);
        BigDecimal commission = this.commissionOn(amountSchool);

        PaymentIntentEntity intent = this.paymentIntentRepository.saveAndFlush(
                PaymentIntentEntity.builder()
                        .installment(installment)
                        .payer(payer)
                        .amountSchool(amountSchool)
                        .amountCommission(commission)
                        .currency(this.billingProperties.getCurrency())
                        .channel(PaymentChannel.ONLINE)
                        .status(PaymentIntentStatus.PENDING)
                        .build());

        InitiatePaymentResponse response = this.paymentService.initiate(new InitiatePaymentRequest(
                intent.totalAmount(),
                this.billingProperties.getCurrency(),
                this.descriptionOf(installment, student),
                this.customerOf(payer),
                this.billingProperties.getPaymentSuccessUrl(),
                this.billingProperties.getPaymentErrorUrl(),
                // Canal choisi par le parent. Jeko en exige toujours un : à défaut, PaySwitch
                // retombe sur celui configuré par défaut sur le fournisseur actif.
                StringUtils.trimToNull(paymentMethod),
                this.metadataOf(intent, installment, student, establishment)));

        intent.setInternalReference(response.internalReference());
        intent.setCheckoutUrl(response.checkoutUrl());
        intent.setProviderType(Objects.isNull(response.providerType())
                ? null : response.providerType().name());
        this.paymentIntentRepository.saveAndFlush(intent);

        log.info("PAYMENT_INITIATED: intent {} reference {} for installment {} amount {} {}",
                intent.getId(), response.internalReference(), installmentId,
                intent.totalAmount(), this.billingProperties.getCurrency());

        return PaymentInitiationDto.builder()
                .paymentIntentId(intent.getId())
                .internalReference(response.internalReference())
                .checkoutUrl(response.checkoutUrl())
                .amountSchool(amountSchool)
                .amountCommission(commission)
                .totalAmount(intent.totalAmount())
                .currency(this.billingProperties.getCurrency())
                .build();
    }

    /** Tranche existante et encore due. Refuse une tranche déjà soldée avant tout calcul. */
    private InstallmentEntity payableInstallment(UUID installmentId) {
        InstallmentEntity installment = this.installmentRepository.findById(installmentId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Installment with provided id %s not found", installmentId)));

        if (!InstallmentStatus.PENDING.equals(installment.getStatus())) {
            throw new BadRequestException(String.format(
                    "Installment %s is already %s", installmentId, installment.getStatus()));
        }
        return installment;
    }

    private BigDecimal amountSchoolOf(InstallmentEntity installment) {
        return installment.getAmount().setScale(XOF_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Commission arrondie à l'entier : le franc CFA n'a pas de centime, et une transaction portant
     * des décimales serait rejetée ou tronquée par l'agrégateur.
     */
    private BigDecimal commissionOn(BigDecimal amountSchool) {
        return amountSchool
                .multiply(this.billingProperties.getCommissionRate())
                .setScale(XOF_SCALE, RoundingMode.HALF_UP);
    }

    private String descriptionOf(InstallmentEntity installment, StudentEntity student) {
        return String.format("%s - %s %s (%s)",
                Objects.toString(installment.getLabel(), "Frais de scolarité"),
                Objects.toString(student.getFirstName(), ""),
                Objects.toString(student.getLastName(), ""),
                Objects.toString(student.getRegistrationNumber(), ""));
    }

    private Customer customerOf(UserEntity payer) {
        return new Customer(
                String.join(" ", Objects.toString(payer.getFirstName(), ""),
                        Objects.toString(payer.getLastName(), "")).trim(),
                this.contactValue(payer, ContactType.EMAIL).orElse(null),
                this.contactValue(payer, ContactType.PHONE_NUMBER).orElse(null),
                "CI");
    }

    private Optional<String> contactValue(UserEntity user, ContactType type) {
        return user.getContacts().stream()
                .filter(contact -> type.equals(contact.getType()))
                .map(ContactEntity::getValue)
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * Ces métadonnées reviennent telles quelles dans le webhook. Elles portent tout ce qu'il faut
     * pour solder la tranche sans dépendre d'une autre source, la référence interne restant le
     * pivot du rapprochement.
     */
    private Map<String, String> metadataOf(PaymentIntentEntity intent,
                                           InstallmentEntity installment,
                                           StudentEntity student,
                                           EstablishmentEntity establishment) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("payment_intent_id", intent.getId().toString());
        metadata.put("installment_id", installment.getId().toString());
        metadata.put("student_id", student.getId().toString());
        if (Objects.nonNull(establishment)) {
            metadata.put("establishment_id", establishment.getId().toString());
        }
        if (Objects.nonNull(intent.getPayer())) {
            metadata.put("payer_user_id", intent.getPayer().getId().toString());
        }
        return metadata;
    }
}
