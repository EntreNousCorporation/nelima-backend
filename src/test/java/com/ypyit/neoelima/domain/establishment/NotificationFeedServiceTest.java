package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.NotificationDto;
import com.ypyit.neoelima.domain.establishment.entity.ActivityEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.ActivityEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.ActivityKind;
import com.ypyit.neoelima.domain.establishment.enums.ActivityStatus;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentSource;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.NotificationKind;
import com.ypyit.neoelima.domain.establishment.repository.ActivityEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.ActivityRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.NotificationFeedService;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.PermissionRepository;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
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

/**
 * Fil de notifications.
 *
 * <p>Le risque propre à un fil calculé est qu'il rende toujours zéro : la requête se trompe de
 * jointure, personne ne s'en aperçoit, et la cloche ment en silence pendant des mois. Chaque nature
 * est donc éprouvée sur un fait réel.
 */
@Transactional
class NotificationFeedServiceTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationFeedService notificationFeedService;
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
    private ActivityRepository activityRepository;
    @Autowired
    private ActivityEnrollmentRepository activityEnrollmentRepository;
    @Autowired
    private PaymentIntentRepository paymentIntentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;

    private EstablishmentEntity school;
    private StudentEntity student;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.school();
        this.student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(this.school).build());
        this.authenticateOn(this.school, "activity:read", "accounting:read", "fee:read");
    }

    @Test
    @DisplayName("les trois natures remontent, la plus récente en tête")
    void surfacesTheThreeKinds() {
        this.parentEnrolment();
        this.paymentWithoutReceipt();
        this.overdueInstallment();

        List<NotificationDto> feed = this.notificationFeedService.feed();

        assertThat(feed).extracting(NotificationDto::getKind)
                .containsExactlyInAnyOrder(NotificationKind.ACTIVITY_REQUEST,
                        NotificationKind.PAYMENT_TO_RECONCILE,
                        NotificationKind.INSTALLMENT_OVERDUE);
        // Le fil se lit du haut : l'ordre n'est pas cosmétique.
        assertThat(feed).isSortedAccordingTo(
                (a, b) -> b.getOccurredAt().compareTo(a.getOccurredAt()));
    }

    @Test
    @DisplayName("une inscription saisie par l'école n'est pas une notification")
    void ignoresSchoolEnrolments() {
        ActivityEntity activity = this.activity();
        this.activityEnrollmentRepository.saveAndFlush(ActivityEnrollmentEntity.builder()
                .activity(activity).student(this.student)
                .status(EnrollmentStatus.ENROLLED).source(EnrollmentSource.SCHOOL)
                .requestedAt(Instant.now()).build());

        // Le secrétariat sait ce qu'il vient de saisir : le lui annoncer ne ferait que du bruit.
        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("tout est non lu tant que le panneau n'a pas été ouvert, puis rien ne l'est")
    void unreadUntilSeen() {
        this.parentEnrolment();

        assertThat(this.notificationFeedService.feed()).allMatch(NotificationDto::isUnread);

        this.notificationFeedService.markSeen();

        assertThat(this.notificationFeedService.feed()).noneMatch(NotificationDto::isUnread);
    }

    @Test
    @DisplayName("le fil d'une autre école est vide")
    void doesNotLeakOtherSchools() {
        this.parentEnrolment();
        this.overdueInstallment();

        this.authenticateOn(this.school(), "activity:read", "accounting:read", "fee:read");

        assertThat(this.notificationFeedService.feed()).isEmpty();
    }

    @Test
    @DisplayName("sans droit sur la comptabilité, l'encaissement à réconcilier n'apparaît pas")
    void hidesAccountingWithoutPermission() {
        this.paymentWithoutReceipt();
        this.parentEnrolment();

        this.authenticateOn(this.school, "activity:read");

        // Le contenu est filtré permission par permission : la route, elle, reste ouverte, sinon
        // un rôle sans comptabilité perdrait sa cloche entière.
        assertThat(this.notificationFeedService.feed())
                .extracting(NotificationDto::getKind)
                .containsExactly(NotificationKind.ACTIVITY_REQUEST);
    }

    /* ---------- fabriques ---------- */

    private void parentEnrolment() {
        this.activityEnrollmentRepository.saveAndFlush(ActivityEnrollmentEntity.builder()
                .activity(this.activity()).student(this.student)
                .status(EnrollmentStatus.WAITLISTED).source(EnrollmentSource.PARENT)
                .requestedAt(Instant.now().minusSeconds(120)).build());
    }

    private ActivityEntity activity() {
        return this.activityRepository.saveAndFlush(ActivityEntity.builder()
                .name("Judo").kind(ActivityKind.SPORT).capacity(10)
                .status(ActivityStatus.ACTIVE).establishment(this.school).build());
    }

    private void paymentWithoutReceipt() {
        this.paymentIntentRepository.saveAndFlush(PaymentIntentEntity.builder()
                .installment(this.installment(LocalDate.now().plusDays(10), InstallmentStatus.PAID))
                .amountSchool(new BigDecimal("25000"))
                .amountCommission(BigDecimal.ZERO)
                .channel(PaymentChannel.CASH)
                .status(PaymentIntentStatus.SUCCEEDED)
                .settledAt(Instant.now().minusSeconds(60))
                .build());
    }

    private void overdueInstallment() {
        this.installment(LocalDate.now().minusDays(3), InstallmentStatus.PENDING);
    }

    private InstallmentEntity installment(LocalDate dueDate, InstallmentStatus status) {
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("25000"))
                .establishment(this.school).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(this.student).fee(fee).build());
        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement unique")
                .amount(new BigDecimal("25000")).dueDate(dueDate).status(status).build());
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private void authenticateOn(EstablishmentEntity establishment, String... permissions) {
        Set<PermissionEntity> granted = new HashSet<>();
        for (String code : permissions) {
            granted.add(this.permissionRepository.findByCode(code)
                    .orElseGet(() -> this.permissionRepository.saveAndFlush(PermissionEntity.builder()
                            .code(code)
                            .name(TranslateEntity.builder().fr(code).en(code).build()).build())));
        }
        RoleEntity role = this.roleRepository.saveAndFlush(RoleEntity.builder()
                .code("ROLE-" + UUID.randomUUID())
                .name(TranslateEntity.builder().fr("Rôle de test").en("Test role").build())
                .permissions(granted).build());

        String email = "user-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(establishment).role(role)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
