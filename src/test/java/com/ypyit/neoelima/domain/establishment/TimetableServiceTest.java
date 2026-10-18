package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.SchoolClassDto;
import com.ypyit.neoelima.domain.establishment.dto.StaffDto;
import com.ypyit.neoelima.domain.establishment.dto.TimetableSlotDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.establishment.form.SchoolClassForm;
import com.ypyit.neoelima.domain.establishment.form.StaffForm;
import com.ypyit.neoelima.domain.establishment.form.TimetableSlotForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.SchoolClassService;
import com.ypyit.neoelima.domain.establishment.service.StaffService;
import com.ypyit.neoelima.domain.establishment.service.TimetableService;
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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Emploi du temps d'une classe.
 *
 * <p>Ce qui compte : une grille qui ne se contredit pas — ni deux cours à la fois dans une classe,
 * ni un enseignant à deux endroits — et qui reste celle de l'école qui l'a saisie.
 */
@Transactional
class TimetableServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TimetableService timetableService;
    @Autowired
    private SchoolClassService schoolClassService;
    @Autowired
    private StaffService staffService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;

    private UUID classA;
    private UUID classB;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.authenticateOn(this.school());
        this.classA = this.schoolClass("6e A");
        this.classB = this.schoolClass("6e B");
    }

    @Test
    @DisplayName("les créneaux se lisent triés par jour puis par heure")
    void listsSlotsInWeekOrder() {
        this.timetableService.create(this.classA, this.slot(DayOfWeek.TUESDAY, 8, 10, "Anglais", null));
        this.timetableService.create(this.classA, this.slot(DayOfWeek.MONDAY, 10, 12, "Français", null));
        this.timetableService.create(this.classA, this.slot(DayOfWeek.MONDAY, 8, 10, "Mathématiques", null));

        assertThat(this.timetableService.findByClass(this.classA))
                .extracting(TimetableSlotDto::getSubject)
                .containsExactly("Mathématiques", "Français", "Anglais");
    }

    @Test
    @DisplayName("deux cours qui se chevauchent dans la même classe sont refusés")
    void refusesOverlapInTheSameClass() {
        this.timetableService.create(this.classA, this.slot(DayOfWeek.MONDAY, 8, 10, "Mathématiques", null));

        assertThatThrownBy(() -> this.timetableService
                .create(this.classA, this.slot(DayOfWeek.MONDAY, 9, 11, "Français", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mathématiques");
    }

    @Test
    @DisplayName("deux cours qui s'enchaînent ne se chevauchent pas")
    void acceptsBackToBackSlots() {
        this.timetableService.create(this.classA, this.slot(DayOfWeek.MONDAY, 8, 10, "Mathématiques", null));
        this.timetableService.create(this.classA, this.slot(DayOfWeek.MONDAY, 10, 12, "Français", null));

        assertThat(this.timetableService.findByClass(this.classA)).hasSize(2);
    }

    @Test
    @DisplayName("un enseignant ne peut pas être dans deux classes à la fois")
    void refusesATeacherInTwoPlaces() {
        UUID teacher = this.teacher("KOFFI", "Serge");
        this.timetableService.create(this.classA, this.slot(DayOfWeek.MONDAY, 8, 10, "SVT", teacher));

        assertThatThrownBy(() -> this.timetableService
                .create(this.classB, this.slot(DayOfWeek.MONDAY, 9, 10, "SVT", teacher)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("6e A");
    }

    @Test
    @DisplayName("modifier un créneau ne le met pas en conflit avec lui-même")
    void updatingASlotIgnoresItself() {
        UUID teacher = this.teacher("KOFFI", "Serge");
        TimetableSlotDto created = this.timetableService
                .create(this.classA, this.slot(DayOfWeek.MONDAY, 8, 10, "SVT", teacher));

        TimetableSlotDto updated = this.timetableService.update(this.classA,
                UUID.fromString(created.getId()), this.slot(DayOfWeek.MONDAY, 8, 11, "SVT", teacher));

        assertThat(updated.getEndTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(updated.getTeacherName()).isEqualTo("KOFFI Serge");
    }

    @Test
    @DisplayName("une fin qui précède le début est refusée")
    void refusesAnInvertedSlot() {
        assertThatThrownBy(() -> this.timetableService
                .create(this.classA, this.slot(DayOfWeek.MONDAY, 10, 8, "SVT", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("la grille d'une autre école est invisible")
    void doesNotLeakOtherSchools() {
        this.timetableService.create(this.classA, this.slot(DayOfWeek.MONDAY, 8, 10, "SVT", null));

        this.authenticateOn(this.school());

        assertThatThrownBy(() -> this.timetableService.findByClass(this.classA))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("l'enseignant d'une autre école ne peut pas être désigné")
    void refusesATeacherFromAnotherSchool() {
        UUID ownTeacher = this.teacher("KOFFI", "Serge");
        this.authenticateOn(this.school());
        UUID foreignClass = this.schoolClass("CM2");

        assertThatThrownBy(() -> this.timetableService
                .create(foreignClass, this.slot(DayOfWeek.MONDAY, 8, 10, "SVT", ownTeacher)))
                .isInstanceOf(BadRequestException.class);
    }

    private TimetableSlotForm slot(DayOfWeek day, int from, int to, String subject, UUID teacherId) {
        TimetableSlotForm form = new TimetableSlotForm();
        form.setDayOfWeek(day);
        form.setStartTime(LocalTime.of(from, 0));
        form.setEndTime(LocalTime.of(to, 0));
        form.setSubject(subject);
        form.setTeacherId(teacherId);
        return form;
    }

    private UUID schoolClass(String name) {
        SchoolClassForm form = new SchoolClassForm();
        form.setName(name);
        form.setCapacity(40);
        SchoolClassDto created = this.schoolClassService.create(form);
        return UUID.fromString(created.getId());
    }

    private UUID teacher(String lastName, String firstName) {
        StaffForm form = new StaffForm();
        form.setFirstName(firstName);
        form.setLastName(lastName);
        form.setRole(StaffRole.TEACHER);
        StaffDto created = this.staffService.create(form);
        return UUID.fromString(created.getId());
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private void authenticateOn(EstablishmentEntity establishment) {
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(establishment)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
