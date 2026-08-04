package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.CalendarEntryDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolEventEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.SchoolEventForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolEventRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Événements du calendrier scolaire saisis par l'école.
 *
 * <p>Les échéances financières ne passent pas par ici : elles sont déduites des tranches dues, dans
 * {@link CalendarService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolEventService {

    private final SchoolEventRepository schoolEventRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final PushNotificationService pushNotificationService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public CalendarEntryDto create(SchoolEventForm form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.findById(scope)
                .orElseThrow(() -> new NotFoundException("Establishment not found"));

        SchoolEventEntity entity = SchoolEventEntity.builder()
                .title(form.getTitle().trim())
                .kind(form.getKind())
                .date(form.getDate())
                .establishment(establishment)
                .build();
        this.apply(entity, form);

        SchoolEventEntity saved = this.schoolEventRepository.saveAndFlush(entity);
        // Rien n'est envoyé ici, même si l'événement est visible des familles : rendre visible
        // n'est pas interrompre. L'envoi est un geste distinct.
        log.info("SCHOOL_EVENT_CREATED: {} in establishment {}", saved.getId(), scope);
        return CalendarService.toEntry(saved);
    }

    @Transactional
    public CalendarEntryDto update(UUID id, SchoolEventForm form) {
        SchoolEventEntity entity = this.load(id);
        entity.setTitle(form.getTitle().trim());
        entity.setKind(form.getKind());
        entity.setDate(form.getDate());
        this.apply(entity, form);
        return CalendarService.toEntry(this.schoolEventRepository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(UUID id) {
        SchoolEventEntity entity = this.load(id);
        this.schoolEventRepository.delete(entity);
        log.info("SCHOOL_EVENT_DELETED: {}", id);
    }

    /**
     * Prévient les familles concernées.
     *
     * <p>Geste explicite et répétable : c'est la direction qui décide qu'un événement mérite
     * d'interrompre un parent, et {@code lastNotifiedAt} lui dit si elle l'a déjà fait.
     *
     * @return le nombre de tuteurs joints
     */
    @Transactional
    public int notifyFamilies(UUID id) {
        SchoolEventEntity entity = this.load(id);
        if (!entity.isVisibleToFamilies()) {
            // Prévenir d'un événement que l'application ne montre pas enverrait le parent sur un
            // écran vide.
            throw new BadRequestException(
                    "Cet événement n'est pas visible des familles : rendez-le visible avant de les prévenir.");
        }

        Set<UUID> guardians = this.guardiansOf(entity);
        if (guardians.isEmpty()) {
            throw new BadRequestException("Aucune famille rattachée aux classes concernées.");
        }

        this.pushNotificationService.send(guardians, entity.getTitle(),
                this.messageOf(entity), Map.of("eventId", entity.getId().toString()));
        entity.setLastNotifiedAt(Instant.now());
        this.schoolEventRepository.saveAndFlush(entity);
        log.info("SCHOOL_EVENT_NOTIFIED: {} to {} guardian(s)", id, guardians.size());
        return guardians.size();
    }

    /** Tuteurs des élèves concernés, sans doublon : un parent de deux enfants n'est prévenu qu'une fois. */
    private Set<UUID> guardiansOf(SchoolEventEntity entity) {
        List<StudentEntity> students = entity.isWholeSchool()
                ? this.studentRepository.findByEstablishment_Id(entity.getEstablishment().getId())
                : entity.getClasses().stream()
                        .flatMap(schoolClass -> this.studentRepository
                                .findBySchoolClass_Id(schoolClass.getId()).stream())
                        .toList();

        return students.stream()
                .flatMap(student -> student.getParentUsers().stream())
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String messageOf(SchoolEventEntity entity) {
        String when = entity.isAllDay() || Objects.isNull(entity.getStartTime())
                ? "toute la journée"
                : String.format("à %s", entity.getStartTime());
        return String.format("Le %s, %s.", entity.getDate(), when);
    }

    private void apply(SchoolEventEntity entity, SchoolEventForm form) {
        entity.setAllDay(form.isAllDay());
        // Un événement sur la journée entière n'a pas d'horaire : les garder afficherait « toute
        // la journée, de 8 h à 10 h ».
        entity.setStartTime(form.isAllDay() ? null : form.getStartTime());
        entity.setEndTime(form.isAllDay() ? null : form.getEndTime());
        entity.setDetails(StringUtils.isBlank(form.getDetails()) ? null : form.getDetails().trim());
        entity.setVisibleToFamilies(form.isVisibleToFamilies());
        entity.setWholeSchool(form.isWholeSchool());

        entity.getClasses().clear();
        if (!form.isWholeSchool()) {
            for (UUID classId : form.getClassIds()) {
                SchoolClassEntity schoolClass = this.schoolClassRepository.findById(classId)
                        .orElseThrow(() -> new NotFoundException(
                                String.format("Class with provided id %s not found", classId)));
                // L'identifiant vient du client : sans ce contrôle, une école viserait la classe
                // d'une autre et en préviendrait les familles.
                if (!schoolClass.getEstablishment().getId().equals(entity.getEstablishment().getId())) {
                    throw new NotFoundException(String.format("Class with provided id %s not found", classId));
                }
                entity.getClasses().add(schoolClass);
            }
        }
    }

    SchoolEventEntity load(UUID id) {
        SchoolEventEntity entity = this.schoolEventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Event with provided id %s not found", id)));
        UUID scope = this.scope();
        if (Objects.nonNull(scope) && !entity.getEstablishment().getId().equals(scope)) {
            throw new NotFoundException(String.format("Event with provided id %s not found", id));
        }
        return entity;
    }

    UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
