package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.CarryOverDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.DebtCarryOverService;
import com.ypyit.neoelima.domain.establishment.service.SchoolYearClosureService;
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
 * Report des impayés : la dette passe sur l'année suivante, l'année d'origine peut se clôturer.
 */
@Transactional
class DebtCarryOverTest extends AbstractIntegrationTest {

    @Autowired
    private DebtCarryOverService carryOverService;
    @Autowired
    private SchoolYearClosureService closureService;
    @Autowired
    private AcademicYearRepository academicYearRepository;
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

    private AcademicYearEntity previous;
    private AcademicYearEntity current;
    private InstallmentEntity paid;
    private InstallmentEntity first;
    private InstallmentEntity second;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.previous = this.year("2031-2032", 2031, false);
        this.current = this.year("2032-2033", 2032, true);
        EstablishmentEntity school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));

        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName("KONE").registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .establishment(school).build());
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité").price(new BigDecimal("300000")).academical(true)
                .establishment(school).academicYear(this.previous).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        this.paid = this.installment(studentFee, "1re tranche", InstallmentStatus.PAID);
        this.first = this.installment(studentFee, "2e tranche", InstallmentStatus.PENDING);
        this.second = this.installment(studentFee, "3e tranche", InstallmentStatus.PENDING);
    }

    @Test
    @DisplayName("les tranches dues passent sur l'année suivante et l'année d'origine devient clôturable")
    void carriesOverAndUnblocksClosure() {
        assertThat(this.closureService.preview(this.previous.getId()).isClosable()).isFalse();
        assertThat(this.closureService.preview(this.previous.getId()).getNextYearLabel()).isEqualTo("2032-2033");

        CarryOverDto result = this.carryOverService.carryOver(this.previous.getId());

        assertThat(result.getInstallments()).isEqualTo(2);
        assertThat(result.getStudents()).isEqualTo(1);
        assertThat(result.getAmount()).isEqualByComparingTo("200000");
        assertThat(result.getTargetLabel()).isEqualTo("2032-2033");

        InstallmentEntity origin = this.installmentRepository.findById(this.first.getId()).orElseThrow();
        assertThat(origin.getStatus()).isEqualTo(InstallmentStatus.CARRIED_OVER);
        InstallmentEntity carried = origin.getCarriedTo();
        assertThat(carried.getStatus()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(carried.getAmount()).isEqualByComparingTo("100000");
        assertThat(carried.getLabel()).isEqualTo("Scolarité — 2e tranche");
        assertThat(carried.getDueDate()).isEqualTo(this.current.getStartDate());
        assertThat(carried.getStudentFee().getFee().getName()).isEqualTo("Report 2031-2032");
        assertThat(carried.getStudentFee().getFee().getAcademicYear().getId()).isEqualTo(this.current.getId());
        assertThat(carried.getStudentFee().getFee().isOptional())
                .as("le frais de report ne doit s'appliquer à personne d'office").isTrue();

        assertThat(this.installmentRepository.findById(this.paid.getId()).orElseThrow().getStatus())
                .as("ce qui est encaissé ne bouge pas").isEqualTo(InstallmentStatus.PAID);
        assertThat(this.closureService.preview(this.previous.getId()).isClosable()).isTrue();

        assertThatThrownBy(() -> this.carryOverService.carryOver(this.previous.getId()))
                .as("rien à reporter une seconde fois").isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("l'année en cours ne se reporte pas")
    void currentYearIsNotCarriedOver() {
        assertThatThrownBy(() -> this.carryOverService.carryOver(this.current.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    private InstallmentEntity installment(StudentFeeEntity studentFee, String label, InstallmentStatus status) {
        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label(label).amount(new BigDecimal("100000"))
                .dueDate(LocalDate.of(2031, 10, 5)).status(status).build());
    }

    private AcademicYearEntity year(String label, int startYear, boolean active) {
        if (active) {
            // Une seule année active sur la plateforme : on désactive les autres, dans la transaction du test.
            this.academicYearRepository.findAll().stream().filter(AcademicYearEntity::isActive).forEach(y -> {
                y.setActive(false);
                this.academicYearRepository.saveAndFlush(y);
            });
        }
        return this.academicYearRepository.saveAndFlush(AcademicYearEntity.builder()
                .label(label).startDate(LocalDate.of(startYear, 9, 1))
                .endDate(LocalDate.of(startYear + 1, 7, 31)).active(active).build());
    }
}
