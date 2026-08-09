package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.PayrollSummaryDto;
import com.ypyit.neoelima.domain.establishment.dto.SchoolClassDto;
import com.ypyit.neoelima.domain.establishment.dto.StaffDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.enums.ContractType;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.establishment.form.SchoolClassForm;
import com.ypyit.neoelima.domain.establishment.form.StaffForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffRepository;
import com.ypyit.neoelima.domain.establishment.service.SchoolClassService;
import com.ypyit.neoelima.domain.establishment.service.StaffService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Répertoire du personnel.
 *
 * <p>Trois erreurs seraient coûteuses et silencieuses : lire le personnel d'une autre école, servir
 * un salaire à qui n'a pas le droit de le lire, et effacer une fiche dont des feuilles de présence
 * portent encore le nom.
 */
@Transactional
class StaffServiceTest extends AbstractIntegrationTest {

    @Autowired
    private StaffService staffService;
    @Autowired
    private SchoolClassService schoolClassService;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;

    private EstablishmentEntity school;
    private LevelOfStudyEntity level;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.level = this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code("CM2-" + UUID.randomUUID().toString().substring(0, 6)).position(1)
                .name(TranslateEntity.builder().fr("CM2").en("CM2").build())
                .build());
        this.school = this.school();
        this.authenticateOn(this.school, "staff:read", "staff:write", "staff:read_salary");
    }

    @Test
    @DisplayName("un membre créé porte sa fonction, son contrat et son intitulé de poste")
    void createsAMember() {
        StaffDto created = this.staffService.create(this.form("KOUAMÉ", "Adjoua"));

        assertThat(created.getLastName()).isEqualTo("KOUAMÉ");
        assertThat(created.getRole()).isEqualTo(StaffRole.TEACHER);
        assertThat(created.getContractType()).isEqualTo(ContractType.CDI);
        assertThat(created.getJobTitle()).isEqualTo("Instituteur CM2");
        assertThat(created.isActive()).isTrue();
        assertThat(created.getClasses()).isEmpty();
    }

    @Test
    @DisplayName("le personnel d'une autre école est invisible")
    void doesNotLeakOtherSchools() {
        this.staffService.create(this.form("KOUAMÉ", "Adjoua"));

        this.authenticateOn(this.school(), "staff:read", "staff:write");

        assertThat(this.staffService.findAll(false)).isEmpty();
    }

    @Test
    @DisplayName("la fiche d'une autre école ne se lit pas par son identifiant")
    void doesNotExposeAnotherSchoolMemberById() {
        StaffDto created = this.staffService.create(this.form("BROU", "Léa"));
        this.authenticateOn(this.school(), "staff:read");

        // NotFound et non Forbidden : distinguer les deux révélerait que la fiche existe.
        assertThatThrownBy(() -> this.staffService.findById(UUID.fromString(created.getId())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("sans staff:read_salary, ni le salaire ni la charge horaire ne sont servis")
    void hidesSalaryWithoutPermission() {
        this.staffService.create(this.form("ZOKOU", "Marc"));

        // Le secrétariat pointe les présences mais ne connaît pas les rémunérations. C'est la
        // charge utile qu'on vérifie, pas l'écran : un champ masqué côté portail reste lisible
        // par quiconque interroge l'API.
        this.authenticateOnSameSchool("staff:read", "attendance:write");
        StaffDto seen = this.staffService.findAll(false).getFirst();

        assertThat(seen.getMonthlySalary()).isNull();
        assertThat(seen.getWeeklyHours()).isNull();
        assertThat(seen.getLastName()).isEqualTo("ZOKOU");
    }

    @Test
    @DisplayName("avec staff:read_salary, le salaire est servi")
    void servesSalaryWithPermission() {
        this.staffService.create(this.form("DIABATÉ", "Rose"));

        this.authenticateOnSameSchool("staff:read", "staff:read_salary");
        StaffDto seen = this.staffService.findAll(false).getFirst();

        assertThat(seen.getMonthlySalary()).isEqualByComparingTo(new BigDecimal("265000"));
        assertThat(seen.getWeeklyHours()).isEqualTo(28);
    }

    @Test
    @DisplayName("une modification par un appelant sans droit sur le salaire ne l'efface pas")
    void doesNotWipeSalaryOnBlindUpdate() {
        StaffDto created = this.staffService.create(this.form("EHOUMAN", "Julie"));

        // Le secrétariat renvoie le formulaire sans le salaire, qu'il n'a jamais reçu. Sans
        // garde-fou, la rémunération disparaîtrait à la première correction de numéro de
        // téléphone.
        this.authenticateOnSameSchool("staff:read", "staff:write");
        StaffForm form = this.form("EHOUMAN", "Julie");
        form.setMonthlySalary(null);
        this.staffService.update(UUID.fromString(created.getId()), form);

        this.authenticateOnSameSchool("staff:read", "staff:read_salary");
        assertThat(this.staffService.findById(UUID.fromString(created.getId())).getMonthlySalary())
                .isEqualByComparingTo(new BigDecimal("265000"));
    }

    @Test
    @DisplayName("le rattachement aux classes n'accepte pas la classe d'une autre école")
    void refusesAClassFromAnotherSchool() {
        StaffDto member = this.staffService.create(this.form("YEO", "Christophe"));
        SchoolClassDto mine = this.schoolClassService.create(this.classForm("CM2 A"));

        // Une classe de l'école courante passe...
        StaffDto assigned = this.staffService.assignClasses(
                UUID.fromString(member.getId()), List.of(UUID.fromString(mine.getId())));
        assertThat(assigned.getClasses()).hasSize(1);

        // ...celle d'une autre école n'existe pas de son point de vue.
        EstablishmentEntity other = this.school();
        this.authenticateOn(other, "staff:read", "staff:write");
        SchoolClassDto stranger = this.schoolClassService.create(this.classForm("CM2 B"));
        this.authenticateOnSameSchool("staff:read", "staff:write");

        assertThatThrownBy(() -> this.staffService.assignClasses(
                UUID.fromString(member.getId()), List.of(UUID.fromString(stranger.getId()))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("la masse salariale est refusée à qui n'a pas le droit de lire les salaires")
    void refusesPayrollWithoutPermission() {
        this.staffService.create(this.form("KIPRÉ", "Auguste"));
        this.authenticateOnSameSchool("staff:read", "attendance:write");

        // Ce calcul n'a pas de version dégradée : une masse salariale amputée serait un chiffre
        // faux, pas un chiffre partiel.
        assertThatThrownBy(() -> this.staffService.payrollSummary())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("le salaire moyen ne porte que sur les fiches renseignées")
    void averagesOnlyOverKnownSalaries() {
        this.staffService.create(this.form("BEDIA", "Franck"));
        StaffForm withoutSalary = this.form("OUÉDRAOGO", "Rachel");
        withoutSalary.setMonthlySalary(null);
        this.staffService.create(withoutSalary);

        PayrollSummaryDto summary = this.staffService.payrollSummary();

        // Diviser par l'effectif entier afficherait une rémunération moyenne d'autant plus basse
        // que les fiches sont incomplètes, ce qui se lirait comme une information sur les salaires.
        assertThat(summary.getHeadcount()).isEqualTo(2);
        assertThat(summary.getPaidHeadcount()).isEqualTo(1);
        assertThat(summary.getAverageSalary()).isEqualByComparingTo(new BigDecimal("265000"));
        assertThat(summary.getMonthlyPayroll()).isEqualByComparingTo(new BigDecimal("265000"));
    }

    @Test
    @DisplayName("une fiche sans historique se supprime réellement")
    void deletesAMemberWithoutHistory() {
        StaffDto created = this.staffService.create(this.form("SANOGO", "Idrissa"));

        this.staffService.deactivate(UUID.fromString(created.getId()));

        assertThat(this.staffRepository.findById(UUID.fromString(created.getId()))).isEmpty();
    }

    @Test
    @DisplayName("le rattachement de classe n'accepte qu'un profil enseignant")
    void refusesClassAssignmentForNonTeacher() {
        StaffForm director = this.form("KOFFI", "Marc");
        director.setRole(StaffRole.DIRECTION);
        StaffDto member = this.staffService.create(director);
        SchoolClassDto mine = this.schoolClassService.create(this.classForm("CM2 C"));

        // Un membre de la direction n'intervient pas devant une classe : lui en rattacher une
        // n'aurait pas de sens, et le titulaire ne doit pouvoir être qu'un enseignant.
        assertThatThrownBy(() -> this.staffService.assignClasses(
                UUID.fromString(member.getId()), List.of(UUID.fromString(mine.getId()))))
                .isInstanceOf(BadRequestException.class);
    }

    private StaffForm form(String lastName, String firstName) {
        StaffForm form = new StaffForm();
        form.setFirstName(firstName);
        form.setLastName(lastName);
        form.setRole(StaffRole.TEACHER);
        form.setJobTitle("Instituteur CM2");
        form.setPhone("+225 01 33 87 21 06");
        form.setContractType(ContractType.CDI);
        form.setMonthlySalary(new BigDecimal("265000"));
        form.setWeeklyHours(28);
        form.setHiredAt(LocalDate.of(2019, 9, 2));
        return form;
    }

    private SchoolClassForm classForm(String name) {
        SchoolClassForm form = new SchoolClassForm();
        form.setName(name);
        form.setCapacity(35);
        form.setLevelOfStudyCode(this.level.getCode());
        return form;
    }

    private EstablishmentEntity school() {
        EstablishmentEntity establishment = this.establishmentRepository
                .saveAndFlush(EstablishmentEntity.builder()
                        .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        establishment.getLevelOfStudies().add(this.level);
        return this.establishmentRepository.saveAndFlush(establishment);
    }

    private void authenticateOnSameSchool(String... permissions) {
        this.authenticateOn(this.school, permissions);
    }

    /**
     * Ouvre une session sur cet établissement avec exactement ces permissions.
     *
     * <p>Le catalogue de rôles n'est pas semé dans la base de test : chaque cas fabrique le rôle
     * dont il a besoin, ce qui rend visible dans le test lui-même ce que l'appelant a le droit de
     * faire.
     */
    private void authenticateOn(EstablishmentEntity establishment, String... permissions) {
        Set<PermissionEntity> granted = Arrays.stream(permissions)
                .map(code -> this.permissionRepository.findByCode(code)
                        .orElseGet(() -> this.permissionRepository.saveAndFlush(PermissionEntity.builder()
                                .code(code)
                                .name(TranslateEntity.builder().fr(code).en(code).build())
                                .build())))
                .collect(Collectors.toCollection(HashSet::new));

        RoleEntity role = this.roleRepository.saveAndFlush(RoleEntity.builder()
                .code("ROLE-" + UUID.randomUUID())
                .name(TranslateEntity.builder().fr("Rôle de test").en("Test role").build())
                .permissions(granted)
                .build());

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
