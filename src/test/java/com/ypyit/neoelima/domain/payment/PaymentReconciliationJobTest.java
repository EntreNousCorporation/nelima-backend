package com.ypyit.neoelima.domain.payment;

import com.ypy.paygw.payswitch.api.PaymentService;
import com.ypy.paygw.payswitch.api.PaymentStatus;
import com.ypy.paygw.payswitch.api.ProviderType;
import com.ypy.paygw.payswitch.api.UnifiedTransaction;
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
import com.ypyit.neoelima.domain.payment.service.PaymentReconciliationJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Rattrapage des webhooks perdus.
 *
 * <p>Sans ce filet, un parent débité dont la notification s'est perdue verrait sa tranche rester
 * affichée comme due, et l'école n'aurait jamais vu l'encaissement.
 */
@Transactional
class PaymentReconciliationJobTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentReconciliationJob job;
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

    @MockitoBean
    private PaymentService paymentService;

    @Test
    @DisplayName("un encaissement réel dont le webhook s'est perdu est rattrapé")
    void settlesPaymentWhoseWebhookWasLost() {
        PaymentIntentEntity intent = aPendingIntentCreated(Duration.ofMinutes(30));
        when(paymentService.get(anyString()))
                .thenReturn(transaction(intent.getInternalReference(), PaymentStatus.COMPLETED));

        job.reconcilePendingPayments();

        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntentStatus.SUCCEEDED);
        assertThat(installmentRepository.findById(intent.getInstallment().getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentStatus.PAID);
        assertThat(receiptRepository.findByPaymentIntent_Id(intent.getId()))
                .as("le rattrapage doit quittancer comme l'aurait fait le webhook")
                .isPresent();
    }

    @Test
    @DisplayName("une tentative récente est laissée tranquille, le parent est peut-être encore dans le tunnel")
    void leavesRecentAttemptsAlone() {
        PaymentIntentEntity intent = aPendingIntentCreated(Duration.ofMinutes(2));
        when(paymentService.get(anyString()))
                .thenReturn(transaction(intent.getInternalReference(), PaymentStatus.COMPLETED));

        job.reconcilePendingPayments();

        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntentStatus.PENDING);
        assertThat(receiptRepository.findByPaymentIntent_Id(intent.getId())).isEmpty();
    }

    @Test
    @DisplayName("un abandon chez l'agrégateur clôt la tentative sans toucher à la tranche")
    void closesExpiredAttemptWithoutSettlingInstallment() {
        PaymentIntentEntity intent = aPendingIntentCreated(Duration.ofMinutes(30));
        when(paymentService.get(anyString()))
                .thenReturn(transaction(intent.getInternalReference(), PaymentStatus.EXPIRED));

        job.reconcilePendingPayments();

        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntentStatus.CANCELLED);
        assertThat(installmentRepository.findById(intent.getInstallment().getId()).orElseThrow().getStatus())
                .as("la tranche reste due, le parent doit pouvoir réessayer")
                .isEqualTo(InstallmentStatus.PENDING);
    }

    @Test
    @DisplayName("une transaction toujours en cours chez l'agrégateur n'est pas tranchée d'office")
    void doesNotDecideOnStillProcessingTransactions() {
        PaymentIntentEntity intent = aPendingIntentCreated(Duration.ofMinutes(30));
        when(paymentService.get(anyString()))
                .thenReturn(transaction(intent.getInternalReference(), PaymentStatus.PROCESSING));

        job.reconcilePendingPayments();

        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntentStatus.PENDING);
    }

    private PaymentIntentEntity aPendingIntentCreated(Duration age) {
        EstablishmentEntity school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École réconciliation " + UUID.randomUUID()).active(true).build());
        StudentEntity student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("50000"))
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        InstallmentEntity installment = installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement").amount(new BigDecimal("50000"))
                .status(InstallmentStatus.PENDING).build());

        PaymentIntentEntity intent = paymentIntentRepository.saveAndFlush(PaymentIntentEntity.builder()
                .installment(installment)
                .amountSchool(new BigDecimal("50000"))
                .amountCommission(new BigDecimal("1000"))
                .channel(PaymentChannel.ONLINE)
                .status(PaymentIntentStatus.PENDING)
                .internalReference("PSW-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase())
                .build());

        // @CreatedDate n'est posée qu'à l'insertion : on peut vieillir la ligne après coup pour
        // simuler une tentative laissée en attente.
        intent.setCreatedAt(Instant.now().minus(age));
        return paymentIntentRepository.saveAndFlush(intent);
    }

    private UnifiedTransaction transaction(String reference, PaymentStatus status) {
        return new UnifiedTransaction(
                UUID.randomUUID(), reference, "jeko-ref", ProviderType.JEKO,
                new BigDecimal("51000"), "XOF", status, "Scolarité",
                null, Map.of(), null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }
}
