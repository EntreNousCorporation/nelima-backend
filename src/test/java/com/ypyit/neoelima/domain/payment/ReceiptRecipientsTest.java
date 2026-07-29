package com.ypyit.neoelima.domain.payment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.common.service.email.service.EmailConstants;
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
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.form.OfflineCollectionForm;
import com.ypyit.neoelima.domain.payment.service.OfflineCollectionService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * À qui part le reçu.
 *
 * <p>La règle est la partie la plus facile à casser sans s'en apercevoir : une erreur ici envoie
 * la pièce comptable d'une famille à la mauvaise personne, ou ne l'envoie à personne, sans qu'aucun
 * test fonctionnel classique ne s'en plaigne.
 */
@Transactional
class ReceiptRecipientsTest extends AbstractIntegrationTest {

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

    @MockitoBean
    private EmailService emailService;

    private EstablishmentEntity school;
    private StudentEntity student;
    private String agentEmail;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École reçus " + UUID.randomUUID()).active(true).build());
        student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());

        agentEmail = "agent-" + UUID.randomUUID() + "@nelima.ci";
        userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(primaryEmail(agentEmail)).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(agentEmail, "n/a", List.of()));
    }

    @Test
    @DisplayName("le tuteur de l'élève reçoit le reçu, jamais l'agent qui a encaissé")
    void guardianReceivesReceiptNotTheAgent() {
        String guardian = "parent-" + UUID.randomUUID() + "@gmail.com";
        attachGuardian(guardian);

        collect(anInstallment(), null, null);

        assertThat(sentRecipients()).containsExactly(guardian);
        assertThat(sentRecipients())
                .as("le compte de l'agent au comptoir n'est jamais destinataire")
                .doesNotContain(agentEmail);
    }

    @Test
    @DisplayName("l'email saisi au comptoir s'ajoute à celui du tuteur")
    void counterEmailIsAddedToTheGuardian() {
        String guardian = "parent-" + UUID.randomUUID() + "@gmail.com";
        attachGuardian(guardian);
        String atCounter = "oncle-" + UUID.randomUUID() + "@gmail.com";

        collect(anInstallment(), "Oncle Kouassi", atCounter);

        assertThat(sentRecipients()).containsExactlyInAnyOrder(guardian, atCounter);
    }

    @Test
    @DisplayName("le même email saisi dans une autre casse ne produit pas deux envois")
    void doesNotSendTwiceForTheSameAddress() {
        String guardian = "parent-" + UUID.randomUUID() + "@gmail.com";
        attachGuardian(guardian);

        collect(anInstallment(), null, guardian.toUpperCase());

        assertThat(sentRecipients()).containsExactly(guardian);
    }

    @Test
    @DisplayName("sans tuteur joignable ni email saisi, aucun envoi n'est tenté")
    void sendsNothingWithoutAnyLegitimateRecipient() {
        collect(anInstallment(), null, null);

        verify(emailService, never()).sendWithAttachment(any(), any(), any(), any(), any());
        verify(emailService, never()).send(any(), any());
    }

    private void collect(InstallmentEntity installment, String payerName, String payerEmail) {
        offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId())
                .channel(PaymentChannel.CASH)
                .payerName(payerName)
                .payerEmail(payerEmail)
                .build());
    }

    private List<String> sentRecipients() {
        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce())
                .sendWithAttachment(captor.capture(), eq(EmailTemplateType.RECEIPT),
                        any(), any(), eq("application/pdf"));
        return captor.getAllValues().stream()
                .map(c -> (String) c.getVariable(EmailConstants.EMAIL))
                .toList();
    }

    private void attachGuardian(String email) {
        StudentParentUserEntity parent = userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .contacts(primaryEmail(email)).build());
        student.getParentUsers().add(parent);
        student = studentRepository.saveAndFlush(student);
    }

    private InstallmentEntity anInstallment() {
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("50000"))
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        return installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement").amount(new BigDecimal("50000"))
                .status(InstallmentStatus.PENDING).build());
    }

    /**
     * Ensemble mutable, et non {@code Set.of} : lors d'un merge en cascade Hibernate appelle
     * {@code clear()} sur la collection, ce qu'une collection immuable refuse.
     */
    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(List.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
