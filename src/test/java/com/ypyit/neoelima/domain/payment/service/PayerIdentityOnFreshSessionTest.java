package com.ypyit.neoelima.domain.payment.service;

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
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le payeur est reconnu pour ce qu'il est, même lu dans une session neuve.
 *
 * <p>{@code PaymentIntentEntity.payer} est une association <strong>paresseuse</strong>. Quand
 * l'intention vient d'être construite — l'encaissement au guichet — {@code getPayer()} rend
 * l'instance réelle, et {@code instanceof EstablishmentUserEntity} répond juste. Quand elle est
 * <strong>relue</strong> — le webhook, la réconciliation, l'émission différée d'un reçu —, il rend
 * un <strong>proxy typé {@code UserEntity}</strong>, et le test de type répond <em>faux</em> pour
 * un agent d'école.
 *
 * <p>Deux conséquences, toutes deux sur un reçu :
 * <ul>
 *   <li>{@code ReceiptAudience} ajoute alors l'agent aux destinataires — il reçoit le reçu d'une
 *       famille qui n'est pas la sienne ;</li>
 *   <li>{@code ReceiptIssuer} inscrit son nom comme payeur, là où la pièce doit rester muette.</li>
 * </ul>
 *
 * <p>C'est la même famille de défaut que {@code CurrentUserProvider} corrige déjà par
 * {@code Hibernate.unproxy} — révélée là aussi par le passage d'une association en LAZY.
 *
 * <p>Bénin tant que les paiements en ligne viennent de parents : « ce n'est pas un agent » est alors
 * la bonne réponse, par accident. Il devient faux au premier règlement fait par une école.
 *
 * <p>Le {@code entityManager.clear()} est tout le test : sans lui, l'entité reste en session et le
 * défaut est invisible — c'est exactement pourquoi {@code ReceiptRecipientsTest}, qui n'exerce que
 * le guichet, passait sans rien voir.
 */
@Transactional
class PayerIdentityOnFreshSessionTest extends AbstractIntegrationTest {

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
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("un agent d'école payeur n'est pas ajouté aux destinataires du reçu, même relu")
    void schoolAgentIsNotAReceiptRecipientWhenReadFresh() {
        UUID intentId = anIntentPaidBySchoolAgent();

        // Le cœur du test : on force une session neuve, comme le webhook et la réconciliation.
        entityManager.flush();
        entityManager.clear();
        PaymentIntentEntity reread = paymentIntentRepository.findById(intentId).orElseThrow();

        Collection<UserEntity> recipients = ReceiptAudience.accountsOf(reread);

        assertThat(recipients)
                .as("l'agent qui encaisse ne reçoit pas le reçu de la famille")
                .noneMatch(user -> user.getId().equals(reread.getPayer().getId()));
        assertThat(recipients)
                .as("le parent, lui, le reçoit toujours")
                .isNotEmpty();
    }

    private UUID anIntentPaidBySchoolAgent() {
        EstablishmentEntity school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École payeur " + UUID.randomUUID()).active(true).build());

        StudentParentUserEntity parent = userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Aya").lastName("Traoré")
                .contacts(primaryEmail("parent-" + UUID.randomUUID() + "@email.com"))
                .build());

        EstablishmentUserEntity agent = userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Koné")
                .establishment(school)
                .contacts(primaryEmail("agent-" + UUID.randomUUID() + "@email.com"))
                .build());

        StudentEntity student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3))
                .establishment(school)
                .parentUsers(new HashSet<>(Set.of(parent)))
                .build());

        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("50000"))
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        InstallmentEntity installment = installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement").amount(new BigDecimal("50000"))
                .status(InstallmentStatus.PENDING).build());

        return paymentIntentRepository.saveAndFlush(PaymentIntentEntity.builder()
                .installment(installment)
                .payer(agent)
                .amountSchool(new BigDecimal("50000"))
                .amountCommission(BigDecimal.ZERO)
                .channel(PaymentChannel.CASH)
                .status(PaymentIntentStatus.SUCCEEDED)
                .build()).getId();
    }

    private Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(Set.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
