package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.TimetableSlotDto;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import com.ypyit.neoelima.domain.establishment.entity.TimetableSlotEntity;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.establishment.form.TimetableSlotForm;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffRepository;
import com.ypyit.neoelima.domain.establishment.repository.TimetableSlotRepository;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Emploi du temps des classes : une semaine type par classe.
 *
 * <p>Deux contrôles font sa valeur, sans lesquels une grille saisie à la main se contredit dès la
 * première semaine : une classe n'a pas deux cours à la fois, et un enseignant n'est pas dans deux
 * salles à la fois. Le second est celui qu'on ne voit pas à l'écran — il traverse les classes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableService {

    private static final Locale FRENCH = Locale.FRENCH;

    private final TimetableSlotRepository timetableSlotRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StaffRepository staffRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AcademicYearContext academicYearContext;

    public List<TimetableSlotDto> findByClass(UUID classId) {
        this.loadClass(classId);
        // La grille de l'année consultée : chaque rentrée a la sienne.
        return this.timetableSlotRepository.findBySchoolClass_IdOrderByDayOfWeekAscStartTimeAsc(classId)
                .stream().filter(slot -> this.academicYearContext.inConsultedYear(slot.getAcademicYear()))
                .map(TimetableService::toDto).toList();
    }

    @Transactional
    public TimetableSlotDto create(UUID classId, TimetableSlotForm form) {
        SchoolClassEntity schoolClass = this.loadClass(classId);
        TimetableSlotEntity slot = TimetableSlotEntity.builder().schoolClass(schoolClass)
                .academicYear(this.academicYearContext.consulted().orElse(null)).build();
        this.apply(slot, form);
        return toDto(this.timetableSlotRepository.saveAndFlush(slot));
    }

    @Transactional
    public TimetableSlotDto update(UUID classId, UUID slotId, TimetableSlotForm form) {
        TimetableSlotEntity slot = this.loadSlot(classId, slotId);
        this.apply(slot, form);
        return toDto(this.timetableSlotRepository.saveAndFlush(slot));
    }

    @Transactional
    public void delete(UUID classId, UUID slotId) {
        this.timetableSlotRepository.delete(this.loadSlot(classId, slotId));
    }

    private void apply(TimetableSlotEntity slot, TimetableSlotForm form) {
        if (!form.getStartTime().isBefore(form.getEndTime())) {
            throw new BadRequestException("L'heure de fin doit suivre l'heure de début.");
        }
        StaffEntity teacher = this.resolveTeacher(form.getTeacherId(), slot.getSchoolClass());
        this.assertClassIsFree(slot, form);
        this.assertTeacherIsFree(slot, teacher, form);

        slot.setDayOfWeek(form.getDayOfWeek());
        slot.setStartTime(form.getStartTime());
        slot.setEndTime(form.getEndTime());
        slot.setSubject(form.getSubject().trim());
        slot.setTeacher(teacher);
        slot.setRoom(StringUtils.isBlank(form.getRoom()) ? null : form.getRoom().trim());
    }

    private void assertClassIsFree(TimetableSlotEntity slot, TimetableSlotForm form) {
        this.timetableSlotRepository
                .findBySchoolClass_IdAndDayOfWeek(slot.getSchoolClass().getId(), form.getDayOfWeek())
                .stream()
                .filter(other -> !other.getId().equals(slot.getId()))
                .filter(other -> sameYear(other, slot))
                .filter(other -> overlaps(other, form.getStartTime(), form.getEndTime()))
                .findFirst()
                .ifPresent(other -> {
                    throw new BadRequestException(String.format(
                            "La classe a déjà %s le %s de %s à %s.", other.getSubject(),
                            dayLabel(other.getDayOfWeek()), other.getStartTime(), other.getEndTime()));
                });
    }

    /**
     * L'enseignant n'est pas déjà ailleurs sur ce créneau, dans aucune classe de l'école.
     *
     * <p>Le message nomme la classe en cause : sans elle, la direction devrait parcourir toutes
     * les grilles pour trouver d'où vient le conflit.
     */
    private void assertTeacherIsFree(TimetableSlotEntity slot, StaffEntity teacher, TimetableSlotForm form) {
        if (Objects.isNull(teacher)) {
            return;
        }
        this.timetableSlotRepository.findByTeacher_IdAndDayOfWeek(teacher.getId(), form.getDayOfWeek())
                .stream()
                .filter(other -> !other.getId().equals(slot.getId()))
                .filter(other -> sameYear(other, slot))
                .filter(other -> overlaps(other, form.getStartTime(), form.getEndTime()))
                .findFirst()
                .ifPresent(other -> {
                    throw new BadRequestException(String.format(
                            "%s %s enseigne déjà en %s le %s de %s à %s.",
                            teacher.getLastName(), teacher.getFirstName(), other.getSchoolClass().getName(),
                            dayLabel(other.getDayOfWeek()), other.getStartTime(), other.getEndTime()));
                });
    }

    /** Enseignant du créneau, s'il appartient à l'école, est actif et enseigne. */
    private StaffEntity resolveTeacher(UUID staffId, SchoolClassEntity schoolClass) {
        if (Objects.isNull(staffId)) {
            return null;
        }
        StaffEntity member = this.staffRepository.findById(staffId)
                .filter(found -> found.getEstablishment().getId()
                        .equals(schoolClass.getEstablishment().getId()))
                .orElseThrow(() -> new BadRequestException("Ce membre du personnel n'existe pas."));
        if (!member.isActive()) {
            throw new BadRequestException(String.format(
                    "%s ne fait plus partie du personnel actif.", member.getLastName()));
        }
        if (member.getRole() != StaffRole.TEACHER) {
            throw new BadRequestException(String.format(
                    "%s n'a pas le profil enseignant.", member.getLastName()));
        }
        return member;
    }

    private SchoolClassEntity loadClass(UUID classId) {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return this.schoolClassRepository.findById(classId)
                .filter(found -> found.getEstablishment().getId().equals(scope))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Class with provided id %s not found", classId)));
    }

    private TimetableSlotEntity loadSlot(UUID classId, UUID slotId) {
        this.loadClass(classId);
        return this.timetableSlotRepository.findById(slotId)
                .filter(found -> found.getSchoolClass().getId().equals(classId))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Timetable slot with provided id %s not found", slotId)));
    }

    /** Une grille ne se heurte qu'à celle de la même année : l'an passé, l'enseignant était ailleurs. */
    private static boolean sameYear(TimetableSlotEntity other, TimetableSlotEntity slot) {
        UUID a = Objects.isNull(other.getAcademicYear()) ? null : other.getAcademicYear().getId();
        UUID b = Objects.isNull(slot.getAcademicYear()) ? null : slot.getAcademicYear().getId();
        return Objects.equals(a, b);
    }

    /** Bornes ouvertes : un cours qui finit à 10 h et le suivant qui commence à 10 h s'enchaînent. */
    private static boolean overlaps(TimetableSlotEntity other, LocalTime start, LocalTime end) {
        return other.getStartTime().isBefore(end) && start.isBefore(other.getEndTime());
    }

    private static String dayLabel(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, FRENCH);
    }

    private static TimetableSlotDto toDto(TimetableSlotEntity entity) {
        StaffEntity teacher = entity.getTeacher();
        return TimetableSlotDto.builder()
                .id(entity.getId().toString())
                .dayOfWeek(entity.getDayOfWeek())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .subject(entity.getSubject())
                .teacherId(Optional.ofNullable(teacher).map(t -> t.getId().toString()).orElse(null))
                .teacherName(Optional.ofNullable(teacher)
                        .map(t -> String.format("%s %s", t.getLastName(), t.getFirstName())).orElse(null))
                .room(entity.getRoom())
                .build();
    }
}
