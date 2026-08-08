package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.ParentSummaryDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.ParentDashboardService;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Chiffres d'accueil de la famille.
 *
 * <p>Un tableau de bord qui se trompe ne tombe pas en panne : il affiche un montant, et personne ne
 * sait qu'il est faux. Les deux erreurs qui coûteraient le plus sont éprouvées ici — compter ce qui
 * n'est pas dû ce mois-ci, et compter l'enfant d'une autre famille.
 */
@Transactional
class ParentDashboardServiceTest extends AbstractIntegrationTest {

    private static final LocalDate BIRTH_DAY = LocalDate.of(2013, 9, 12);

    @Autowired
    private ParentDashboardService parentDashboardService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
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

    private EstablishmentEntity school;
    private StudentEntity myChild;
    private StudentEntity someoneElsesChild;
    private StudentParentUserEntity parent;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("Groupe scolaire " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.myChild = this.student("Marie");
        this.someoneElsesChild = this.student("Bintou");
        this.authenticateAsParentOf(this.myChild);
    }

    @Test
    @DisplayName("un compte sans enfant rend des zéros, pas une erreur")
    void emptyAccountIsNotAnError() {
        this.authenticateAsParentOf();

        ParentSummaryDto summary = this.parentDashboardService.summary();

        // C'est le premier écran d'une famille qui vient de s'inscrire : elle n'a encore rien
        // rattaché, et l'application doit pouvoir se dessiner.
        assertThat(summary.getChildrenCount()).isZero();
        assertThat(summary.getChildren()).isEmpty();
        assertThat(summary.getDueThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getOutstandingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getNextDueDate()).isNull();
    }

