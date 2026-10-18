package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentStatsDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentDocumentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.Gender;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.StudentDocumentKind;
import com.ypyit.neoelima.domain.establishment.enums.StudentPaymentStatus;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentDocumentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.establishment.service.StudentStatsService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chiffres de l'écran Élèves.
 *
 * <p>Une école de quatre élèves, un par cas : dossier incomplet, en retard, acompte, à jour. Les
 * cartes, les filtres et la colonne de statut doivent tous les ranger au même endroit — c'est ce
 * qui permet de croire un chiffre quand on clique dessus.
 */
@Transactional
class StudentStatsServiceTest extends AbstractIntegrationTest {

    @Autowired
    private StudentStatsService studentStatsService;
    @Autowired
    private StudentService studentService;
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
    private StudentDocumentRepository studentDocumentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private FeeEntity fee;
    private StudentEntity incomplete;
    private StudentEntity late;
    private StudentEntity deposit;
    private StudentEntity upToDate;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.school();
        this.authenticateOn(this.school);
        this.fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("300000"))
                .academical(true).establishment(this.school).build());
        LocalDate today = LocalDate.now();

        // Rien à payer, mais aucune pièce : le dossier incomplet l'emporte sur « à jour ».
        this.incomplete = this.student("KONE", Gender.FEMALE);

        this.late = this.withDocuments(this.student("SORO", Gender.MALE));
        this.installment(this.late, "100000", today.minusDays(10), InstallmentStatus.PENDING);

        this.deposit = this.withDocuments(this.student("KOUASSI", Gender.FEMALE));
        this.installment(this.deposit, "100000", today.minusDays(30), InstallmentStatus.PAID);
        this.installment(this.deposit, "75000", today.plusDays(20), InstallmentStatus.PENDING);

        // Une tranche à venir, rien de payé, rien d'échu : à jour.
        this.upToDate = this.withDocuments(this.student("ADOU", null));
        this.installment(this.upToDate, "50000", today.plusDays(20), InstallmentStatus.PENDING);

        // Une autre école, qui ne doit apparaître dans aucun chiffre.
        EstablishmentEntity other = this.school();
        this.studentRepository.saveAndFlush(StudentEntity.builder().firstName("X").lastName("AUTRE")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .establishment(other).build());
    }

    @Test
    @DisplayName("les statuts sont exclusifs et leur somme fait l'effectif")
    void statusesAreExclusive() {
        StudentStatsDto stats = this.studentStatsService.compute(null);

        assertThat(stats.getTotal()).isEqualTo(4);
        assertThat(stats.getStatusIncomplete()).isEqualTo(1);
        assertThat(stats.getStatusOverdue()).isEqualTo(1);
        assertThat(stats.getStatusDeposit()).isEqualTo(1);
        assertThat(stats.getStatusUpToDate()).isEqualTo(1);
        assertThat(stats.getOverdueCount()).isEqualTo(1);
        assertThat(stats.getOverdueAmount()).isEqualByComparingTo("100000");
        assertThat(stats.getWithoutClass()).isEqualTo(4);
        assertThat(stats.getFemale()).isEqualTo(2);
        assertThat(stats.getMale()).isEqualTo(1);
        assertThat(stats.getGenderUnknown()).isEqualTo(1);
        assertThat(stats.getNewThisMonth()).isEqualTo(4);
    }

    @Test
    @DisplayName("chaque filtre de statut rend exactement les élèves de ce statut")
    void filtersMatchTheStatuses() {
        assertThat(this.idsFor(StudentSearchForm.builder().paymentStatus(StudentPaymentStatus.OVERDUE).build()))
                .containsExactly(this.late.getId());
        assertThat(this.idsFor(StudentSearchForm.builder().paymentStatus(StudentPaymentStatus.DEPOSIT).build()))
                .containsExactly(this.deposit.getId());
        assertThat(this.idsFor(StudentSearchForm.builder().paymentStatus(StudentPaymentStatus.UP_TO_DATE).build()))
                .containsExactlyInAnyOrder(this.incomplete.getId(), this.upToDate.getId());
        assertThat(this.idsFor(StudentSearchForm.builder().incompleteDossier(true).build()))
                .containsExactly(this.incomplete.getId());
        assertThat(this.idsFor(StudentSearchForm.builder().gender(Gender.FEMALE).build()))
                .containsExactlyInAnyOrder(this.incomplete.getId(), this.deposit.getId());
    }

    @Test
    @DisplayName("la colonne de statut de la liste dit la même chose que les filtres")
    void listRowsCarryTheSameStatus() {
        List<StudentDto> rows = this.studentService.search(new StudentSearchForm(), PageRequest.of(0, 50))
                .getContent();

        assertThat(this.row(rows, this.late).getPaymentStatus()).isEqualTo(StudentPaymentStatus.OVERDUE);
        assertThat(this.row(rows, this.deposit).getPaymentStatus()).isEqualTo(StudentPaymentStatus.DEPOSIT);
        assertThat(this.row(rows, this.upToDate).getPaymentStatus()).isEqualTo(StudentPaymentStatus.UP_TO_DATE);
        assertThat(this.row(rows, this.incomplete).getDossierComplete()).isFalse();
        assertThat(this.row(rows, this.late).getDossierComplete()).isTrue();
    }

    private List<UUID> idsFor(StudentSearchForm form) {
        return this.studentService.search(form, PageRequest.of(0, 50)).getContent().stream()
                .map(StudentDto::getId).toList();
    }

    private StudentDto row(List<StudentDto> rows, StudentEntity student) {
        return rows.stream().filter(row -> row.getId().equals(student.getId())).findFirst().orElseThrow();
    }

    private StudentEntity student(String lastName, Gender gender) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName(lastName).gender(gender)
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2014, 2, 9)).establishment(this.school).build());
    }

    private StudentEntity withDocuments(StudentEntity student) {
        for (StudentDocumentKind kind : List.of(StudentDocumentKind.BIRTH_CERTIFICATE, StudentDocumentKind.PHOTO)) {
            this.studentDocumentRepository.saveAndFlush(StudentDocumentEntity.builder()
                    .student(student).kind(kind).fileName("piece.pdf").contentType("application/pdf")
                    .sizeBytes(10).objectKey(UUID.randomUUID().toString()).build());
        }
        return student;
    }

    private void installment(StudentEntity student, String amount, LocalDate dueDate, InstallmentStatus status) {
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(this.fee).build());
        this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Tranche").amount(new BigDecimal(amount))
                .dueDate(dueDate).status(status).build());
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
