package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.form.InstallmentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.StudentFeeSearchForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.InstallmentService;
import com.ypyit.neoelima.domain.establishment.service.StudentFeeService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
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
 * Ce qu'un parent a le droit de lire.
 *
 * <p>Ces cas manquaient, et leur absence a coûté cher : la portée « établissement » était dérivée
 * pour tout appelant, si bien qu'un parent recevait un 403 sur les échéances de son propre enfant.
 * L'application mobile n'avait donc rien à afficher et aucun paiement n'était possible, alors que
 * les tests côté école passaient tous.
 *
 * <p>Le pendant du même contrôle — un parent ne doit rien voir de l'enfant d'un autre — est vérifié
 * ici aussi : les deux erreurs se corrigent au même endroit et l'une se répare facilement en
 * cassant l'autre.
 */
@Transactional
class ParentDataAccessTest extends AbstractIntegrationTest {

    private static final LocalDate BIRTH_DAY = LocalDate.of(2012, 4, 3);

    @Autowired
    private InstallmentService installmentService;
    @Autowired
    private StudentFeeService studentFeeService;
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

    private StudentEntity myChild;
    private StudentEntity someoneElsesChild;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        EstablishmentEntity school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École parcours parent " + UUID.randomUUID()).active(true).build());
        myChild = aStudentWithADueInstallment(school, "Aaron");
        someoneElsesChild = aStudentWithADueInstallment(school, "Bintou");

        String parentEmail = "parent-" + UUID.randomUUID() + "@gmail.com";
        StudentParentUserEntity parent = userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .contacts(primaryEmail(parentEmail)).build());
        myChild.getParentUsers().add(parent);
        myChild = studentRepository.saveAndFlush(myChild);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(parentEmail, "n/a", List.of()));
    }

    @Test
    @DisplayName("le parent voit les échéances de son enfant")
    void parentSeesTheInstallmentsOfTheirChild() {
        var found = installmentService.findAll(
                InstallmentSearchForm.builder().studentId(myChild.getId()).build(),
                PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().getFirst().getAmount())
                .isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    @DisplayName("le parent ne voit pas les échéances de l'enfant d'un autre")
    void parentCannotSeeAnotherChildsInstallments() {
        assertThatThrownBy(() -> installmentService.findAll(
                InstallmentSearchForm.builder().studentId(someoneElsesChild.getId()).build(),
                PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("le parent voit les frais de son enfant")
    void parentSeesTheFeesOfTheirChild() {
        var found = studentFeeService.findAll(
                StudentFeeSearchForm.builder().studentId(myChild.getId()).build(),
                PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("le parent ne voit pas les frais de l'enfant d'un autre")
    void parentCannotSeeAnotherChildsFees() {
        assertThatThrownBy(() -> studentFeeService.findAll(
                StudentFeeSearchForm.builder().studentId(someoneElsesChild.getId()).build(),
                PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("sans élève désigné, le parent obtient les échéances de ses enfants et d'eux seuls")
    void parentWithoutStudentIdSeesOnlyTheirOwnChildren() {
        // L'écran « Échéances » interroge sans désigner d'enfant. Le périmètre est alors la liste
        // des enfants rattachés : ni rien — ce qui obligerait à un appel par enfant — ni la
        // plateforme entière.
        var found = installmentService.findAll(
                InstallmentSearchForm.builder().build(), PageRequest.of(0, 20));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().getFirst().getStudentFee().getStudent().getId())
                .isEqualTo(myChild.getId());
    }

    @Test
    @DisplayName("un parent sans enfant rattaché n'obtient rien, et surtout pas tout")
    void parentWithoutAnyChildGetsNothing() {
        // Le cas qui compte : sans enfant, un filtre omis retournerait toutes les tranches de la
        // plateforme au lieu d'aucune.
        authenticateAsAParentWithoutChildren();

        var found = installmentService.findAll(
                InstallmentSearchForm.builder().build(), PageRequest.of(0, 20));

        assertThat(found.getContent()).isEmpty();
        assertThat(found.getTotalElements()).isZero();
    }

    private void authenticateAsAParentWithoutChildren() {
        String email = "parent-sans-enfant-" + UUID.randomUUID() + "@gmail.com";
        userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Awa").lastName("Bamba")
                .contacts(primaryEmail(email)).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    private StudentEntity aStudentWithADueInstallment(EstablishmentEntity school, String firstName) {
        StudentEntity student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName(firstName).lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(BIRTH_DAY).establishment(school).build());
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("500"))
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement unique").amount(new BigDecimal("500"))
                .status(InstallmentStatus.PENDING).build());
        return student;
    }

    /**
     * Ensemble mutable, et non {@code Set.of} : lors d'un merge en cascade Hibernate appelle
     * {@code clear()} sur la collection, ce qu'une collection immuable refuse.
     */
    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(List.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
