package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.NotificationDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolEventEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.NotificationKind;
import com.ypyit.neoelima.domain.establishment.enums.SchoolEventKind;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolEventRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.NotificationFeedService;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.payment.repository.ReceiptRepository;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * Le fil de notifications tel qu'une famille le lit.
 *
 * <p>La même route sert l'école et la famille, et c'est précisément ce qui la rend fragile : une
 * portée dérivée à l'aveugle refuserait le parent, une jointure trop large lui montrerait l'enfant
 * d'un autre. Les deux sont éprouvés ici.
 */
@Transactional
class ParentNotificationFeedTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationFeedService notificationFeedService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private SchoolEventRepository schoolEventRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private StudentFeeRepository studentFeeRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private PaymentIntentRepository paymentIntentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private StudentEntity myChild;
    private StudentEntity someoneElsesChild;
    private StudentParentUserEntity parent;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("Institut " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.myChild = this.student("Marie");
        this.someoneElsesChild = this.student("Bintou");
        this.authenticateAsParentOf(this.myChild);
    }

    @Test
    @DisplayName("un parent obtient son fil, et non une erreur de portée")
    void servesTheParentInsteadOfRefusingThem() {
        this.installment(this.myChild, LocalDate.now().plusDays(1), new BigDecimal("150000"));

        // La branche école lève « Cette opération suppose un compte d'établissement » : la même
        // route doit répondre au parent sans qu'il ait la moindre permission.
        assertThat(this.notificationFeedService.feed())
                .extracting(NotificationDto::getKind)
                .containsExactly(NotificationKind.INSTALLMENT_DUE_SOON);
    }

    @Test
    @DisplayName("une échéance à J-1 est annoncée comme le rappel l'a annoncée")
    void announcesTheDeadlineTheSameWayThePushDid() {
        this.installment(this.myChild, LocalDate.now().plusDays(1), new BigDecimal("150000"));

        NotificationDto item = this.notificationFeedService.feed().getFirst();

        // Le rappel automatique dit « Échéance demain ». Dire autre chose ici ferait compter deux
        // fois le même fait à un parent qui a reçu la notification sur son téléphone.
        assertThat(item.getTitle()).isEqualTo("Échéance demain");
        assertThat(item.getDetail()).contains("Marie").contains("150 000 FCFA");
    }

    @Test
    @DisplayName("une échéance encore lointaine n'apparaît pas : aucun rappel n'est parti")
    void staysSilentBeforeTheFirstReminder() {
        this.installment(this.myChild, LocalDate.now().plusDays(20), new BigDecimal("150000"));

        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("une échéance à J-3 n'apparaît qu'une fois, au rappel le plus récent")
    void neverShowsTheSameInstalmentTwice() {
        this.installment(this.myChild, LocalDate.now().plusDays(3), new BigDecimal("150000"));

        // J-7 est parti, J-1 pas encore : une seule ligne, datée du J-7.
        assertThat(this.notificationFeedService.feed()).hasSize(1);
    }

    @Test
    @DisplayName("une échéance dépassée est annoncée comme telle")
    void surfacesTheOverdueInstalment() {
        this.installment(this.myChild, LocalDate.now().minusDays(3), new BigDecimal("150000"));

        assertThat(this.notificationFeedService.feed())
                .extracting(NotificationDto::getKind, NotificationDto::getTitle)
                .containsExactly(
                        tuple(NotificationKind.INSTALLMENT_OVERDUE, "Échéance dépassée"));
    }

    @Test
    @DisplayName("une tranche réglée ne dit plus rien")
    void ignoresSettledInstalments() {
        this.installmentWithStatus(this.myChild, LocalDate.now().plusDays(1),
                new BigDecimal("150000"), InstallmentStatus.PAID);

        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("un reçu émis pour l'enfant devient un paiement confirmé")
    void surfacesTheReceipt() {
        this.receiptFor(this.myChild, new BigDecimal("45000"));

        NotificationDto item = this.notificationFeedService.feed().getFirst();

        assertThat(item.getKind()).isEqualTo(NotificationKind.PAYMENT_CONFIRMED);
        assertThat(item.getDetail()).contains("45 000 FCFA").contains("en ligne");
    }

    @Test
    @DisplayName("le reçu de l'enfant d'un autre ne remonte jamais")
    void neverLeaksAnotherChildsReceipt() {
        this.receiptFor(this.someoneElsesChild, new BigDecimal("999000"));

        // Le rapprochement se fait sur le couple école + matricule, paire par paire. Deux `in()`
        // croisés auraient laissé passer celui-ci dès que deux écoles réutilisent un matricule.
        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("l'échéance de l'enfant d'un autre ne remonte jamais")
    void neverLeaksAnotherChildsInstalment() {
        this.installment(this.someoneElsesChild, LocalDate.now().plusDays(1),
                new BigDecimal("999000"));

        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("un événement publié pour toute l'école remonte, daté de sa publication")
    void surfacesTheSchoolEvent() {
        this.event(true, null);

        NotificationDto item = this.notificationFeedService.feed().getFirst();

        assertThat(item.getKind()).isEqualTo(NotificationKind.SCHOOL_EVENT);
        assertThat(item.getTitle()).isEqualTo("Réunion parents-professeurs");
        assertThat(item.getDetail()).contains(this.school.getName());
        // Daté de la publication : c'est le jour où le parent a quelque chose à apprendre, pas
        // celui où la réunion se tient.
        assertThat(item.getOccurredAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("un événement que l'école garde pour elle ne sort pas")
    void respectsTheVisibilityFlag() {
        SchoolEventEntity hidden = this.event(true, null);
        hidden.setVisibleToFamilies(false);
        this.schoolEventRepository.saveAndFlush(hidden);

        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("un événement ciblé sur une autre classe ne concerne pas l'enfant")
    void respectsTheClassTargeting() {
        SchoolClassEntity mine = this.classroom("CM2 A");
        SchoolClassEntity other = this.classroom("6e B");
        this.myChild.setSchoolClass(mine);
        this.studentRepository.saveAndFlush(this.myChild);

        this.event(false, other);

        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("tout est non lu tant que le panneau n'a pas été ouvert, puis rien ne l'est")
    void unreadUntilSeen() {
        this.installment(this.myChild, LocalDate.now().plusDays(1), new BigDecimal("150000"));

        assertThat(this.notificationFeedService.feed()).allMatch(NotificationDto::isUnread);

        this.notificationFeedService.markSeen();

        assertThat(this.notificationFeedService.feed()).noneMatch(NotificationDto::isUnread);
    }

    @Test
    @DisplayName("un compte sans enfant rend une liste vide, pas une erreur")
    void emptyAccountIsNotAnError() {
        this.authenticateAsParentOf();

        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("aucun élément parent ne porte de lien de portail")
    void carriesNoPortalLink() {
        this.installment(this.myChild, LocalDate.now().plusDays(1), new BigDecimal("150000"));
        this.receiptFor(this.myChild, new BigDecimal("45000"));
        this.event(true, null);

        // L'application déduit sa destination de la nature. Un chemin `/app/...` ne lui servirait
        // à rien et finirait par être ouvert dans un navigateur.
        assertThat(this.notificationFeedService.feed()).allSatisfy(
                item -> assertThat(item.getLink()).isNull());
    }

    /* ---------- fabriques ---------- */

    private StudentEntity student(String firstName) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName(firstName).lastName("Kouassi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 9, 12)).establishment(this.school).build());
    }

    private SchoolClassEntity classroom(String name) {
        return this.schoolClassRepository.saveAndFlush(SchoolClassEntity.builder()
                .name(name + " " + UUID.randomUUID()).capacity(30)
                .establishment(this.school).build());
    }

    private void installment(StudentEntity student, LocalDate dueDate, BigDecimal amount) {
        this.installmentWithStatus(student, dueDate, amount, InstallmentStatus.PENDING);
    }

    private InstallmentEntity installmentWithStatus(StudentEntity student, LocalDate dueDate,
                                                    BigDecimal amount, InstallmentStatus status) {
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(amount)
                .establishment(this.school).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(student).fee(fee).build());
        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Scolarité de Février")
                .amount(amount).dueDate(dueDate).status(status).build());
    }

    /**
     * Un reçu complet : il ne peut pas exister sans la tentative de paiement qui l'a produit, et
     * c'est par elle que le fil retrouve l'élève.
     */
    private void receiptFor(StudentEntity student, BigDecimal amount) {
        InstallmentEntity installment = this.installmentWithStatus(student,
                LocalDate.now().minusDays(2), amount, InstallmentStatus.PAID);
        PaymentIntentEntity intent = this.paymentIntentRepository.saveAndFlush(
                PaymentIntentEntity.builder()
                        .installment(installment)
                        .amountSchool(amount)
                        .amountCommission(BigDecimal.ZERO)
                        .channel(PaymentChannel.ONLINE)
                        .status(PaymentIntentStatus.SUCCEEDED)
                        .settledAt(Instant.now().minusSeconds(3600))
                        .build());
        this.receiptRepository.saveAndFlush(ReceiptEntity.builder()
                .number("REC-" + UUID.randomUUID().toString().substring(0, 8))
                // Attribué en production par le compteur de l'établissement ; ici il suffit qu'il
                // soit renseigné et distinct.
                .sequenceNumber(Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100000))
                .amount(amount)
                .issuedAt(Instant.now().minusSeconds(3600))
                .studentLabel(student.getLastName() + " " + student.getFirstName())
                .studentRegistrationNumber(student.getRegistrationNumber())
                .payerLabel("Kouassi Didier")
                .channel(PaymentChannel.ONLINE)
                .establishment(this.school)
                .paymentIntent(intent)
                .build());
    }

    private SchoolEventEntity event(boolean wholeSchool, SchoolClassEntity targeted) {
        Set<SchoolClassEntity> classes = new HashSet<>();
        if (targeted != null) {
            classes.add(targeted);
        }
        return this.schoolEventRepository.saveAndFlush(SchoolEventEntity.builder()
                .title("Réunion parents-professeurs")
                .kind(SchoolEventKind.SCHOOL_LIFE)
                .date(LocalDate.now().plusDays(10))
                .allDay(true)
                .wholeSchool(wholeSchool)
                .classes(classes)
                .visibleToFamilies(true)
                .establishment(this.school)
                .build());
    }

    private void authenticateAsParentOf(StudentEntity... children) {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        this.parent = this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Didier").lastName("Kouassi")
                .contacts(new HashSet<>(List.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        for (StudentEntity child : children) {
            Set<UserEntity> parents = new HashSet<>(child.getParentUsers());
            parents.add(this.parent);
            child.setParentUsers(parents);
            this.studentRepository.saveAndFlush(child);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
