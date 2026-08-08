package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.domain.establishment.dto.AuditEventDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.AuditService;
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
 * Journal d'audit.
 *
 * <p>Ce qu'on vérifie n'est pas que la table se remplit, mais qu'elle se remplit <em>aux endroits
 * qui comptent</em>, avec l'auteur : un journal sans auteur ne répond pas à la question qu'on lui
 * pose, et un journal qui déborde chez le voisin en pose une autre.
 */
@Transactional
class AuditServiceTest extends AbstractIntegrationTest {

    private static final LocalDate FROM = LocalDate.now().minusDays(1);
    private static final LocalDate TO = LocalDate.now();

    @Autowired
    private AuditService auditService;
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
    @MockitoBean
    private EmailService emailService;

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
        this.authenticateOn(this.school, "Awa", "Traoré");
    }

    @Test
    @DisplayName("un encaissement au guichet est consigné, avec son auteur")
    void recordsAnOfflineCollection() {
        this.collect();

        List<AuditEventDto> journal = this.auditService.findAll(FROM, TO, null);

        assertThat(journal).hasSize(1);
        AuditEventDto entry = journal.getFirst();
        assertThat(entry.getAction()).isEqualTo(AuditAction.PAYMENT_COLLECTED);
        assertThat(entry.getTarget()).contains(this.student.getRegistrationNumber());
        // C'est l'auteur qu'on vient chercher : sans lui, la ligne ne répond à rien.
        assertThat(entry.getActorName()).isEqualTo("Awa Traoré");
        assertThat(entry.getDetails()).contains("500");
    }

    @Test
    @DisplayName("le filtre par auteur ne rend que ses propres actes")
    void filtersByActor() {
        this.collect();
        UUID other = this.authenticateOn(this.school, "Kouadio", "N'Guessan");
        this.collect();

        assertThat(this.auditService.findAll(FROM, TO, other))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getActorName()).isEqualTo("Kouadio N'Guessan"));
        assertThat(this.auditService.findAll(FROM, TO, null)).hasSize(2);
    }

    @Test
    @DisplayName("le journal d'une autre école est invisible")
    void doesNotLeakOtherSchools() {
        this.collect();

        this.authenticateOn(this.school(), "Marie", "Bamba");

        assertThat(this.auditService.findAll(FROM, TO, null)).isEmpty();
    }

    /* ---------- fabriques ---------- */

    private void collect() {
        this.offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(this.aDueInstallment().getId())
                .channel(PaymentChannel.CASH)
                .build());
    }

    private InstallmentEntity aDueInstallment() {
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("500"))
                .establishment(this.school).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(this.student).fee(fee).build());
        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement unique").amount(new BigDecimal("500"))
                .status(InstallmentStatus.PENDING).build());
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private UUID authenticateOn(EstablishmentEntity establishment, String firstName, String lastName) {
        String email = "user-" + UUID.randomUUID() + "@ecole.ci";
        EstablishmentUserEntity user = this.userRepository.saveAndFlush(
                EstablishmentUserEntity.builder()
                        .firstName(firstName).lastName(lastName).establishment(establishment)
                        .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                                .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                        .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
        return user.getId();
    }
}
