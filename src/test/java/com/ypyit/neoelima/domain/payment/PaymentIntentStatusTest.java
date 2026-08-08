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
import com.ypyit.neoelima.domain.payment.dto.PaymentIntentStatusDto;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.payment.service.OnlinePaymentService;
import com.ypyit.neoelima.domain.payment.service.ReceiptIssuer;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'état d'une tentative de paiement, tel que l'application le sonde.
 *
 * <p>C'est le seul endroit d'où elle peut apprendre qu'un règlement a réellement abouti. Elle
 * concluait jusqu'ici sur la redirection d'URL du tunnel : elle annonçait « paiement réussi » alors
 * que la tranche était encore due, et si le webhook n'arrivait jamais, elle le disait durablement.
 */
@Transactional
class PaymentIntentStatusTest extends AbstractIntegrationTest {

    @Autowired
    private OnlinePaymentService onlinePaymentService;
    @Autowired
    private ReceiptIssuer receiptIssuer;
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
    private UserRepository userRepository;

    private EstablishmentEntity establishment;
    private InstallmentEntity installment;
    private UserEntity payer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.establishment = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("Institut " + UUID.randomUUID()).active(true).build());
        this.installment = this.anInstallment(new BigDecimal("150000"));
        this.payer = this.aParent("Didier");
        this.authenticateAs(this.payer);
    }

    @Test
    @DisplayName("une tentative fraîchement créée est en attente")
    void startsPending() {
        PaymentIntentEntity intent = this.anIntent(this.payer, PaymentIntentStatus.PENDING);

        PaymentIntentStatusDto status = this.onlinePaymentService.statusOf(intent.getId());

        assertThat(status.getStatus()).isEqualTo(PaymentIntentStatus.PENDING);
        assertThat(status.getInstallmentId()).isEqualTo(this.installment.getId());
        assertThat(status.getTotalAmount()).isEqualByComparingTo("153750");
        // Pas encore de reçu : l'écran de succès ne doit rien proposer d'ouvrir.
        assertThat(status.getReceiptId()).isNull();
    }

    @Test
    @DisplayName("une fois le reçu émis, l'état porte le reçu")
    void carriesTheReceiptOnceIssued() {
        PaymentIntentEntity intent = this.anIntent(this.payer, PaymentIntentStatus.SUCCEEDED);
        ReceiptEntity receipt = this.receiptIssuer.issueFor(intent);

        PaymentIntentStatusDto status = this.onlinePaymentService.statusOf(intent.getId());

        // C'est ce qui permet à l'écran de succès d'offrir « Voir le reçu » sans second appel.
        assertThat(status.getStatus()).isEqualTo(PaymentIntentStatus.SUCCEEDED);
        assertThat(status.getReceiptId()).isEqualTo(receipt.getId());
    }

    @Test
    @DisplayName("un tiers ne peut pas suivre le paiement d'un autre")
    void refusesAnyoneButThePayer() {
        PaymentIntentEntity intent = this.anIntent(this.payer, PaymentIntentStatus.PENDING);

        this.authenticateAs(this.aParent("Awa"));

        // N'importe quel compte authentifié peut régler pour un élève — mais suivre une tentative
        // qu'on n'a pas lancée laisserait observer les règlements d'une famille en énumérant des
        // identifiants.
        assertThatThrownBy(() -> this.onlinePaymentService.statusOf(intent.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("une tentative sans payeur n'est suivie par personne")
    void refusesAnOrphanIntent() {
        PaymentIntentEntity intent = this.anIntent(null, PaymentIntentStatus.PENDING);

        // Cas d'un encaissement au guichet, qui n'a pas de payeur en compte : il n'y a personne à
        // qui rendre cet état, et surtout pas au premier venu.
        assertThatThrownBy(() -> this.onlinePaymentService.statusOf(intent.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    /* ---------- fabriques ---------- */

    private PaymentIntentEntity anIntent(UserEntity payer, PaymentIntentStatus status) {
        return this.paymentIntentRepository.saveAndFlush(PaymentIntentEntity.builder()
                .installment(this.installment)
                .payer(payer)
                .amountSchool(new BigDecimal("150000"))
                .amountCommission(new BigDecimal("3750"))
                .currency("XOF")
                .channel(PaymentChannel.ONLINE)
                .paymentMethod("orange")
                .status(status)
                .build());
    }

    private InstallmentEntity anInstallment(BigDecimal amount) {
        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Marie").lastName("Kouassi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 9, 12))
                .establishment(this.establishment).build());
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(amount)
                .establishment(this.establishment).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(student).fee(fee).build());
        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Scolarité de Février").amount(amount)
                .dueDate(LocalDate.now().plusDays(1))
                .status(InstallmentStatus.PENDING).build());
    }

    private UserEntity aParent(String firstName) {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        return this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName(firstName).lastName("Kouassi")
                .contacts(new HashSet<>(List.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
    }

    private void authenticateAs(UserEntity user) {
        String email = user.getContacts().stream()
                .filter(ContactEntity::isPrimary)
                .map(ContactEntity::getValue)
                .findFirst().orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
