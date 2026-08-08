package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.AcademicYearDto;
import com.ypyit.neoelima.domain.establishment.dto.CalendarEntryDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.CalendarEntryKind;
import com.ypyit.neoelima.domain.establishment.form.AcademicYearForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.AcademicYearService;
import com.ypyit.neoelima.domain.establishment.service.CalendarService;
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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Année scolaire et périodes.
 *
 * <p>Deux fautes seraient silencieuses : deux années actives — l'écran en montrerait une et le
 * calendrier l'autre — et un réglage qui ne règle rien, c'est-à-dire des trimestres saisis qui ne
 * paraissent nulle part.
 */
@Transactional
class AcademicYearServiceTest extends AbstractIntegrationTest {

    private static final LocalDate START = LocalDate.of(2025, 9, 15);
    private static final LocalDate END = LocalDate.of(2026, 7, 10);

    @Autowired
    private AcademicYearService academicYearService;
    @Autowired
    private CalendarService calendarService;
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
        this.school = this.school();
        this.authenticateOn(this.school, "settings:read", "settings:write", "calendar:read");
    }

    @Test
    @DisplayName("activer une année désactive la précédente")
    void onlyOneActiveYear() {
        this.academicYearService.create(this.form("2024-2025",
                LocalDate.of(2024, 9, 16), LocalDate.of(2025, 7, 11), true));
        this.academicYearService.create(this.form("2025-2026", START, END, true));

        List<AcademicYearDto> years = this.academicYearService.findAll();

        // Deux années actives feraient répondre deux jeux de bornes à la même question.
        assertThat(years).filteredOn(AcademicYearDto::isActive)
                .extracting(AcademicYearDto::getLabel).containsExactly("2025-2026");
        // La précédente est conservée : ses reçus et ses échéances y sont datés.
        assertThat(years).extracting(AcademicYearDto::getLabel)
                .containsExactly("2025-2026", "2024-2025");
    }

    @Test
    @DisplayName("les bornes des périodes paraissent au calendrier, sur la fenêtre demandée")
    void periodsSurfaceOnTheCalendar() {
        AcademicYearForm form = this.form("2025-2026", START, END, true);
        form.setPeriods(List.of(
                period("1er trimestre", START, LocalDate.of(2025, 12, 19)),
                period("2e trimestre", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 4, 3))));
        this.academicYearService.create(form);

        // Décembre : la fin du premier trimestre, et rien du deuxième.
        List<CalendarEntryDto> december = this.calendarService.findAll(
                LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31));

        assertThat(december).extracting(CalendarEntryDto::getTitle)
                .containsExactly("Fin — 1er trimestre");
        assertThat(december).allMatch(entry ->
                CalendarEntryKind.ACADEMIC_PERIOD.equals(entry.getKind()) && entry.isAllDay());

        // Septembre : le début du premier, pas sa fin. Ce sont les bornes qui sont posées, non la
        // durée — un trimestre étalé sur trois mois occuperait la grille sans rien apprendre.
        assertThat(this.calendarService.findAll(
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30)))
                .extracting(CalendarEntryDto::getTitle)
                .containsExactly("Début — 1er trimestre");
    }

    @Test
    @DisplayName("sans période déclarée, ce sont les bornes de l'année qui paraissent")
    void yearBoundsWhenNoPeriodDeclared() {
        this.academicYearService.create(this.form("2025-2026", START, END, true));

        assertThat(this.calendarService.findAll(
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30)))
                .extracting(CalendarEntryDto::getTitle)
                .containsExactly("Rentrée scolaire — 2025-2026");
    }

    @Test
    @DisplayName("une année inactive ne pose aucun repère au calendrier")
    void inactiveYearSurfacesNothing() {
        this.academicYearService.create(this.form("2025-2026", START, END, false));

        assertThat(this.calendarService.findAll(
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30))).isEmpty();
    }

    @Test
    @DisplayName("une période hors des bornes de l'année est refusée")
    void refusesAPeriodOutsideTheYear() {
        AcademicYearForm form = this.form("2025-2026", START, END, true);
        form.setPeriods(List.of(
                period("Trimestre fantôme", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 12, 19))));

        // Sinon le calendrier placerait un trimestre que l'année ne couvre pas.
        assertThatThrownBy(() -> this.academicYearService.create(form))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("l'année en cours ne se supprime pas")
    void refusesToDeleteTheActiveYear() {
        AcademicYearDto year = this.academicYearService.create(this.form("2025-2026", START, END, true));

        // L'école resterait sans repère jusqu'à ce qu'elle en active une autre, sans rien qui le
        // lui dise.
        assertThatThrownBy(() -> this.academicYearService.delete(UUID.fromString(year.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("l'année d'une autre école est introuvable")
    void doesNotLeakOtherSchools() {
        AcademicYearDto year = this.academicYearService.create(this.form("2025-2026", START, END, true));

        this.authenticateOn(this.school(), "settings:read", "settings:write", "calendar:read");

        assertThat(this.academicYearService.findAll()).isEmpty();
        assertThatThrownBy(() -> this.academicYearService.update(
                UUID.fromString(year.getId()), this.form("Détournée", START, END, true)))
                .isInstanceOf(NotFoundException.class);
    }

    /* ---------- fabriques ---------- */

    private AcademicYearForm form(String label, LocalDate start, LocalDate end, boolean active) {
        AcademicYearForm form = new AcademicYearForm();
        form.setLabel(label);
        form.setStartDate(start);
        form.setEndDate(end);
        form.setActive(active);
        return form;
    }

    private static AcademicYearForm.AcademicPeriodForm period(String label, LocalDate start, LocalDate end) {
        AcademicYearForm.AcademicPeriodForm period = new AcademicYearForm.AcademicPeriodForm();
        period.setLabel(label);
        period.setStartDate(start);
        period.setEndDate(end);
        return period;
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private void authenticateOn(EstablishmentEntity establishment, String... permissions) {
        Set<PermissionEntity> granted = new HashSet<>();
        for (String code : permissions) {
            granted.add(this.permissionRepository.findByCode(code)
                    .orElseGet(() -> this.permissionRepository.saveAndFlush(PermissionEntity.builder()
                            .code(code)
                            .name(TranslateEntity.builder().fr(code).en(code).build()).build())));
        }
        RoleEntity role = this.roleRepository.saveAndFlush(RoleEntity.builder()
                .code("ROLE-" + UUID.randomUUID())
                .name(TranslateEntity.builder().fr("Rôle de test").en("Test role").build())
                .permissions(granted).build());

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
