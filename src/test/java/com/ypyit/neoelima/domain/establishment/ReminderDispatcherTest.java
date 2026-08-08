package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.common.service.sms.service.SmsSender;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.NotificationPreferenceEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderOrigin;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.NotificationPreferenceRepository;
import com.ypyit.neoelima.domain.establishment.repository.ReminderDeliveryRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.InstallmentReminderJob;
import com.ypyit.neoelima.domain.establishment.service.ReminderDispatcher;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Registre des rappels déjà envoyés.
 *
 * <p>C'est la pièce qui rend les campagnes tenables. Sans elle, deux émetteurs — le travail
 * programmé et une campagne lancée à la main — relancent la même famille le même jour sans le
 * savoir. Sur un canal facturé à l'envoi, l'erreur se paie deux fois et personne ne la remarque
 * avant la facture.
 */
@Transactional
class ReminderDispatcherTest extends AbstractIntegrationTest {

    @Autowired
    private ReminderDispatcher dispatcher;
    @Autowired
    private InstallmentReminderJob reminderJob;
    @Autowired
    private ReminderDeliveryRepository deliveryRepository;
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
    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @MockitoBean
    private PushNotificationService pushNotificationService;
    @MockitoBean
    private SmsSender smsSender;

    private InstallmentEntity installment;
    private EstablishmentEntity school;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.establishmentRepository.saveAndFlush(
                EstablishmentEntity.builder()
                        .name("École " + UUID.randomUUID()).active(true).build());
        EstablishmentEntity school = this.school;
        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());

        StudentParentUserEntity guardian = this.userRepository.saveAndFlush(
                StudentParentUserEntity.builder()
                        .firstName("Jean").lastName("Kouassi")
                        .contacts(new HashSet<>(Set.of(
                                ContactEntity.builder().type(ContactType.EMAIL)
                                        .value("parent-" + UUID.randomUUID() + "@gmail.com")
                                        .isPrimary(true).build(),
                                ContactEntity.builder().type(ContactType.PHONE_NUMBER)
                                        .value("+2250101020304").build())))
                        .build());
        student.getParentUsers().add(guardian);
        this.studentRepository.saveAndFlush(student);

        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité").price(new BigDecimal("50000")).establishment(school).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(student).fee(fee).name("Scolarité").build());
        // Échéance à J+7 : c'est l'un des décalages que le travail programmé vise.
        this.installment = this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement")
                .amount(new BigDecimal("25000")).dueDate(LocalDate.now().plusDays(7))
                .status(InstallmentStatus.PENDING).build());
    }

    @Test
    @DisplayName("deux exécutions du travail programmé le même jour n'envoient qu'une fois")
    void theJobIsIdempotentWithinADay() {
        this.reminderJob.remindUpcomingInstallments();
        this.reminderJob.remindUpcomingInstallments();

        // Avant le registre, la seconde exécution renvoyait tout. C'était assumé pour une
        // notification gratuite ; ça ne l'est plus quand le même registre sert au SMS.
        verify(this.pushNotificationService, times(1)).send(any(), anyString(), anyString(), any());
        assertThat(this.deliveryRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("une campagne ignore le destinataire que le travail programmé vient de relancer")
    void aCampaignSkipsWhoTheJobJustReminded() {
        this.reminderJob.remindUpcomingInstallments();

        ReminderDispatcher.Result result = this.dispatcher.remind(this.installment,
                List.of(ReminderChannel.PUSH), ReminderOrigin.CAMPAIGN, null,
                NotificationEvent.INSTALLMENT_OVERDUE, "Relance", "Votre échéance reste due.");

        assertThat(result.sent()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(this.pushNotificationService, times(1)).send(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("le SMS et la notification ne se confondent pas : ce sont deux canaux")
    void channelsAreTrackedSeparately() {
        // Le SMS est fermé par défaut, et ce cas parle du registre, pas du réglage : on l'ouvre.
        this.openSms();
        this.dispatcher.remind(this.installment, List.of(ReminderChannel.PUSH),
                ReminderOrigin.CAMPAIGN, null, NotificationEvent.INSTALLMENT_OVERDUE,
                "Relance", "Message");

        // Recevoir la même relance par notification puis par SMS n'est pas un doublon : c'est un
        // choix de l'école. La recevoir deux fois par SMS en serait un.
        ReminderDispatcher.Result bySms = this.dispatcher.remind(this.installment,
                List.of(ReminderChannel.SMS), ReminderOrigin.CAMPAIGN, null,
                NotificationEvent.INSTALLMENT_OVERDUE, "Relance", "Message");

        assertThat(bySms.sent()).isEqualTo(1);
        assertThat(this.deliveryRepository.findAll()).hasSize(2);
    }

    private void openSms() {
        for (NotificationEvent event : List.of(NotificationEvent.INSTALLMENT_DUE_SOON,
                NotificationEvent.INSTALLMENT_OVERDUE)) {
            this.notificationPreferenceRepository.saveAndFlush(NotificationPreferenceEntity.builder()
                    .establishment(this.school).event(event)
                    .channel(NotificationChannel.SMS).enabled(true).build());
        }
    }

    @Test
    @DisplayName("un canal coupé aux paramètres n'envoie rien")
    void aDisabledChannelSendsNothing() {
        this.notificationPreferenceRepository.saveAndFlush(NotificationPreferenceEntity.builder()
                .establishment(this.school).event(NotificationEvent.INSTALLMENT_DUE_SOON)
                .channel(NotificationChannel.PUSH).enabled(false).build());

        this.reminderJob.remindUpcomingInstallments();

        // C'est tout l'enjeu du lot : une matrice que l'envoi n'interroge pas est une promesse que
        // le premier essai contredit.
        verify(this.pushNotificationService, never()).send(any(), anyString(), anyString(), any());
        assertThat(this.deliveryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("le SMS reste fermé tant que l'école ne l'a pas ouvert")
    void smsStaysClosedUntilOpened() {
        this.reminderJob.remindUpcomingInstallments();

        // Un canal payant activé sans le savoir se découvre sur la facture.
        verify(this.smsSender, never()).send(anyString(), anyString());
        assertThat(this.deliveryRepository.findAll())
                .allMatch(delivery -> ReminderChannel.PUSH.equals(delivery.getChannel()));
    }

    @Test
    @DisplayName("le SMS ouvert aux paramètres part avec le rappel programmé")
    void openedSmsIsSentByTheJob() {
        this.openSms();

        this.reminderJob.remindUpcomingInstallments();

        verify(this.smsSender).send(anyString(), anyString());
    }

    @Test
    @DisplayName("un envoi qui échoue n'est pas inscrit au registre")
    void aFailedSendIsNotRecorded() {
        org.mockito.Mockito.doThrow(new IllegalStateException("service indisponible"))
                .when(this.pushNotificationService).send(any(), anyString(), anyString(), any());

        ReminderDispatcher.Result result = this.dispatcher.remind(this.installment,
                List.of(ReminderChannel.PUSH), ReminderOrigin.CAMPAIGN, null,
                NotificationEvent.INSTALLMENT_OVERDUE, "Relance", "Message");

        // Inscrire un envoi qui n'a pas eu lieu masquerait la famille : elle ne serait plus
        // relancée alors qu'elle n'a rien reçu.
        assertThat(result.sent()).isZero();
        assertThat(this.deliveryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("un élève sans tuteur rattaché ne produit aucun envoi")
    void noGuardianNoReminder() {
        StudentEntity orphanRecord = this.installment.getStudentFee().getStudent();
        orphanRecord.getParentUsers().clear();
        this.studentRepository.saveAndFlush(orphanRecord);

        ReminderDispatcher.Result result = this.dispatcher.remind(this.installment,
                List.of(ReminderChannel.PUSH), ReminderOrigin.AUTOMATIC, null,
                NotificationEvent.INSTALLMENT_OVERDUE, "Relance", "Message");

        assertThat(result.sent()).isZero();
        assertThat(result.skipped()).isZero();
    }
}
