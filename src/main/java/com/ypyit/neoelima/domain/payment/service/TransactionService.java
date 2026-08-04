package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.payment.dto.TransactionDto;
import com.ypyit.neoelima.domain.payment.dto.TransactionSummaryDto;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.payment.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rapprochement : ce que l'agrégateur a encaissé face à ce que Nelima a émis.
 *
 * <p>Tout se lit depuis {@link PaymentIntentEntity}, jamais des tables de l'agrégateur. Celles-ci
 * ne portent aucune colonne d'établissement : les interroger obligerait à deviner l'école depuis
 * une charge utile libre, et une erreur de lecture montrerait à une école les encaissements d'une
 * autre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    /** Fuseau de l'école : une journée d'encaissement commence à minuit à Abidjan. */
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Abidjan");

    private final PaymentIntentRepository paymentIntentRepository;
    private final ReceiptRepository receiptRepository;
    private final CurrentUserProvider currentUserProvider;

    public List<TransactionDto> findAll(LocalDate from, LocalDate to) {
        List<PaymentIntentEntity> intents = this.load(from, to);
        Map<UUID, ReceiptEntity> receipts = this.receiptsOf(intents);
        return intents.stream().map(intent -> toDto(intent, receipts)).toList();
    }

    public TransactionSummaryDto summarize(LocalDate from, LocalDate to) {
        List<PaymentIntentEntity> intents = this.load(from, to);

        Map<UUID, ReceiptEntity> receipts = this.receiptsOf(intents);
        List<PaymentIntentEntity> succeeded = intents.stream()
                .filter(intent -> PaymentIntentStatus.SUCCEEDED.equals(intent.getStatus()))
                .toList();

        return TransactionSummaryDto.builder()
                // Le net exclut les tentatives échouées ou en attente : annoncer un encaissement
                // que l'agrégateur n'a pas confirmé ferait un chiffre d'affaires imaginaire.
                .collectedNet(sum(succeeded, PaymentIntentEntity::getAmountSchool))
                .commissionCollected(sum(succeeded, PaymentIntentEntity::getAmountCommission))
                .transactionCount(intents.size())
                .toReconcile(succeeded.stream().filter(intent -> !receipts.containsKey(intent.getId())).count())
                .awaitingProvider(intents.stream()
                        .filter(intent -> PaymentIntentStatus.PENDING.equals(intent.getStatus()))
                        .count())
                .build();
    }

    private List<PaymentIntentEntity> load(LocalDate from, LocalDate to) {
        UUID scope = this.scope();
        if (Objects.isNull(from) || Objects.isNull(to) || to.isBefore(from)) {
            throw new BadRequestException("La période demandée est invalide.");
        }
        Instant start = from.atStartOfDay(SCHOOL_ZONE).toInstant();
        // Borne haute au début du lendemain : s'arrêter à minuit du jour demandé écarterait toutes
        // les opérations de cette journée-là.
        Instant end = to.plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();

        return this.paymentIntentRepository
                .findByInstallment_StudentFee_Student_Establishment_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        scope, start, end);
    }

    private static TransactionDto toDto(PaymentIntentEntity intent, Map<UUID, ReceiptEntity> receipts) {
        InstallmentEntity installment = intent.getInstallment();
        StudentEntity student = installment.getStudentFee().getStudent();
        Optional<ReceiptEntity> receipt = Optional.ofNullable(receipts.get(intent.getId()));

        return TransactionDto.builder()
                .id(intent.getId().toString())
                .reference(intent.getInternalReference())
                .createdAt(intent.getCreatedAt())
                .settledAt(intent.getSettledAt())
                .status(intent.getStatus())
                .channel(intent.getChannel())
                .providerType(intent.getProviderType())
                .studentLabel(String.format("%s %s", student.getLastName(), student.getFirstName()))
                .studentRegistrationNumber(student.getRegistrationNumber())
                .installmentLabel(installment.getLabel())
                .payerName(intent.getPayerName())
                // Repris tels quels : les montants sont séparés dès l'initiation du paiement.
                .amountSchool(intent.getAmountSchool())
                .amountCommission(intent.getAmountCommission())
                .reconciled(receipt.isPresent())
                .receiptNumber(receipt.map(ReceiptEntity::getNumber).orElse(null))
                .build();
    }

    /**
     * Les reçus des tentatives données, en une requête.
     *
     * <p>Une requête par ligne coûterait deux allers-retours par transaction — la liste et le
     * résumé la posent chacun — sur un écran qui affiche un mois d'encaissements.
     */
    private Map<UUID, ReceiptEntity> receiptsOf(List<PaymentIntentEntity> intents) {
        if (intents.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = intents.stream().map(PaymentIntentEntity::getId).toList();
        return this.receiptRepository.findByPaymentIntent_IdIn(ids).stream()
                .collect(Collectors.toMap(receipt -> receipt.getPaymentIntent().getId(),
                        receipt -> receipt, (first, second) -> first));
    }

    private static BigDecimal sum(List<PaymentIntentEntity> intents,
                                  java.util.function.Function<PaymentIntentEntity, BigDecimal> field) {
        return intents.stream()
                .map(field)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
