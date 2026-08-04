package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.ValidationException;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.form.FeeCreationForm;
import com.ypyit.neoelima.domain.establishment.form.FeeScheduleForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.FeeScheduleService;
import com.ypyit.neoelima.domain.establishment.service.FeeService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redéfinition de l'échéancier d'un frais déjà porté par des élèves.
 *
 * <p>C'est l'endroit où une erreur coûterait le plus cher et se verrait le moins : doubler les
 * dettes des familles. Ces cas fixent le comportement — les tranches précédentes sont effacées
 * avant que les nouvelles ne soient produites, et la refonte est refusée dès qu'un encaissement a
 * eu lieu.
 */
@Transactional
class FeeScheduleRedefinitionTest extends AbstractIntegrationTest {

    @Autowired
    private FeeService feeService;
    @Autowired
    private FeeScheduleService feeScheduleService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private LevelOfStudyEntity level;
    private StudentEntity student;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.level = this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code("CM1-" + UUID.randomUUID().toString().substring(0, 6)).position(1)
                .name(TranslateEntity.builder().fr("CM1").en("CM1").build())
                .build());
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.school.getLevelOfStudies().add(this.level);
        this.school = this.establishmentRepository.saveAndFlush(this.school);
        // L'élève existe avant le frais : c'est à la création du frais que sa dette est produite.
        this.student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName("KOUAMÉ")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2014, 2, 9))
                .levelOfStudy(this.level).establishment(this.school).build());
        this.authenticate();
    }

    @Test
    @DisplayName("redéfinir l'échéancier remplace les tranches au lieu de les empiler")
    void redefiningReplacesInstallments() throws Exception {
        UUID feeId = this.fee(new BigDecimal("90000"));

        this.feeScheduleService.defineSchedules(feeId, List.of(
                this.tranche("1er versement", "45000", LocalDate.of(2026, 10, 15)),
                this.tranche("2e versement", "45000", LocalDate.of(2027, 1, 15))));
        assertThat(this.installmentsOfStudent()).hasSize(2);

        this.feeScheduleService.defineSchedules(feeId, List.of(
                this.tranche("1er versement", "30000", LocalDate.of(2026, 10, 15)),
                this.tranche("2e versement", "30000", LocalDate.of(2027, 1, 15)),
                this.tranche("3e versement", "30000", LocalDate.of(2027, 4, 15))));

        // Trois, et non cinq : la famille doit 90 000 F, pas 180 000.
        List<InstallmentEntity> installments = this.installmentsOfStudent();
        assertThat(installments).hasSize(3);
        assertThat(installments.stream()
                .map(InstallmentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(new BigDecimal("90000"));
    }

    @Test
    @DisplayName("un échéancier dont le total ne fait pas le prix du frais est refusé")
    void refusesAScheduleThatDoesNotAddUp() throws Exception {
        UUID feeId = this.fee(new BigDecimal("90000"));

        // Laisserait une dette impossible à solder, ou ferait payer trop.
        assertThatThrownBy(() -> this.feeScheduleService.defineSchedules(feeId, List.of(
                this.tranche("1er versement", "45000", LocalDate.of(2026, 10, 15)))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("l'échéancier ne se refond plus dès qu'une tranche a été encaissée")
    void refusesToRedefineOnceCollected() throws Exception {
        UUID feeId = this.fee(new BigDecimal("90000"));
        this.feeScheduleService.defineSchedules(feeId, List.of(
                this.tranche("1er versement", "45000", LocalDate.of(2026, 10, 15)),
                this.tranche("2e versement", "45000", LocalDate.of(2027, 1, 15))));

        InstallmentEntity first = this.installmentsOfStudent().getFirst();
        first.setStatus(InstallmentStatus.PAID);
        this.installmentRepository.saveAndFlush(first);

        // Refondre fausserait la comptabilité de l'école et ce que les familles ont déjà vu.
        assertThatThrownBy(() -> this.feeScheduleService.defineSchedules(feeId, List.of(
                this.tranche("versement unique", "90000", LocalDate.of(2026, 10, 15)))))
                .isInstanceOf(ValidationException.class);
    }

    private List<InstallmentEntity> installmentsOfStudent() {
        return this.installmentRepository.findAll().stream()
                .filter(line -> line.getStudentFee().getStudent().getId().equals(this.student.getId()))
                .toList();
    }

    private UUID fee(BigDecimal price) throws Exception {
        FeeCreationForm form = new FeeCreationForm();
        form.setName("Scolarité " + UUID.randomUUID().toString().substring(0, 6));
        form.setPrice(price);
        form.setAcademical(true);
        form.setLevelOfStudiesCodes(Set.of(this.level.getCode()));
        return this.feeService.create(form).getId();
    }

    private FeeScheduleForm tranche(String label, String amount, LocalDate dueDate) {
        return FeeScheduleForm.builder()
                .label(label).amount(new BigDecimal(amount)).dueDate(dueDate).build();
    }

    private void authenticate() {
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(this.school)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
