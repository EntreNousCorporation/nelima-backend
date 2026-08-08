package com.ypyit.neoelima.domain.payment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.payment.dto.ReceiptDto;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.form.OfflineCollectionForm;
import com.ypyit.neoelima.domain.payment.mapper.ReceiptMapper;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.payment.service.OfflineCollectionService;
import com.ypyit.neoelima.domain.payment.service.ReceiptIssuer;
import com.ypyit.neoelima.domain.payment.service.ReceiptService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qu'un reçu conserve de l'encaissement.
 *
 * <p>Tout y est <strong>figé à l'émission</strong> : l'intitulé d'une tranche se corrige, un élève
 * change de classe à la rentrée, le taux de commission de la plateforme évolue. Un reçu qui relirait
 * ces valeurs cesserait d'attester ce qui s'est passé le jour du paiement — et personne ne s'en
 * apercevrait, puisqu'il continuerait d'afficher des chiffres plausibles.
 */
@Transactional
class ReceiptDetailsTest extends AbstractIntegrationTest {

    @Autowired
    private OfflineCollectionService offlineCollectionService;
    @Autowired
    private ReceiptIssuer receiptIssuer;
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private ReceiptMapper receiptMapper;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
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

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.establishment = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("Institut LKM " + UUID.randomUUID()).active(true).build());
        this.authenticateAsSchoolUser();
    }

    @Test
    @DisplayName("le reçu fige le motif, l'école et la ventilation du montant")
    void freezesWhatTheParentReads() {
        SchoolClassEntity classroom = this.classroom("CM2");
        InstallmentEntity installment = this.anInstallment("Scolarité de Février",
                new BigDecimal("50000"), classroom);

        ReceiptEntity receipt = this.offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CASH).build());

        assertThat(receipt.getFeeLabel()).isEqualTo("Scolarité de Février");
        assertThat(receipt.getEstablishmentName()).isEqualTo(this.establishment.getName());
        assertThat(receipt.getStudentClassName()).isEqualTo(classroom.getName());
        assertThat(receipt.getAmountSchool()).isEqualByComparingTo("50000");
        assertThat(receipt.getAmountCommission()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("corriger l'intitulé d'une tranche ne réécrit pas le reçu déjà émis")
    void doesNotFollowLaterCorrections() {
        InstallmentEntity installment = this.anInstallment("Scolarité de Février",
                new BigDecimal("50000"), this.classroom("CM2"));
        ReceiptEntity receipt = this.offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CASH).build());

        installment.setLabel("Scolarité de Mars — corrigé");
        this.installmentRepository.saveAndFlush(installment);

        // C'est tout l'intérêt de figer : une pièce comptable atteste d'un jour, elle ne suit pas
        // les corrections postérieures. La lire à travers la tranche l'aurait fait mentir.
        assertThat(this.receiptMapper.toDtos(
                this.receiptService.search(null, PageRequest.of(0, 20)).getContent()))
                .extracting(ReceiptDto::getFeeLabel)
                .containsExactly("Scolarité de Février");
        assertThat(receipt.getFeeLabel()).isEqualTo("Scolarité de Février");
    }

    @Test
    @DisplayName("un élève sans classe donne un reçu sans classe, pas un reçu manquant")
    void survivesAnUnassignedStudent() {
        InstallmentEntity installment = this.anInstallment("Cantine",
                new BigDecimal("25000"), null);

        ReceiptEntity receipt = this.offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CASH).build());

        // Une inscription en cours d'année attend souvent sa répartition. Faire échouer l'émission
        // du reçu pour autant serait refuser un encaissement pour un défaut de saisie de l'école.
        assertThat(receipt.getStudentClassName()).isNull();
        assertThat(receipt.getNumber()).isNotBlank();
        assertThat(receipt.getFeeLabel()).isEqualTo("Cantine");
    }

    @Test
    @DisplayName("un encaissement au guichet n'a pas d'opérateur")
    void carriesNoOperatorAtTheCounter() {
        InstallmentEntity installment = this.anInstallment("Scolarité",
                new BigDecimal("50000"), this.classroom("CM1"));

        ReceiptEntity receipt = this.offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CHECK).build());

        // C'est `channel` qui parle ici. Inventer un opérateur pour un chèque tromperait la famille.
        assertThat(receipt.getPaymentMethod()).isNull();
        assertThat(receipt.getChannel()).isEqualTo(PaymentChannel.CHECK);
    }

    @Test
    @DisplayName("l'opérateur choisi en ligne est recopié sur le reçu")
    void copiesTheChosenOperator() {
        InstallmentEntity installment = this.anInstallment("Scolarité de Février",
                new BigDecimal("100000"), this.classroom("6ème B"));
        PaymentIntentEntity intent = this.paymentIntentRepository.saveAndFlush(
                PaymentIntentEntity.builder()
                        .installment(installment)
                        .amountSchool(new BigDecimal("100000"))
                        .amountCommission(new BigDecimal("2500"))
                        .channel(PaymentChannel.ONLINE)
                        .paymentMethod("orange")
                        .status(PaymentIntentStatus.SUCCEEDED)
                        .build());

        ReceiptEntity receipt = this.receiptIssuer.issueFor(intent);

        assertThat(receipt.getPaymentMethod()).isEqualTo("orange");
        assertThat(receipt.getAmountSchool()).isEqualByComparingTo("100000");
        assertThat(receipt.getAmountCommission()).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("les nouveaux champs voyagent jusqu'au client")
    void reachesTheClient() {
        SchoolClassEntity classroom = this.classroom("CM2");
        InstallmentEntity installment = this.anInstallment("Scolarité de Février",
                new BigDecimal("50000"), classroom);
        this.offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CASH).build());

        ReceiptDto dto = this.receiptMapper.toDtos(
                this.receiptService.search(null, PageRequest.of(0, 20)).getContent()).getFirst();

        // Le mobile lisait déjà un nom d'école que le serveur n'envoyait pas : il valait donc
        // toujours nul, sans que rien ne le signale.
        assertThat(dto.getEstablishmentName()).isEqualTo(this.establishment.getName());
        assertThat(dto.getFeeLabel()).isEqualTo("Scolarité de Février");
        assertThat(dto.getStudentClassName()).isEqualTo(classroom.getName());
        assertThat(dto.getAmountSchool()).isEqualByComparingTo("50000");
        assertThat(dto.getAmountCommission()).isEqualByComparingTo("0");
    }

    /* ---------- fabriques ---------- */

    private SchoolClassEntity classroom(String name) {
        return this.schoolClassRepository.saveAndFlush(SchoolClassEntity.builder()
                .name(name + " " + UUID.randomUUID()).capacity(40)
                .establishment(this.establishment).build());
    }

    private InstallmentEntity anInstallment(String label, BigDecimal amount,
                                            SchoolClassEntity classroom) {
        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Marie").lastName("Kouassi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 9, 12))
                .schoolClass(classroom)
                .establishment(this.establishment).build());
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Frais " + UUID.randomUUID()).price(amount)
                .establishment(this.establishment).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(student).fee(fee).build());
        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label(label).amount(amount)
                .dueDate(LocalDate.of(2026, 10, 15))
                .status(InstallmentStatus.PENDING).build());
    }

    private void authenticateAsSchoolUser() {
        String email = "agent-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(this.establishment)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
