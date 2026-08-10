package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.form.FeeCreationForm;
import com.ypyit.neoelima.domain.establishment.form.InstallmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.InstallmentSearchForm;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.service.InstallmentService;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.service.FeeService;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test anti-régression de l'isolation multi-établissements.
 *
 * <p>Avant l'introduction de {@code CurrentUserProvider}, les services de recherche filtraient sur
 * l'{@code establishmentId} fourni par le client : un utilisateur d'établissement authentifié
 * pouvait lire les élèves d'une autre école en changeant le paramètre. Ces tests échouent si cette
 * dérivation depuis le principal est retirée.
 */
@Transactional
class TenantIsolationTest extends AbstractIntegrationTest {

    @Autowired
    private StudentService studentService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FeeService feeService;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private InstallmentService installmentService;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private StudentFeeRepository studentFeeRepository;

    private EstablishmentEntity victorLoba;
    private EstablishmentEntity sainteMarie;
    private StudentEntity aaronAtVictorLoba;
    private StudentEntity fatouAtSainteMarie;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        victorLoba = establishmentRepository.save(EstablishmentEntity.builder()
                .name("Victor Loba Abobo").active(true).build());
        sainteMarie = establishmentRepository.save(EstablishmentEntity.builder()
                .name("Sainte Marie Cocody").active(true).build());

