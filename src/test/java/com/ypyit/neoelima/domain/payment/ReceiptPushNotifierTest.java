package com.ypyit.neoelima.domain.payment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Qui est notifié d'un encaissement, et ce qu'il advient quand la notification échoue.
 *
 * <p>Deux exigences distinctes. La première tient à la vie privée : un push s'affiche sur un écran
 * verrouillé, et notifier le mauvais compte expose les finances d'une famille. La seconde tient à la
 * comptabilité : un service tiers indisponible ne doit jamais empêcher l'émission d'un reçu.
 */
@Transactional
class ReceiptPushNotifierTest extends AbstractIntegrationTest {

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
    private PushNotificationService pushNotificationService;
    /** L'envoi du reçu par courriel n'est pas le sujet. */
    @MockitoBean
    private EmailService emailService;

    private EstablishmentEntity school;
    private StudentEntity student;
    private UUID guardianId;
    private UUID agentId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École push " + UUID.randomUUID()).active(true).build());
        student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());

        StudentParentUserEntity guardian = userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .contacts(primaryEmail("parent-" + UUID.randomUUID() + "@gmail.com")).build());
        guardianId = guardian.getId();
        student.getParentUsers().add(guardian);
        student = studentRepository.saveAndFlush(student);

        String agentEmail = "agent-" + UUID.randomUUID() + "@nelima.ci";
        EstablishmentUserEntity agent = userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(primaryEmail(agentEmail)).build());
        agentId = agent.getId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(agentEmail, "n/a", List.of()));
    }

    @Test
    @DisplayName("le tuteur est notifié, jamais l'agent qui a encaissé")
    void notifiesTheGuardianAndNotTheAgent() {
        collect();

        assertThat(notifiedAccounts()).containsExactly(guardianId);
        assertThat(notifiedAccounts())
                .as("le compte de l'agent au comptoir n'est jamais notifié")
                .doesNotContain(agentId);
    }

    @Test
    @DisplayName("le message ne révèle pas le nom de l'élève")
    void doesNotDiscloseTheStudentName() {
        collect();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(pushNotificationService).send(any(), anyString(), message.capture(), any());

        // Une notification s'affiche sur un écran verrouillé, visible de quiconque passe.
        assertThat(message.getValue()).doesNotContain("Aaron").doesNotContain("Koffi");
        assertThat(message.getValue()).contains(student.getRegistrationNumber());
    }

    @Test
    @DisplayName("une notification en échec n'empêche pas l'émission du reçu")
    void receiptSurvivesAFailingNotification() {
        doThrow(new RuntimeException("OneSignal indisponible"))
                .when(pushNotificationService).send(any(), anyString(), anyString(), any());

        ReceiptEntity[] receipt = new ReceiptEntity[1];
        assertThatCode(() -> receipt[0] = collect()).doesNotThrowAnyException();

        assertThat(receipt[0].getNumber()).isNotBlank();
    }

    @Test
    @DisplayName("sans tuteur rattaché, aucune notification n'est tentée")
    void sendsNothingWithoutAnyGuardian() {
        StudentEntity orphan = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Bintou").lastName("Diallo")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());

        offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(aDueInstallment(orphan).getId())
                .channel(PaymentChannel.CASH)
                .build());

        verify(pushNotificationService, never()).send(any(), anyString(), anyString(), any());
    }

    @SuppressWarnings("unchecked")
    private List<UUID> notifiedAccounts() {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(pushNotificationService).send(captor.capture(), anyString(), anyString(), any());
        return List.copyOf(captor.getValue());
    }

    private ReceiptEntity collect() {
        return offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(aDueInstallment(student).getId())
                .channel(PaymentChannel.CASH)
                .build());
    }

    private InstallmentEntity aDueInstallment(StudentEntity target) {
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("500"))
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(target).fee(fee).build());
        return installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement unique").amount(new BigDecimal("500"))
                .status(InstallmentStatus.PENDING).build());
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(List.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