    @Test
    @DisplayName("« à régler ce mois-ci » ne retient que le mois courant, et rien de réglé")
    void thisMonthCountsOnlyWhatIsDueThisMonth() {
        LocalDate today = LocalDate.now();
        this.installment(this.myChild, today.withDayOfMonth(1).plusDays(13),
                new BigDecimal("150000"), InstallmentStatus.PENDING);
        this.installment(this.myChild, today.withDayOfMonth(1).plusMonths(1).plusDays(3),
                new BigDecimal("90000"), InstallmentStatus.PENDING);
        this.installment(this.myChild, today.withDayOfMonth(1).plusDays(2),
                new BigDecimal("40000"), InstallmentStatus.PAID);

        ParentSummaryDto summary = this.parentDashboardService.summary();

        // Le mois prochain n'est pas encore à régler, et ce qui est payé ne l'est plus. Additionner
        // les trois donnerait 280 000 — un montant que le parent ne reconnaîtrait pas.
        assertThat(summary.getDueThisMonth()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(summary.getDueThisMonthCount()).isEqualTo(1);
        // Le reste dû, lui, comprend bien le mois suivant.
        assertThat(summary.getOutstandingAmount()).isEqualByComparingTo(new BigDecimal("240000"));
    }

    @Test
    @DisplayName("la part déjà échue est isolée du reste dû")
    void overdueIsAPartOfOutstanding() {
        this.installment(this.myChild, LocalDate.now().minusDays(4),
                new BigDecimal("50000"), InstallmentStatus.PENDING);
        this.installment(this.myChild, LocalDate.now().plusDays(20),
                new BigDecimal("70000"), InstallmentStatus.PENDING);

        ParentSummaryDto summary = this.parentDashboardService.summary();

        assertThat(summary.getOverdueAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(summary.getOutstandingAmount()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(summary.getNextDueDate()).isEqualTo(LocalDate.now().minusDays(4));
    }

    @Test
    @DisplayName("l'enfant d'une autre famille n'entre ni dans la liste ni dans les totaux")
    void neverCountsSomeoneElsesChild() {
        this.installment(this.someoneElsesChild, LocalDate.now().plusDays(2),
                new BigDecimal("999000"), InstallmentStatus.PENDING);
        this.installment(this.myChild, LocalDate.now().plusDays(2),
                new BigDecimal("10000"), InstallmentStatus.PENDING);

        ParentSummaryDto summary = this.parentDashboardService.summary();

        assertThat(summary.getChildrenCount()).isEqualTo(1);
        assertThat(summary.getChildren()).extracting(ParentSummaryDto.ParentChildDto::getFirstName)
                .containsExactly("Marie");
        assertThat(summary.getOutstandingAmount()).isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    @DisplayName("chaque enfant porte son propre solde et sa propre échéance, le plus urgent en tête")
    void eachChildCarriesItsOwnBalance() {
        StudentEntity secondChild = this.student("Aaron");
        this.attachToParent(secondChild);
        this.installment(this.myChild, LocalDate.now().plusDays(20),
                new BigDecimal("30000"), InstallmentStatus.PENDING);
        this.installment(secondChild, LocalDate.now().plusDays(1),
                new BigDecimal("80000"), InstallmentStatus.PENDING);

        ParentSummaryDto summary = this.parentDashboardService.summary();

        // Le badge « J-1 » se calcule sur cette date : la donner par famille et non par enfant
        // ferait porter l'urgence de l'un au frère de l'autre.
        assertThat(summary.getChildren()).extracting(
                        ParentSummaryDto.ParentChildDto::getFirstName,
                        ParentSummaryDto.ParentChildDto::getNextDueDate)
                .containsExactly(
                        tuple("Aaron", LocalDate.now().plusDays(1)),
                        tuple("Marie", LocalDate.now().plusDays(20)));
        // Comparés par valeur : la base rend « 80000.00 », et l'égalité d'un BigDecimal tient
        // compte de l'échelle — un assert par égalité passerait ou non selon la colonne.
        assertThat(summary.getChildren().getFirst().getOutstandingAmount())
                .isEqualByComparingTo(new BigDecimal("80000"));
        assertThat(summary.getChildren().getLast().getOutstandingAmount())
                .isEqualByComparingTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("un enfant sans classe ni échéance reste visible, à zéro")
    void anUnassignedChildStaysVisible() {
        ParentSummaryDto summary = this.parentDashboardService.summary();

        // Une jointure interne l'aurait fait disparaître de sa propre liste — la pire réponse
        // possible à une répartition que l'école n'a pas encore faite.
        assertThat(summary.getChildren()).hasSize(1);
        assertThat(summary.getChildren().getFirst().getClassName()).isNull();
        assertThat(summary.getChildren().getFirst().getOutstandingAmount())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getChildren().getFirst().getEstablishmentName())
                .isEqualTo(this.school.getName());
    }

    @Test
    @DisplayName("la classe d'affectation est servie quand elle existe")
    void servesTheClassWhenAssigned() {
        SchoolClassEntity classroom = this.schoolClassRepository.saveAndFlush(
                SchoolClassEntity.builder().name("CM2 A").capacity(30)
                        .establishment(this.school).build());
        this.myChild.setSchoolClass(classroom);
        this.studentRepository.saveAndFlush(this.myChild);

        assertThat(this.parentDashboardService.summary().getChildren().getFirst().getClassName())
                .isEqualTo("CM2 A");
    }

    /* ---------- fabriques ---------- */

    private StudentEntity student(String firstName) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName(firstName).lastName("Kouassi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(BIRTH_DAY).establishment(this.school).build());
    }

    private void installment(StudentEntity student, LocalDate dueDate, BigDecimal amount,
                             InstallmentStatus status) {
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(amount)
                .establishment(this.school).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(
                StudentFeeEntity.builder().student(student).fee(fee).build());
        this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement")
                .amount(amount).dueDate(dueDate).status(status).build());
    }

    private void authenticateAsParentOf(StudentEntity... children) {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        this.parent = this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Didier").lastName("Kouassi")
                .contacts(new HashSet<>(List.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        for (StudentEntity child : children) {
            this.attachToParent(child);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    private void attachToParent(StudentEntity child) {
        Set<UserEntity> parents = new HashSet<>(child.getParentUsers());
        parents.add(this.parent);
        child.setParentUsers(parents);
        this.studentRepository.saveAndFlush(child);
    }
}
