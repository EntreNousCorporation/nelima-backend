package com.ypyit.neoelima.domain.payment;

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
import com.ypyit.neoelima.domain.payment.dto.TransactionDto;
import com.ypyit.neoelima.domain.payment.dto.TransactionSummaryDto;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.payment.repository.ReceiptRepository;
import com.ypyit.neoelima.domain.payment.service.TransactionService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rapprochement des encaissements.
 *
 * <p>C'est l'écran où une école constate qu'il manque une pièce. Deux erreurs y seraient graves :
 * annoncer un encaissement que l'agrégateur n'a pas confirmé, et taire une opération réussie dont
 * aucun reçu n'a été émis — celle-là même qu'on vient chercher.
 */
@Transactional
class TransactionServiceTest extends AbstractIntegrationTest {

    private static final LocalDate FROM = LocalDate.now().minusDays(7);
    private static final LocalDate TO = LocalDate.now().plusDays(1);

    @Autowired
    private TransactionService transactionService;
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
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.school();
        this.authenticateOn(this.school);
    }

    @Test
    @DisplayName("une opération réussie sans reçu est à réconcilier")
    void aSucceededIntentWithoutReceiptIsToReconcile() {
        this.intent("PSW-0000000001", PaymentIntentStatus.SUCCEEDED, "20000", "400", false);

        TransactionSummaryDto summary = this.transactionService.summarize(FROM, TO);
        TransactionDto line = this.transactionService.findAll(FROM, TO).getFirst();

        assertThat(summary.getToReconcile()).isEqualTo(1);
        assertThat(line.isReconciled()).isFalse();
        assertThat(line.getReceiptNumber()).isNull();
    }

    @Test
    @DisplayName("la même opération, reçu émis, n'y est plus")
    void aReceiptClearsTheReconciliation() {
        this.intent("PSW-0000000002", PaymentIntentStatus.SUCCEEDED, "20000", "400", true);

        TransactionSummaryDto summary = this.transactionService.summarize(FROM, TO);
        TransactionDto line = this.transactionService.findAll(FROM, TO).getFirst();

        assertThat(summary.getToReconcile()).isZero();
        assertThat(line.isReconciled()).isTrue();
        assertThat(line.getReceiptNumber()).isNotNull();
    }

    @Test
    @DisplayName("le net et les frais sont repris de l'opération, jamais recalculés")
    void amountsComeFromTheIntent() {
        this.intent("PSW-0000000003", PaymentIntentStatus.SUCCEEDED, "30000", "600", false);

        TransactionDto line = this.transactionService.findAll(FROM, TO).getFirst();
        TransactionSummaryDto summary = this.transactionService.summarize(FROM, TO);

        // Les recomposer depuis un total et un taux ferait diverger l'écran de ce qui a été
        // prélevé le jour où le taux change.
        assertThat(line.getAmountSchool()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(line.getAmountCommission()).isEqualByComparingTo(new BigDecimal("600"));
        assertThat(summary.getCollectedNet()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(summary.getCommissionCollected()).isEqualByComparingTo(new BigDecimal("600"));
    }

    @Test
    @DisplayName("une opération en attente ne compte pas dans l'encaissé")
    void pendingIntentsAreNotCounted() {
        this.intent("PSW-0000000004", PaymentIntentStatus.PENDING, "50000", "1000", false);

        TransactionSummaryDto summary = this.transactionService.summarize(FROM, TO);

        // Annoncer un encaissement que l'agrégateur n'a pas confirmé ferait un chiffre d'affaires
        // imaginaire, et l'école le découvrirait en rapprochant sa banque.
        assertThat(summary.getCollectedNet()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getAwaitingProvider()).isEqualTo(1);
        assertThat(summary.getToReconcile()).isZero();
    }

    @Test
    @DisplayName("les opérations d'une autre école sont invisibles")
    void doesNotLeakOtherSchools() {
        this.intent("PSW-0000000005", PaymentIntentStatus.SUCCEEDED, "10000", "200", true);

        this.school = this.school();
        this.authenticateOn(this.school);

        assertThat(this.transactionService.findAll(FROM, TO)).isEmpty();
        assertThat(this.transactionService.summarize(FROM, TO).getTransactionCount()).isZero();
    }

    /* ---------- fabriques ---------- */

    private void intent(String reference, PaymentIntentStatus status, String net, String commission,
                        boolean withReceipt) {
        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName("KOUAMÉ")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 5, 14)).establishment(this.school).build());
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID().toString().substring(0, 4))
                .price(new BigDecimal(net)).establishment(this.school).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(student).fee(fee).name(fee.getName()).build());
        InstallmentEntity installment = this.installmentRepository.saveAndFlush(
                InstallmentEntity.builder().studentFee(studentFee).label("1er versement")
                        .amount(new BigDecimal(net)).dueDate(LocalDate.now())
                        .status(InstallmentStatus.PENDING).build());

        PaymentIntentEntity intent = this.paymentIntentRepository.saveAndFlush(
                PaymentIntentEntity.builder()
                        .internalReference(reference)
                        .amountSchool(new BigDecimal(net))
                        .amountCommission(new BigDecimal(commission))
                        .currency("XOF")
                        .status(status)
                        .channel(PaymentChannel.ONLINE)
                        .providerType("JEKO")
                        .installment(installment)
                        .payerName("SORO Bakary")
                        .build());

        if (withReceipt) {
            this.receiptRepository.saveAndFlush(ReceiptEntity.builder()
                    .sequenceNumber(System.nanoTime())
                    .number("REC-" + UUID.randomUUID().toString().substring(0, 8))
                    .amount(new BigDecimal(net))
                    .issuedAt(Instant.now())
                    .channel(PaymentChannel.ONLINE)
                    .establishment(this.school)
                    .paymentIntent(intent)
                    .build());
        }
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private void authenticateOn(EstablishmentEntity establishment) {
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(establishment)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
