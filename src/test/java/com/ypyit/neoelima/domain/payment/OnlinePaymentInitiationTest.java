package com.ypyit.neoelima.domain.payment;

import com.ypy.paygw.payswitch.api.InitiatePaymentResponse;
import com.ypy.paygw.payswitch.api.PaymentService;
import com.ypy.paygw.payswitch.api.PaymentStatus;
import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
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
import com.ypyit.neoelima.domain.payment.service.OnlinePaymentService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * L'ouverture d'un paiement en ligne.
 *
 * <p>C'est l'entrée du tunnel qui débite réellement, et elle n'avait <strong>aucun test</strong> :
 * la couverture s'arrêtait au devis ({@code quote}) et à la relecture du statut ({@code statusOf}).
 * Entre les deux, la méthode qui crée l'intention, calcule la commission et appelle l'agrégateur
 * n'était exercée par rien.
 *
 * <p>L'agrégateur est bouchonné : ce qui est éprouvé ici est ce que Nelima décide — le montant, la
 * commission, le refus d'une tranche déjà réglée — et non ce que PaySwitch en fait.
 */
@Transactional
class OnlinePaymentInitiationTest extends AbstractIntegrationTest {

    @Autowired
    private OnlinePaymentService onlinePaymentService;
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

    @MockitoBean
    private PaymentService paymentService;

    private String parentEmail;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(paymentService.initiate(any())).thenReturn(new InitiatePaymentResponse(
                "PSW-TEST-" + UUID.randomUUID().toString().substring(0, 8),
                "PROV-REF",
                "https://checkout.example/stub",
                PaymentStatus.PENDING,
                null));
    }

    @Test
    @DisplayName("l'intention porte le montant de la tranche et la commission de la plateforme")
    void createsPendingIntentWithCommission() {
        InstallmentEntity installment = aPendingInstallment(new BigDecimal("50000"));
        authenticateAsParent();

        onlinePaymentService.initiate(installment.getId(), "WAVE");

        PaymentIntentEntity intent = paymentIntentRepository.findAll().stream()
                .filter(i -> i.getInstallment().getId().equals(installment.getId()))
                .findFirst().orElseThrow();

        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.PENDING);
        assertThat(intent.getChannel()).isEqualTo(PaymentChannel.ONLINE);
        assertThat(intent.getAmountSchool()).isEqualByComparingTo("50000");
        assertThat(intent.getAmountCommission())
                .as("la commission est celle de la plateforme, pas un taux par opérateur")
                .isEqualByComparingTo("1000");
        assertThat(intent.getPaymentMethod())
                .as("l'opérateur choisi est conservé : c'est ce que la famille reconnaîtra sur son reçu")
                .isEqualTo("WAVE");
        assertThat(intent.getInternalReference()).isNotBlank();
        assertThat(intent.getCheckoutUrl()).isNotBlank();
    }

    @Test
    @DisplayName("la commission s'arrondit au franc : le CFA n'a pas de centime")
    void roundsCommissionToWholeFranc() {
        // 25 × 2 % = 0,5. Un montant à décimale serait rejeté ou tronqué par l'agrégateur ; la règle
        // est d'arrondir au supérieur, et c'est ce qu'on fige ici plutôt que de le déduire du code.
        InstallmentEntity installment = aPendingInstallment(new BigDecimal("25"));
        authenticateAsParent();

        onlinePaymentService.initiate(installment.getId(), "WAVE");

        PaymentIntentEntity intent = paymentIntentRepository.findAll().stream()
                .filter(i -> i.getInstallment().getId().equals(installment.getId()))
                .findFirst().orElseThrow();
        assertThat(intent.getAmountCommission()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("une tranche déjà réglée ne peut pas être payée une seconde fois")
    void refusesAnInstallmentThatIsNoLongerPending() {
        InstallmentEntity installment = aPendingInstallment(new BigDecimal("50000"));
        installment.setStatus(InstallmentStatus.PAID);
        installmentRepository.saveAndFlush(installment);
        authenticateAsParent();

        // Le garde-fou du double encaissement : une tranche soldée au guichet pendant que le parent
        // hésitait ne doit pas pouvoir repartir dans le tunnel.
        assertThatThrownBy(() -> onlinePaymentService.initiate(installment.getId(), "WAVE"))
                .isInstanceOf(BadRequestException.class);

        assertThat(paymentIntentRepository.findAll())
                .as("aucune intention ne doit être créée sur une tranche close")
                .noneMatch(i -> i.getInstallment().getId().equals(installment.getId()));
    }

    private void authenticateAsParent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(parentEmail, "n/a", List.of()));
    }

    private InstallmentEntity aPendingInstallment(BigDecimal amount) {
        EstablishmentEntity school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École en ligne " + UUID.randomUUID()).active(true).build());

        parentEmail = "parent-" + UUID.randomUUID() + "@email.com";
        StudentParentUserEntity parent = userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Aya").lastName("Traoré")
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(parentEmail).isPrimary(true).build())))
                .build());

        StudentEntity student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3))
                .establishment(school)
                .parentUsers(new HashSet<>(Set.of(parent)))
                .build());

        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(amount)
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());

        return installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement").amount(amount)
                .status(InstallmentStatus.PENDING).build());
    }
}
