package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.FeeDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.FeeCreationForm;
import com.ypyit.neoelima.domain.establishment.form.FeeScheduleForm;
import com.ypyit.neoelima.domain.establishment.form.FeeUpdateForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
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
 * Modifier un frais déjà créé.
 *
 * <p>Un frais créé sur le mauvais niveau ou au mauvais montant ne se rattrapait qu'en le
 * supprimant : la route de modification ignorait les niveaux. Or changer de niveau n'est pas
 * changer un champ — un frais engendre une ligne par élève du niveau, et ces lignes portent des
 * échéanciers, parfois déjà réglés.
 *
 * <p>D'où l'asymétrie que ces cas figent : <strong>ajouter</strong> un niveau crée les lignes
 * manquantes, <strong>retirer</strong> un niveau ne défait que ce qui n'engage aucun argent.
 */
@Transactional
class FeeUpdateTest extends AbstractIntegrationTest {

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
    private StudentFeeRepository studentFeeRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private LevelOfStudyEntity cp1;
    private LevelOfStudyEntity cp2;
    private StudentEntity inCp1;
    private StudentEntity inCp2;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.cp1 = this.level("CP1");
        this.cp2 = this.level("CP2");

        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École frais " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.school.getLevelOfStudies().addAll(Set.of(this.cp1, this.cp2));
        this.school = this.establishmentRepository.saveAndFlush(this.school);

        this.inCp1 = this.student("Awa", this.cp1);
        this.inCp2 = this.student("Sekou", this.cp2);
        this.authenticate();
    }

    @Test
    @DisplayName("le nom et le montant se corrigent")
    void editsNameAndPrice() throws Exception {
        UUID feeId = this.fee("Scolarité", new BigDecimal("90000"), Set.of(this.cp1.getCode()));

        FeeDto updated = this.feeService.update(feeId, FeeUpdateForm.builder()
                .name("Scolarité annuelle").price(new BigDecimal("120000")).build());

        assertThat(updated.getName()).isEqualTo("Scolarité annuelle");
        assertThat(updated.getPrice()).isEqualByComparingTo("120000");
    }

    @Test
    @DisplayName("ajouter un niveau crée la dette des élèves qui y sont")
    void addingALevelCreatesTheMissingStudentFees() throws Exception {
        UUID feeId = this.fee("Cantine", new BigDecimal("30000"), Set.of(this.cp1.getCode()));
        assertThat(this.studentsOwing(feeId)).containsExactly(this.inCp1.getId());

        this.feeService.update(feeId, FeeUpdateForm.builder()
                .levelOfStudiesCodes(Set.of(this.cp1.getCode(), this.cp2.getCode())).build());

        // Sans cela, le niveau figurerait sur le frais sans qu'aucun élève ne le doive.
        assertThat(this.studentsOwing(feeId))
                .containsExactlyInAnyOrder(this.inCp1.getId(), this.inCp2.getId());
    }

    @Test
    @DisplayName("retirer un niveau détache les élèves qui n'ont pas d'échéancier")
    void removingALevelDetachesStudentsWithoutSchedule() throws Exception {
        UUID feeId = this.fee("Transport", new BigDecimal("20000"),
                Set.of(this.cp1.getCode(), this.cp2.getCode()));

        this.feeService.update(feeId, FeeUpdateForm.builder()
                .levelOfStudiesCodes(Set.of(this.cp1.getCode())).build());

        assertThat(this.studentsOwing(feeId)).containsExactly(this.inCp1.getId());
    }

    @Test
    @DisplayName("retirer un niveau est refusé dès qu'un échéancier existe")
    void refusesToRemoveALevelThatCarriesASchedule() throws Exception {
        UUID feeId = this.fee("Fournitures", new BigDecimal("40000"),
                Set.of(this.cp1.getCode(), this.cp2.getCode()));
        this.feeScheduleService.defineSchedules(feeId, List.of(FeeScheduleForm.builder()
                .label("versement unique").amount(new BigDecimal("40000"))
                .dueDate(LocalDate.of(2026, 10, 15)).build()));

        // L'échéancier porte des montants, des dates, parfois un règlement déjà encaissé : le
        // supprimer effacerait de l'argent qu'une famille a versé.
        assertThatThrownBy(() -> this.feeService.update(feeId, FeeUpdateForm.builder()
                .levelOfStudiesCodes(Set.of(this.cp1.getCode())).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("échéancier");

        assertThat(this.studentsOwing(feeId))
                .as("le refus doit être total : rien ne doit avoir été détaché au passage")
                .containsExactlyInAnyOrder(this.inCp1.getId(), this.inCp2.getId());
    }

    @Test
    @DisplayName("ne pas fournir de niveaux les laisse tels quels")
    void leavesLevelsUntouchedWhenAbsent() throws Exception {
        UUID feeId = this.fee("Assurance", new BigDecimal("5000"),
                Set.of(this.cp1.getCode(), this.cp2.getCode()));

        // Un ensemble vide voudrait dire « retirer tous les niveaux » : une école qui corrige un
        // montant ne s'attend pas à voir son frais se détacher de toutes ses classes.
        this.feeService.update(feeId, FeeUpdateForm.builder().price(new BigDecimal("6000")).build());

        assertThat(this.studentsOwing(feeId))
                .containsExactlyInAnyOrder(this.inCp1.getId(), this.inCp2.getId());
    }

    private List<UUID> studentsOwing(UUID feeId) {
        return this.studentFeeRepository.findByFee_Id(feeId).stream()
                .map(studentFee -> studentFee.getStudent().getId()).toList();
    }

    private UUID fee(String name, BigDecimal price, Set<String> levels) throws Exception {
        FeeCreationForm form = new FeeCreationForm();
        form.setName(name + " " + UUID.randomUUID().toString().substring(0, 6));
        form.setPrice(price);
        form.setAcademical(true);
        form.setLevelOfStudiesCodes(levels);
        return this.feeService.create(form).getId();
    }

    private LevelOfStudyEntity level(String prefix) {
        String code = prefix + "-" + UUID.randomUUID().toString().substring(0, 6);
        return this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code(code).position(1)
                .name(TranslateEntity.builder().fr(prefix).en(prefix).build()).build());
    }

    private StudentEntity student(String firstName, LevelOfStudyEntity level) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName(firstName).lastName("KOUAMÉ")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2014, 2, 9))
                .levelOfStudy(level).establishment(this.school).build());
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