        aaronAtVictorLoba = studentRepository.save(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi").registrationNumber("2022333")
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(victorLoba).build());
        fatouAtSainteMarie = studentRepository.save(StudentEntity.builder()
                .firstName("Fatou").lastName("Diallo").registrationNumber("2022444")
                .birthDay(LocalDate.of(2011, 9, 21)).establishment(sainteMarie).build());
    }

    @Test
    @DisplayName("un utilisateur d'établissement ne voit que les élèves de son établissement")
    void schoolUserOnlySeesOwnStudents() {
        authenticateAs(saveSchoolUser("secretaire@victorloba.ci", victorLoba));

        Page<StudentDto> result = studentService.search(
                StudentSearchForm.builder().build(), PageRequest.of(0, 50));

        assertThat(result.getContent())
                .extracting(StudentDto::getRegistrationNumber)
                .containsExactly(aaronAtVictorLoba.getRegistrationNumber());
    }

    @Test
    @DisplayName("cibler explicitement un autre établissement ne contourne pas le filtre")
    void schoolUserCannotTargetAnotherEstablishment() {
        authenticateAs(saveSchoolUser("secretaire@victorloba.ci", victorLoba));

        // La faille corrigée : cet identifiant était repris tel quel dans la requête.
        Page<StudentDto> result = studentService.search(
                StudentSearchForm.builder().establishmentId(sainteMarie.getId()).build(),
                PageRequest.of(0, 50));

        assertThat(result.getContent())
                .as("l'établissement demandé doit être ignoré au profit de celui du principal")
                .extracting(StudentDto::getRegistrationNumber)
                .containsExactly(aaronAtVictorLoba.getRegistrationNumber())
                .doesNotContain(fatouAtSainteMarie.getRegistrationNumber());
    }

    @Test
    @DisplayName("un parent n'a pas de portée établissement et ne peut pas lister d'élèves")
    void parentCannotBrowseEstablishmentStudents() {
        authenticateAs(saveParentUser("parent@gmail.com"));

        assertThatThrownBy(() -> studentService.search(
                StudentSearchForm.builder().establishmentId(victorLoba.getId()).build(),
                PageRequest.of(0, 50)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("un frais est créé dans l'établissement du principal, pas dans celui demandé")
    void feeIsCreatedInCallerEstablishment() {
        String levelCode = "CE2-" + UUID.randomUUID().toString().substring(0, 6);
        LevelOfStudyEntity level = levelOfStudyRepository.saveAndFlush(
                LevelOfStudyEntity.builder().code(levelCode).position(1).build());
        victorLoba.getLevelOfStudies().add(level);
        establishmentRepository.saveAndFlush(victorLoba);

        authenticateAs(saveSchoolUser("comptable@victorloba.ci", victorLoba));

        // L'établissement visé est celui d'une autre école : il doit être ignoré.
        feeService.create(FeeCreationForm.builder()
                .establishmentId(sainteMarie.getId())
                .name("Scolarité annuelle")
                .price(new java.math.BigDecimal("150000"))
                .levelOfStudiesCodes(java.util.Set.of(levelCode))
                .build());

        assertThat(feeRepository.findAll())
                .filteredOn(fee -> "Scolarité annuelle".equals(fee.getName()))
                .allSatisfy(fee -> assertThat(fee.getEstablishment().getId())
                        .as("le frais doit appartenir à l'école de l'utilisateur")
                        .isEqualTo(victorLoba.getId()));
    }

    @Test
    @DisplayName("on ne peut pas créer une tranche sur l'élève d'une autre école")
    void installmentCannotBeCreatedOnAnotherSchoolsStudent() {
        StudentFeeEntity fatouFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .name("Scolarité 2026").student(fatouAtSainteMarie).build());

        authenticateAs(saveSchoolUser("comptable@victorloba.ci", victorLoba));

        // La faille : `create` chargeait le frais d'élève par identifiant et posait la tranche, sans
        // aucun contrôle de périmètre — le seul garde-fou étant `hasAuthority('fee:write')`, que
        // tout comptable détient dans sa propre école. Un identifiant appartenant à une autre école
        // suffisait donc à y créer une dette. Et cette dette devient de l'argent réel : le tunnel de
        // paiement accepte toute tranche PENDING, et le reçu sort dans la séquence numérotée de
        // l'école d'en face.
        assertThatThrownBy(() -> installmentService.create(InstallmentCreationForm.builder()
                .amount(new java.math.BigDecimal("50000"))
                .studentFeeId(fatouFee.getId())
                .paymentId(UUID.randomUUID())
                .build()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(installmentRepository.findAll())
                .as("aucune tranche ne doit avoir été créée")
                .noneSatisfy(installment -> assertThat(installment.getStudentFee().getId())
                        .isEqualTo(fatouFee.getId()));
    }

    @Test
    @DisplayName("la même écriture passe sur un élève de sa propre école")
    void installmentIsCreatedOnOwnStudent() {
        StudentFeeEntity aaronFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .name("Scolarité 2026").student(aaronAtVictorLoba).build());

        authenticateAs(saveSchoolUser("comptable@victorloba.ci", victorLoba));

        // Le pendant du test précédent : une garde qui refuse tout serait passée inaperçue.
        assertThatCode(() -> installmentService.create(InstallmentCreationForm.builder()
                .amount(new java.math.BigDecimal("50000"))
                .studentFeeId(aaronFee.getId())
                .paymentId(UUID.randomUUID())
                .build()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la recherche de tranches filtre sur l'établissement sans échouer")
    void installmentSearchResolvesDeepEstablishmentPath() {
        authenticateAs(saveSchoolUser("comptable@victorloba.ci", victorLoba));

        // Le filtre traverse installment.studentFee.student.establishment. QueryDSL n'initialise
        // pas ce chemin par défaut : sans @QueryInit sur InstallmentEntity.studentFee, l'appel
        // échoue en NullPointerException à l'exécution alors qu'il compile sans avertissement.
        assertThatCode(() -> installmentService.findAll(
                InstallmentSearchForm.builder().build(), PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    private EstablishmentUserEntity saveSchoolUser(String email, EstablishmentEntity establishment) {
        return userRepository.save(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré")
                .establishment(establishment)
                .contacts(primaryEmail(email))
                .build());
    }

    private StudentParentUserEntity saveParentUser(String email) {
        return userRepository.save(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .contacts(primaryEmail(email))
                .build());
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return Set.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build());
    }

    private void authenticateAs(UserEntity user) {
        String username = user.getContacts().iterator().next().getValue();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }
}
