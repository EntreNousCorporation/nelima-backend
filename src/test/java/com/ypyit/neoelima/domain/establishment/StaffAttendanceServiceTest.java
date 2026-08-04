package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.AttendanceSummaryDto;
import com.ypyit.neoelima.domain.establishment.dto.StaffAttendanceDto;
import com.ypyit.neoelima.domain.establishment.dto.StaffDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.AttendanceStatus;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.establishment.form.StaffAttendanceForm;
import com.ypyit.neoelima.domain.establishment.form.StaffForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffAttendanceRepository;
import com.ypyit.neoelima.domain.establishment.service.StaffAttendanceService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pointage du personnel.
 *
 * <p>Deux exigences portent tout le reste : une seule ligne par personne et par jour, et une
 * feuille du matin qui montre d'abord ceux qu'on n'a pas encore pointés.
 */
@Transactional
class StaffAttendanceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private StaffService staffService;
    @Autowired
    private StaffAttendanceService attendanceService;
    @Autowired
    private StaffAttendanceRepository attendanceRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;

    private EstablishmentEntity school;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.authenticate("staff:read", "staff:write", "attendance:write");
    }

    @Test
    @DisplayName("la feuille du jour porte aussi les personnes pas encore pointées")
    void sheetIncludesUnpointedMembers() {
        this.member("KOUAMÉ");
        this.member("BROU");

        List<StaffAttendanceDto> sheet = this.attendanceService.sheet(LocalDate.now());

        // C'est le cas d'usage du matin : la feuille sert à trouver qui manque, pas à relire ceux
        // qu'on a déjà traités.
        assertThat(sheet).hasSize(2);
        assertThat(sheet).allSatisfy(line -> assertThat(line.getStatus()).isNull());
    }

    @Test
    @DisplayName("repointer la même journée corrige la ligne au lieu d'en créer une seconde")
    void recordingTwiceUpdatesTheSameDay() {
        StaffDto member = this.member("ZOKOU");
        UUID id = UUID.fromString(member.getId());
        LocalDate day = LocalDate.now();

        this.attendanceService.record(id, this.form(day, AttendanceStatus.ABSENT, null));
        StaffAttendanceDto corrected = this.attendanceService
                .record(id, this.form(day, AttendanceStatus.LATE, "arrivé à 8 h 20"));

        assertThat(corrected.getStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(this.attendanceRepository.findByStaff_Establishment_IdAndDay(
                this.school.getId(), day)).hasSize(1);
    }

    @Test
    @DisplayName("une journée future ne se pointe pas")
    void refusesAFutureDay() {
        StaffDto member = this.member("DIABATÉ");

        // Pointer demain affirmerait une présence que personne n'a constatée.
        assertThatThrownBy(() -> this.attendanceService.record(UUID.fromString(member.getId()),
                this.form(LocalDate.now().plusDays(1), AttendanceStatus.PRESENT, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("le congé ne pèse pas sur le taux de présence")
    void leaveDoesNotWeighOnTheRate() {
        StaffDto present = this.member("AKA");
        StaffDto onLeave = this.member("YEO");
        LocalDate day = LocalDate.now();

        this.attendanceService.record(UUID.fromString(present.getId()),
                this.form(day, AttendanceStatus.PRESENT, null));
        this.attendanceService.record(UUID.fromString(onLeave.getId()),
                this.form(day, AttendanceStatus.LEAVE, "congé maternité"));

        AttendanceSummaryDto summary = this.attendanceService.summary(YearMonth.from(day));

        // Compter le congé en absence ferait plonger le taux des mois de vacances scolaires.
        assertThat(summary.getLeave()).isEqualTo(1);
        assertThat(summary.getPresenceRate()).isEqualByComparingTo(new BigDecimal("100.0"));
    }

    @Test
    @DisplayName("sans aucun pointage, le taux n'est pas nul mais indéterminé")
    void ratesNothingWhenNothingWasRecorded() {
        this.member("SANOGO");

        AttendanceSummaryDto summary = this.attendanceService.summary(YearMonth.now());

        // Zéro se lirait comme « personne n'est venu » ; l'absence de taux dit « on n'a pas pointé ».
        assertThat(summary.getPresenceRate()).isNull();
    }

    @Test
    @DisplayName("le membre d'une autre école ne se pointe pas")
    void refusesAMemberFromAnotherSchool() {
        StaffDto stranger = this.member("EHOUMAN");

        EstablishmentEntity other = this.establishmentRepository.saveAndFlush(
                EstablishmentEntity.builder().name("École " + UUID.randomUUID())
                        .active(true).isPrimary(true).build());
        this.school = other;
        this.authenticate("staff:read", "attendance:write");

        assertThatThrownBy(() -> this.attendanceService.record(UUID.fromString(stranger.getId()),
                this.form(LocalDate.now(), AttendanceStatus.PRESENT, null)))
                .isInstanceOf(RuntimeException.class);
    }

    private StaffAttendanceForm form(LocalDate day, AttendanceStatus status, String note) {
        StaffAttendanceForm form = new StaffAttendanceForm();
        form.setDay(day);
        form.setStatus(status);
        form.setNote(note);
        return form;
    }

    private StaffDto member(String lastName) {
        StaffForm form = new StaffForm();
        form.setFirstName("Awa");
        form.setLastName(lastName);
        form.setRole(StaffRole.TEACHER);
        return this.staffService.create(form);
    }

    private void authenticate(String... permissions) {
        Set<PermissionEntity> granted = new HashSet<>();
        for (String code : permissions) {
            granted.add(this.permissionRepository.findByCode(code)
                    .orElseGet(() -> this.permissionRepository.saveAndFlush(PermissionEntity.builder()
                            .code(code)
                            .name(TranslateEntity.builder().fr(code).en(code).build())
                            .build())));
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
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
