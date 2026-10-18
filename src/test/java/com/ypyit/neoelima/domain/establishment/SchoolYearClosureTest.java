package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.SchoolYearClosureDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolEventEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.SchoolEventKind;
import com.ypyit.neoelima.domain.establishment.enums.SchoolYearStatus;
import com.ypyit.neoelima.domain.establishment.form.FeeUpdateForm;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolEventRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.FeeService;
import com.ypyit.neoelima.domain.establishment.service.SchoolEventService;
import com.ypyit.neoelima.domain.establishment.service.SchoolYearClosureService;
import com.ypyit.neoelima.domain.authentication.service.AuthService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
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
 * Clôture d'une année et lecture seule.
 *
 * <p>Une école clôture son année passée quand tout est encaissé, et seulement alors ; ensuite plus
 * rien n'y change, ni depuis l'année clôturée qu'on consulte, ni depuis l'année en cours par l'API.
 */
@Transactional
@AutoConfigureMockMvc
class SchoolYearClosureTest extends AbstractIntegrationTest {

    @Autowired
    private SchoolYearClosureService closureService;
    @Autowired
    private FeeService feeService;
    @Autowired
    private SchoolEventService schoolEventService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private SchoolYearRepository schoolYearRepository;
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
    private SchoolEventRepository schoolEventRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private AcademicYearEntity previous;
    private AcademicYearEntity current;
    private String email;
    private InstallmentEntity unpaid;
    private FeeEntity fee;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.previous = this.year("2025-2026", 2025, false);
        this.current = this.year("2026-2027", 2026, true);
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(this.school)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(this.email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(this.email, "n/a", List.of()));

        // L'an dernier : une tranche encaissée, une autre encore due.
        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName("KONE").registrationNumber("2025001").establishment(this.school).build());
        this.fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité").price(new BigDecimal("200000")).academical(true)
                .establishment(this.school).academicYear(this.previous).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(this.fee).build());
        this.installment(studentFee, InstallmentStatus.PAID);
        this.unpaid = this.installment(studentFee, InstallmentStatus.PENDING);
    }

    @Test
    @DisplayName("on ne clôture pas tant qu'il reste à encaisser, et l'aperçu dit qui doit quoi")
    void refusesWhileSomethingIsDue() {
        SchoolYearClosureDto preview = this.closureService.preview(this.previous.getId());

        assertThat(preview.isClosable()).isFalse();
        assertThat(preview.getPendingCount()).isEqualTo(1);
        assertThat(preview.getPendingAmount()).isEqualByComparingTo("100000");
        assertThat(preview.getOutstanding()).extracting(SchoolYearClosureDto.Outstanding::getRegistrationNumber)
                .containsExactly("2025001");
        assertThatThrownBy(() -> this.closureService.close(this.previous.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("l'année en cours ne se clôture pas")
    void currentYearCannotBeClosed() {
        assertThat(this.closureService.preview(this.current.getId()).isClosable()).isFalse();
        assertThatThrownBy(() -> this.closureService.close(this.current.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("tout encaissé, l'année se clôture ; ses données ne se modifient plus, même depuis l'année en cours")
    void closedYearIsReadOnly() {
        this.settleEverything();
        SchoolEventEntity event = this.schoolEventRepository.saveAndFlush(SchoolEventEntity.builder()
                .title("Kermesse").kind(SchoolEventKind.values()[0]).date(LocalDate.of(2026, 6, 1))
                .establishment(this.school).academicYear(this.previous).build());

        SchoolYearClosureDto closed = this.closureService.close(this.previous.getId());

        assertThat(closed.getStatus()).isEqualTo(SchoolYearStatus.CLOSED);
        assertThat(this.schoolYearRepository.findByEstablishment_IdAndAcademicYear_Id(
                this.school.getId(), this.previous.getId()).orElseThrow().getClosedBy()).isNotBlank();
        FeeUpdateForm rename = new FeeUpdateForm();
        rename.setName("Scolarité renommée");
        assertThatThrownBy(() -> this.feeService.update(this.fee.getId(), rename))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> this.schoolEventService.delete(event.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("qui consulte une année clôturée ne peut rien écrire, mais peut changer d'année")
    void consultingAClosedYearIsReadOnly() throws Exception {
        this.settleEverything();
        this.closureService.close(this.previous.getId());
        String token = this.authService.generateToken(this.email).getAccessToken();

        this.mockMvc.perform(MockMvcRequestBuilders.put("/academic-years/consulted")
                        .param("academicYearId", this.previous.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(204));

        int write = this.mockMvc.perform(MockMvcRequestBuilders.post("/classes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CM1 B\",\"capacity\":30}"))
                .andReturn().getResponse().getStatus();
        assertThat(write).isEqualTo(400);

        // Le retour à l'année en cours reste permis.
        this.mockMvc.perform(MockMvcRequestBuilders.put("/academic-years/consulted")
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(204));
    }

    private void settleEverything() {
        this.unpaid.setStatus(InstallmentStatus.PAID);
        this.installmentRepository.saveAndFlush(this.unpaid);
    }

    private InstallmentEntity installment(StudentFeeEntity studentFee, InstallmentStatus status) {
        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Tranche").amount(new BigDecimal("100000"))
                .dueDate(LocalDate.of(2025, 10, 5)).status(status).build());
    }

    private AcademicYearEntity year(String label, int startYear, boolean active) {
        return this.academicYearRepository.saveAndFlush(AcademicYearEntity.builder()
                .label(label).startDate(LocalDate.of(startYear, 9, 1))
                .endDate(LocalDate.of(startYear + 1, 7, 31)).active(active).build());
    }
}
