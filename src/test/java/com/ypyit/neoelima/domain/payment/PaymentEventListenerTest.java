package com.ypyit.neoelima.domain.payment;

import com.ypy.paygw.payswitch.api.PaymentStatus;
import com.ypy.paygw.payswitch.api.ProviderType;
import com.ypy.paygw.payswitch.api.UnifiedTransaction;
import com.ypy.paygw.payswitch.api.event.PaymentFailedEvent;
import com.ypy.paygw.payswitch.api.event.PaymentSucceededEvent;
import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.payment.repository.ReceiptRepository;
import com.ypyit.neoelima.domain.payment.service.PaymentEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotence du traitement des notifications d'encaissement.
 *
 * <p>C'est la garantie centrale du paiement en ligne : Jeko réessaie trois fois sur une vingtaine
 * de minutes dès qu'il n'obtient pas un 200 franc, et PaySwitch republie l'événement à chaque
 * réception. Un traitement naïf solderait deux fois la tranche et émettrait deux reçus.
 */
@Transactional
class PaymentEventListenerTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentEventListener listener;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private StudentFeeRepository studentFeeRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private PaymentIntentRepository paymentIntentRepository;
    @Autowired
    private ReceiptRepository receiptRepository;

    private InstallmentEntity installment;
    private PaymentIntentEntity intent;
    private String reference;

    @BeforeEach
    void setUp() {
        EstablishmentEntity school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École en ligne " + UUID.randomUUID()).active(true).build());
        StudentEntity student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("50000"))
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        installment = installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement").amount(new BigDecimal("50000"))
                .dueDate(LocalDate.of(2026, 10, 15)).status(InstallmentStatus.PENDING).build());

        reference = "PSW-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        // Commission 2 % : 50 000 pour l'école, 1 000 pour YPYit, 51 000 débités au parent.
        intent = paymentIntentRepository.saveAndFlush(PaymentIntentEntity.builder()
                .installment(installment)
                .amountSchool(new BigDecimal("50000"))
                .amountCommission(new BigDecimal("1000"))
                .channel(PaymentChannel.ONLINE)
                .status(PaymentIntentStatus.PENDING)
                .internalReference(reference)
                .build());
    }

    @Test
    @DisplayName("un encaissement confirmé solde la tranche et émet un reçu")
    void successSettlesInstallmentAndIssuesReceipt() {
        listener.on(new PaymentSucceededEvent(transaction(PaymentStatus.COMPLETED)));

        assertThat(installmentRepository.findById(installment.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentStatus.PAID);
        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntentStatus.SUCCEEDED);
        assertThat(receiptRepository.findByPaymentIntent_Id(intent.getId()))
                .as("l'encaissement doit être quittancé")
                .isPresent();
    }

    @Test
    @DisplayName("le rejeu du même webhook ne produit ni double reçu ni double encaissement")
    void replayedWebhookIsIgnored() {
        listener.on(new PaymentSucceededEvent(transaction(PaymentStatus.COMPLETED)));
        var firstReceipt = receiptRepository.findByPaymentIntent_Id(intent.getId()).orElseThrow();

        // Jeko réessaie : trois notifications supplémentaires pour le même encaissement.
        listener.on(new PaymentSucceededEvent(transaction(PaymentStatus.COMPLETED)));
        listener.on(new PaymentSucceededEvent(transaction(PaymentStatus.COMPLETED)));
        listener.on(new PaymentSucceededEvent(transaction(PaymentStatus.COMPLETED)));

        assertThat(receiptRepository.findAll().stream()
                .filter(r -> r.getPaymentIntent().getId().equals(intent.getId())).toList())
                .as("un encaissement, un seul reçu")
                .hasSize(1);
        assertThat(receiptRepository.findByPaymentIntent_Id(intent.getId()).orElseThrow().getNumber())
                .as("le numéro de reçu ne doit pas changer au rejeu")
                .isEqualTo(firstReceipt.getNumber());
    }

    @Test
    @DisplayName("un échec laisse la tranche due, le parent doit pouvoir réessayer")
    void failureLeavesInstallmentPayable() {
        listener.on(new PaymentFailedEvent(transaction(PaymentStatus.FAILED)));

        assertThat(installmentRepository.findById(installment.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentStatus.PENDING);
        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntentStatus.FAILED);
        assertThat(receiptRepository.findByPaymentIntent_Id(intent.getId())).isEmpty();
    }

    @Test
    @DisplayName("une tranche déjà encaissée au guichet n'est pas réécrite par un paiement en ligne")
    void doesNotOverwriteAnInstallmentSettledAtTheDesk() {
        installment.setStatus(InstallmentStatus.PAID);
        installmentRepository.saveAndFlush(installment);

        listener.on(new PaymentSucceededEvent(transaction(PaymentStatus.COMPLETED)));

        assertThat(receiptRepository.findByPaymentIntent_Id(intent.getId()))
                .as("le doublon est à rembourser, pas à quittancer")
                .isEmpty();
        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getStatus())
                .as("l'encaissement a bien eu lieu chez l'agrégateur, il reste tracé")
                .isEqualTo(PaymentIntentStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("une transaction inconnue est tracée sans faire échouer le webhook")
    void unknownTransactionIsIgnored() {
        UnifiedTransaction foreign = new UnifiedTransaction(
                UUID.randomUUID(), "PSW-UNKNOWN01", "prov-ref", ProviderType.JEKO,
                new BigDecimal("51000"), "XOF", PaymentStatus.COMPLETED, "test",
                null, Map.of(), null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());

        listener.on(new PaymentSucceededEvent(foreign));

        assertThat(installmentRepository.findById(installment.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentStatus.PENDING);
    }

    private UnifiedTransaction transaction(PaymentStatus status) {
        return new UnifiedTransaction(
                UUID.randomUUID(), reference, "jeko-ref-1", ProviderType.JEKO,
                new BigDecimal("51000"), "XOF", status, "Scolarité Aaron Koffi",
                null, Map.of("installment_id", installment.getId().toString()),
                null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }
}
