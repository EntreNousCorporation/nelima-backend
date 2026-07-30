package com.ypyit.neoelima.domain.payment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.service.email.service.EmailService;
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
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.form.OfflineCollectionForm;
import com.ypyit.neoelima.domain.payment.service.OfflineCollectionService;
import com.ypyit.neoelima.domain.payment.service.ReceiptService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
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

/**
 * Accès du parent à ses reçus.
 *
 * <p>Le reçu est la seule preuve de paiement de la famille : un parent qui ne peut pas le relire
 * n'a rien à opposer à l'école. La recherche filtre sur un chemin imbriqué profond
 * (reçu → tentative → tranche → dette → élève) que QueryDSL n'initialise pas par défaut ; sans
 * {@code @QueryInit} la requête échoue à l'exécution alors que le code compile. D'où ce test, qui
 * exerce le chemin réel plutôt que la seule règle métier.
 */
@Transactional
class ParentReceiptAccessTest extends AbstractIntegrationTest {

    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private OfflineCollectionService offlineCollectionService;
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
    private UserRepository userRepository;

    /** L'envoi du reçu par courriel n'est pas le sujet ici. */
    @MockitoBean
    private EmailService emailService;

    private EstablishmentEntity school;
    private ReceiptEntity myChildsReceipt;
    private ReceiptEntity anotherFamilysReceipt;
    private String parentEmail;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École reçus parent " + UUID.randomUUID()).active(true).build());

        StudentEntity myChild = aStudent("Aaron");
        StudentEntity otherChild = aStudent("Bintou");

        parentEmail = "parent-" + UUID.randomUUID() + "@gmail.com";
        StudentParentUserEntity parent = userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .contacts(primaryEmail(parentEmail)).build());
        myChild.getParentUsers().add(parent);
        studentRepository.saveAndFlush(myChild);

        // Les deux encaissements sont faits par l'école, au guichet : le parent n'est donc pas le
        // payeur, et seul son rattachement à l'élève peut lui ouvrir la lecture.
        authenticateAsSchoolAgent();
        myChildsReceipt = collect(myChild);
        anotherFamilysReceipt = collect(otherChild);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(parentEmail, "n/a", List.of()));
    }

    @Test
    @DisplayName("le parent retrouve le reçu de son enfant dans sa liste")
    void parentListsTheirOwnChildsReceipt() {
        var found = receiptService.search(null, PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(ReceiptEntity::getId)
                .containsExactly(myChildsReceipt.getId());
    }

    @Test
    @DisplayName("la liste du parent exclut les reçus des autres familles")
    void parentListExcludesOtherFamilies() {
        var found = receiptService.search(null, PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(ReceiptEntity::getId)
                .doesNotContain(anotherFamilysReceipt.getId());
    }

    @Test
    @DisplayName("le parent peut ouvrir le reçu de son enfant")
    void parentOpensTheirOwnChildsReceipt() {
        assertThat(receiptService.findById(myChildsReceipt.getId()).getId())
                .isEqualTo(myChildsReceipt.getId());
    }

    @Test
    @DisplayName("le parent ne peut pas ouvrir le reçu d'une autre famille")
    void parentCannotOpenAnotherFamilysReceipt() {
        // L'identifiant seul ne donne aucun droit : sinon un parent parcourrait les paiements de
        // toute l'école en devinant des UUID.
        assertThatThrownBy(() -> receiptService.findById(anotherFamilysReceipt.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("l'école continue de voir tous les reçus qu'elle a émis")
    void schoolStillSeesEveryReceiptItIssued() {
        authenticateAsSchoolAgent();

        var found = receiptService.search(null, PageRequest.of(0, 20));

        assertThat(found.getContent()).extracting(ReceiptEntity::getId)
                .contains(myChildsReceipt.getId(), anotherFamilysReceipt.getId());
    }

    private ReceiptEntity collect(StudentEntity student) {
        return offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(aDueInstallment(student).getId())
                .channel(PaymentChannel.CASH)
                .build());
    }

    private void authenticateAsSchoolAgent() {
        String email = "agent-" + UUID.randomUUID() + "@nelima.ci";
        userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(primaryEmail(email)).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    private StudentEntity aStudent(String firstName) {
        return studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName(firstName).lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());
    }

    private InstallmentEntity aDueInstallment(StudentEntity student) {
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("500"))
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        return installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement unique").amount(new BigDecimal("500"))
                .status(InstallmentStatus.PENDING).build());
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(List.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
