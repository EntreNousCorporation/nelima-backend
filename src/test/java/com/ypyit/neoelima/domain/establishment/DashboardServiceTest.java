package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.domain.establishment.dto.DashboardSummaryDto;
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
import com.ypyit.neoelima.domain.establishment.service.DashboardService;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.form.OfflineCollectionForm;
import com.ypyit.neoelima.domain.payment.service.OfflineCollectionService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
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

/**
 * Chiffres d'accueil de l'établissement.
 *
 * <p>Ce sont des montants d'argent affichés en évidence : une école qui lit « 150 000 F encaissés »
 * s'y fie pour sa caisse. Deux risques sont couverts ici — compter ce qui appartient à une autre
 * école, et compter un mois pour un autre.
 */
@Transactional
class DashboardServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DashboardService dashboardService;
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
    @MockitoBean
    private PushNotificationService pushNotificationService;

    private EstablishmentEntity mySchool;
    private EstablishmentEntity otherSchool;
    private StudentEntity myStudent;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mySchool = aSchool("Mon école");
        otherSchool = aSchool("École voisine");
        myStudent = aStudent(mySchool, "Aaron");
        authenticateAgentOf(mySchool);
    }

    @Test
    @DisplayName("l'effectif et les encaissements sont ceux de l'école, pas ceux de la voisine")
    void countsOnlyItsOwnSchool() {
        aStudent(otherSchool, "Bintou");
        collect(aDueInstallment(myStudent, new BigDecimal("30000"), LocalDate.now().plusDays(10)));
        // Encaissement de la voisine : il ne doit apparaître dans aucun total.
        authenticateAgentOf(otherSchool);
        collect(aDueInstallment(aStudent(otherSchool, "Cissé"), new BigDecimal("99000"),
                LocalDate.now().plusDays(10)));
        authenticateAgentOf(mySchool);

        DashboardSummaryDto summary = dashboardService.summaryOf(null);

        assertThat(summary.getStudentCount()).isEqualTo(1);
        assertThat(summary.getCollectedThisMonth()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(summary.getReceiptsThisMonth()).isEqualTo(1);
        assertThat(summary.getRecentReceipts()).hasSize(1);
    }

    @Test
    @DisplayName("le reste dû distingue les échéances dépassées de celles à venir")
    void separatesOverdueFromUpcoming() {
        aDueInstallment(myStudent, new BigDecimal("20000"), LocalDate.now().minusDays(3));
        aDueInstallment(myStudent, new BigDecimal("50000"), LocalDate.now().plusDays(30));

        DashboardSummaryDto summary = dashboardService.summaryOf(null);

        assertThat(summary.getPendingAmount()).isEqualByComparingTo(new BigDecimal("70000"));
        assertThat(summary.getPendingCount()).isEqualTo(2);
        // Seule la tranche dépassée : c'est celle sur laquelle l'école doit agir.
        assertThat(summary.getOverdueAmount()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(summary.getOverdueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("une école sans mouvement affiche zéro, et non des totaux vides")
    void showsZeroRatherThanNothing() {
        DashboardSummaryDto summary = dashboardService.summaryOf(null);

        // `sum()` rend null sur un ensemble vide : sans garde, l'écran afficherait « null F ».
        assertThat(summary.getCollectedThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getPendingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getOverdueAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getRecentReceipts()).isEmpty();
    }

    private void collect(InstallmentEntity installment) {
        offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId())
                .channel(PaymentChannel.CASH)
                .build());
    }

    private EstablishmentEntity aSchool(String name) {
        return establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name(name + " " + UUID.randomUUID()).active(true).build());
    }

    private StudentEntity aStudent(EstablishmentEntity school, String firstName) {
        return studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName(firstName).lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());
    }

    private InstallmentEntity aDueInstallment(StudentEntity student, BigDecimal amount, LocalDate dueDate) {
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(amount)
                .establishment(student.getEstablishment()).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        return installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement").amount(amount)
                .dueDate(dueDate).status(InstallmentStatus.PENDING).build());
    }

    private void authenticateAgentOf(EstablishmentEntity school) {
        String email = "agent-" + UUID.randomUUID() + "@nelima.ci";
        userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(primaryEmail(email)).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(List.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
