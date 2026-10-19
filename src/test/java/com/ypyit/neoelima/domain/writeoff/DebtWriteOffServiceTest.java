package com.ypyit.neoelima.domain.writeoff;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
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
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.PermissionRepository;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.writeoff.dto.WriteOffDto;
import com.ypyit.neoelima.domain.writeoff.enums.WriteOffStatus;
import com.ypyit.neoelima.domain.writeoff.form.WriteOffForms;
import com.ypyit.neoelima.domain.writeoff.service.DebtWriteOffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Passage en perte : la comptabilité propose, une autre personne de la direction tranche.
 */
@Transactional
class DebtWriteOffServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DebtWriteOffService service;
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
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;

    private EstablishmentEntity school;
    private InstallmentEntity installment;
    private String accountant;
    private String director;
    private String otherDirector;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).build());
        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("KOFFI").registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .establishment(this.school).build());
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("60000")).establishment(this.school).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        this.installment = this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("2e tranche").amount(new BigDecimal("60000"))
                .status(InstallmentStatus.PENDING).build());

        this.accountant = this.user(DebtWriteOffService.PROPOSE);
        this.director = this.user(DebtWriteOffService.PROPOSE, DebtWriteOffService.APPROVE);
        this.otherDirector = this.user(DebtWriteOffService.PROPOSE, DebtWriteOffService.APPROVE);
    }

    @Test
    @DisplayName("la tranche reste due tant que la direction n'a pas validé, puis passe en perte")
    void proposedThenApproved() {
        WriteOffDto proposal = this.propose(this.accountant);
        assertThat(proposal.getStatus()).isEqualTo(WriteOffStatus.PROPOSED);
        assertThat(this.status()).isEqualTo(InstallmentStatus.PENDING);

        this.login(this.director);
        assertThat(this.service.list()).singleElement().satisfies(w -> assertThat(w.isDecidable()).isTrue());
        WriteOffDto approved = this.service.approve(UUID.fromString(proposal.getId()), new WriteOffForms.Decision());

        assertThat(approved.getStatus()).isEqualTo(WriteOffStatus.APPROVED);
        assertThat(approved.getDecidedBy()).isNotBlank();
        assertThat(this.status()).isEqualTo(InstallmentStatus.WRITTEN_OFF);
    }

    @Test
    @DisplayName("on ne valide pas sa propre demande, même avec le droit de valider")
    void fourEyes() {
        WriteOffDto proposal = this.propose(this.director);
        assertThat(this.service.list()).singleElement().satisfies(w -> assertThat(w.isDecidable()).isFalse());
        assertThatThrownBy(() -> this.service.approve(UUID.fromString(proposal.getId()), new WriteOffForms.Decision()))
                .isInstanceOf(BadRequestException.class);

        this.login(this.otherDirector);
        this.service.approve(UUID.fromString(proposal.getId()), new WriteOffForms.Decision());
        assertThat(this.status()).isEqualTo(InstallmentStatus.WRITTEN_OFF);
    }

    @Test
    @DisplayName("la comptabilité ne peut pas valider, et un refus exige un motif et laisse la tranche due")
    void accountantCannotApproveAndRejectionNeedsReason() {
        WriteOffDto proposal = this.propose(this.accountant);
        assertThatThrownBy(() -> this.service.approve(UUID.fromString(proposal.getId()), new WriteOffForms.Decision()))
                .isInstanceOf(AccessDeniedException.class);

        this.login(this.director);
        assertThatThrownBy(() -> this.service.reject(UUID.fromString(proposal.getId()), new WriteOffForms.Decision()))
                .isInstanceOf(BadRequestException.class);
        WriteOffForms.Decision decision = new WriteOffForms.Decision();
        decision.setNote("La famille a promis de régler en juin.");
        assertThat(this.service.reject(UUID.fromString(proposal.getId()), decision).getStatus()).isEqualTo(WriteOffStatus.REJECTED);
        assertThat(this.status()).isEqualTo(InstallmentStatus.PENDING);
    }

    @Test
    @DisplayName("une tranche réglée entre la proposition et la décision n'est pas passée en perte")
    void paidMeanwhile() {
        WriteOffDto proposal = this.propose(this.accountant);
        this.installment.setStatus(InstallmentStatus.PAID);
        this.installmentRepository.saveAndFlush(this.installment);

        this.login(this.director);
        assertThatThrownBy(() -> this.service.approve(UUID.fromString(proposal.getId()), new WriteOffForms.Decision()))
                .isInstanceOf(BadRequestException.class);
        assertThat(this.status()).isEqualTo(InstallmentStatus.PAID);
        assertThat(this.service.list()).singleElement().satisfies(w -> assertThat(w.getStatus()).isEqualTo(WriteOffStatus.REJECTED));
    }

    @Test
    @DisplayName("une seule demande en cours par tranche")
    void oneOpenRequestPerInstallment() {
        this.propose(this.accountant);
        assertThatThrownBy(() -> this.propose(this.accountant)).isInstanceOf(BadRequestException.class);
    }

    private WriteOffDto propose(String email) {
        this.login(email);
        WriteOffForms.Proposal form = new WriteOffForms.Proposal();
        form.setInstallmentId(this.installment.getId());
        form.setReason("Famille partie sans adresse, relances sans réponse depuis six mois.");
        return this.service.propose(form);
    }

    private InstallmentStatus status() {
        return this.installmentRepository.findById(this.installment.getId()).orElseThrow().getStatus();
    }

    private String user(String... permissions) {
        Set<PermissionEntity> granted = new HashSet<>();
        for (String code : permissions) {
            granted.add(this.permissionRepository.findByCode(code)
                    .orElseGet(() -> this.permissionRepository.saveAndFlush(PermissionEntity.builder()
                            .code(code).name(TranslateEntity.builder().fr(code).en(code).build()).build())));
        }
        RoleEntity role = this.roleRepository.saveAndFlush(RoleEntity.builder()
                .code("ROLE-" + UUID.randomUUID())
                .name(TranslateEntity.builder().fr("Rôle de test").en("Test role").build())
                .permissions(granted)
                .build());
        String email = "user-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(this.school).role(role)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        return email;
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
