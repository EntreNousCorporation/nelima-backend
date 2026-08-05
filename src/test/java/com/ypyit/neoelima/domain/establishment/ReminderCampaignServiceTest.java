package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.common.service.sms.service.SmsSender;
import com.ypyit.neoelima.domain.establishment.dto.ReminderCampaignDto;
import com.ypyit.neoelima.domain.establishment.dto.ReminderTargetDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderTarget;
import com.ypyit.neoelima.domain.establishment.form.ReminderCampaignForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.InstallmentReminderJob;
import com.ypyit.neoelima.domain.establishment.service.ReminderCampaignService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Campagnes de relance.
 *
 * <p>C'est la seule fonction du portail qui engage une dépense. Deux erreurs s'y paient
 * littéralement : viser plus large qu'annoncé, et relancer deux fois la même famille le même jour.
 */
@Transactional
class ReminderCampaignServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ReminderCampaignService campaignService;
    @Autowired
    private InstallmentReminderJob reminderJob;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
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
    @MockitoBean
    private SmsSender smsSender;

    private EstablishmentEntity school;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.school();
        this.authenticateOn(this.school);
    }

    @Test
    @DisplayName("le décompte d'une cible ne retient que les retards qu'elle vise")
    void targetsCountOnlyWhatTheyCover() {
        this.debt("KOUAMÉ", LocalDate.now().minusDays(45), "40000");   // retard > 30
        this.debt("BROU", LocalDate.now().minusDays(10), "20000");     // retard > 7 seulement
        this.debt("YEO", LocalDate.now().plusDays(3), "10000");        // échéance proche

        List<ReminderTargetDto> targets = this.campaignService.targets();

        assertThat(byTarget(targets, ReminderTarget.LATE_30).getFamilyCount()).isEqualTo(1);
        // Le retard de plus de sept jours englobe celui de plus de trente : deux familles.
        assertThat(byTarget(targets, ReminderTarget.LATE_7).getFamilyCount()).isEqualTo(2);
        assertThat(byTarget(targets, ReminderTarget.DUE_SOON).getFamilyCount()).isEqualTo(1);
        assertThat(byTarget(targets, ReminderTarget.ALL_UNPAID).getFamilyCount()).isEqualTo(3);
        assertThat(byTarget(targets, ReminderTarget.LATE_30).getAmountDue())
                .isEqualByComparingTo(new BigDecimal("40000"));
    }

    @Test
    @DisplayName("le décompte annonce les tuteurs joignables, pas les élèves")
    void targetsCountRecipientsNotStudents() {
        InstallmentEntity installment = this.debt("SANOGO", LocalDate.now().minusDays(40), "30000");
        // Deux tuteurs pour un élève : c'est deux envois, donc deux SMS facturés.
        this.attachGuardian(installment.getStudentFee().getStudent());

        ReminderTargetDto late = byTarget(this.campaignService.targets(), ReminderTarget.LATE_30);

        assertThat(late.getFamilyCount()).isEqualTo(1);
        assertThat(late.getRecipientCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("une campagne ignore les familles que le rappel automatique vient de joindre")
    void aCampaignSkipsWhoWasJustReminded() {
        // Échéance à J+1 : le travail programmé la vise, et « échéance dans les 5 jours » aussi.
        // C'est précisément le recouvrement des deux qui crée le risque de doublon.
        this.debt("DIABATÉ", LocalDate.now().plusDays(1), "25000");
        this.reminderJob.remindUpcomingInstallments();

        ReminderCampaignDto campaign = this.campaignService.send(
                this.form("Rappel du jour", ReminderTarget.DUE_SOON, ReminderChannel.PUSH));

        assertThat(campaign.getSentCount()).isZero();
        assertThat(campaign.getSkippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("une cible vide est refusée plutôt qu'envoyée dans le vide")
    void refusesAnEmptyTarget() {
        this.debt("KIPRÉ", LocalDate.now().plusDays(60), "15000");

        // Enregistrer une campagne qui ne touche personne laisserait croire au canal qu'il a
        // fonctionné, et on chercherait l'erreur ailleurs.
        assertThatThrownBy(() -> this.campaignService.send(
                this.form("Dans le vide", ReminderTarget.LATE_30, ReminderChannel.PUSH)))
                .isInstanceOf(BadRequestException.class);
        verify(this.pushNotificationService, never()).send(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("le gabarit remplace les variables par les valeurs de la famille")
    void rendersTheTemplate() {
        this.debt("EHOUMAN", LocalDate.now().minusDays(46), "420000");

        ReminderCampaignForm form = this.form("Relance", ReminderTarget.LATE_30, ReminderChannel.PUSH);
        form.setMessageTemplate("Bonjour {parent}, la scolarité de {eleve} ({classe}) d'un montant "
                + "de {montant} est échue depuis {retard} jours.");
        this.campaignService.send(form);

        // Vérifié sur le message réellement remis au canal, et non sur la fonction de rendu : c'est
        // ce que la famille reçoit qui compte, et une variable oubliée s'y verrait telle quelle.
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(this.pushNotificationService).send(any(), anyString(), message.capture(), any());
        assertThat(message.getValue())
                .contains("EHOUMAN Awa").contains("420 000 FCFA").contains("46 jours")
                .doesNotContain("{");
    }

    @Test
    @DisplayName("les campagnes d'une autre école sont invisibles")
    void doesNotLeakOtherSchools() {
        this.debt("TRAORÉ", LocalDate.now().minusDays(40), "30000");
        this.campaignService.send(this.form("Relance", ReminderTarget.LATE_30, ReminderChannel.PUSH));

        this.school = this.school();
        this.authenticateOn(this.school);

        assertThat(this.campaignService.findAll()).isEmpty();
    }

    /* ---------- fabriques ---------- */

    private static ReminderTargetDto byTarget(List<ReminderTargetDto> targets, ReminderTarget target) {
        return targets.stream().filter(line -> target.equals(line.getTarget()))
                .findFirst().orElseThrow();
    }

    private ReminderCampaignForm form(String name, ReminderTarget target, ReminderChannel channel) {
        ReminderCampaignForm form = new ReminderCampaignForm();
        form.setName(name);
        form.setTarget(target);
        form.setChannels(List.of(channel));
        form.setMessageTemplate("Bonjour {parent}, {montant} restent dus pour {eleve}.");
        return form;
    }

    private InstallmentEntity debt(String lastName, LocalDate dueDate, String amount) {
        LevelOfStudyEntity level = this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code("LVL-" + UUID.randomUUID().toString().substring(0, 8)).position(1)
                .name(TranslateEntity.builder().fr("CM2").en("CM2").build()).build());
        SchoolClassEntity schoolClass = this.schoolClassRepository.saveAndFlush(
                SchoolClassEntity.builder().name("CM2 " + UUID.randomUUID().toString().substring(0, 4))
                        .capacity(35).levelOfStudy(level).establishment(this.school).build());

        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName(lastName)
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 5, 14))
                .schoolClass(schoolClass).establishment(this.school).build());
        this.attachGuardian(student);

        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID().toString().substring(0, 4))
                .price(new BigDecimal(amount)).establishment(this.school).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(student).fee(fee).name(fee.getName()).build());

        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement")
                .amount(new BigDecimal(amount)).dueDate(dueDate)
                .status(InstallmentStatus.PENDING).build());
    }

    private void attachGuardian(StudentEntity student) {
        StudentParentUserEntity guardian = this.userRepository.saveAndFlush(
                StudentParentUserEntity.builder()
                        .firstName("Jean").lastName("SORO")
                        .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                                .type(ContactType.EMAIL)
                                .value("parent-" + UUID.randomUUID() + "@gmail.com")
                                .isPrimary(true).build())))
                        .build());
        student.getParentUsers().add(guardian);
        this.studentRepository.saveAndFlush(student);
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
