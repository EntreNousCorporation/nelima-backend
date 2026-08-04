package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.SchoolClassLiteDto;
import com.ypyit.neoelima.domain.establishment.dto.StaffDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StaffAttendanceEntity;
import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import com.ypyit.neoelima.domain.establishment.form.StaffForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffAttendanceRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Répertoire du personnel de l'établissement.
 *
 * <p>Distinct de la liste des comptes : la plupart des enseignants n'ont pas accès au portail. Le
 * lien vers un compte est facultatif et se pose après coup.
 *
 * <p>La portée vient de l'utilisateur authentifié, comme pour les classes : une école ne voit que
 * son propre personnel, et l'identifiant d'établissement ne circule jamais depuis le formulaire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffService {

    /** Permission qui commande la présence du salaire et de la charge horaire dans la réponse. */
    public static final String READ_SALARY = "staff:read_salary";

    private final StaffRepository staffRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CurrentUserProvider currentUserProvider;

    public List<StaffDto> findAll(boolean includeInactive) {
        UUID scope = this.scope();
        List<StaffEntity> members = includeInactive
                ? this.staffRepository.findByEstablishment_IdOrderByLastNameAscFirstNameAsc(scope)
                : this.staffRepository.findByEstablishment_IdAndActiveTrueOrderByLastNameAscFirstNameAsc(scope);

        // Le pointage du jour est lu en une fois pour tout le monde : une requête par ligne
        // coûterait soixante allers-retours sur un annuaire de soixante personnes.
        Map<UUID, StaffAttendanceEntity> today = this.attendanceByStaff(scope, LocalDate.now());

        boolean salaryVisible = this.canReadSalary();
        return members.stream().map(member -> toDto(member, today.get(member.getId()), salaryVisible)).toList();
    }

    public StaffDto findById(UUID id) {
        StaffEntity entity = this.load(id);
        StaffAttendanceEntity today = this.staffAttendanceRepository
                .findByStaff_IdAndDay(id, LocalDate.now()).orElse(null);
        return toDto(entity, today, this.canReadSalary());
    }

    @Transactional
    public StaffDto create(StaffForm form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.findById(scope)
                .orElseThrow(() -> new NotFoundException("Establishment not found"));

        StaffEntity entity = StaffEntity.builder()
                .firstName(form.getFirstName().trim())
                .lastName(form.getLastName().trim())
                .role(form.getRole())
                .jobTitle(trimToNull(form.getJobTitle()))
                .phone(trimToNull(form.getPhone()))
                .email(trimToNull(form.getEmail()))
                .contractType(form.getContractType())
                .monthlySalary(form.getMonthlySalary())
                .weeklyHours(form.getWeeklyHours())
                .hiredAt(form.getHiredAt())
                .active(true)
                .establishment(establishment)
                .build();

        StaffEntity saved = this.staffRepository.saveAndFlush(entity);
        // Ni nom ni salaire dans les journaux : le répertoire porte des données personnelles d'un
        // tout autre régime que les données scolaires.
        log.info("STAFF_CREATED: {} in establishment {}", saved.getId(), scope);
        return toDto(saved, null, this.canReadSalary());
    }

    @Transactional
    public StaffDto update(UUID id, StaffForm form) {
        StaffEntity entity = this.load(id);
        entity.setFirstName(form.getFirstName().trim());
        entity.setLastName(form.getLastName().trim());
        entity.setRole(form.getRole());
        entity.setJobTitle(trimToNull(form.getJobTitle()));
        entity.setPhone(trimToNull(form.getPhone()));
        entity.setEmail(trimToNull(form.getEmail()));
        entity.setContractType(form.getContractType());
        entity.setWeeklyHours(form.getWeeklyHours());
        entity.setHiredAt(form.getHiredAt());
        // Le salaire ne se modifie que par quelqu'un qui a le droit de le lire. Sans ce garde-fou,
        // un formulaire renvoyé sans le champ — parce qu'il ne l'a jamais reçu — l'effacerait.
        if (this.canReadSalary()) {
            entity.setMonthlySalary(form.getMonthlySalary());
        }
        this.staffRepository.saveAndFlush(entity);
        return this.findById(id);
    }

    /**
     * Désactivation plutôt que suppression dès qu'il existe un historique.
     *
     * <p>Un enseignant parti figure encore dans les feuilles de présence de l'an dernier ; effacer
     * sa fiche rendrait ces lignes illisibles. La suppression reste possible tant que rien n'a été
     * enregistré à son nom, pour rattraper une fiche créée par erreur.
     */
    @Transactional
    public void deactivate(UUID id) {
        StaffEntity entity = this.load(id);
        boolean hasHistory = this.staffAttendanceRepository.existsByStaff_Id(id)
                || Objects.nonNull(entity.getUser());
        if (hasHistory) {
            entity.setActive(false);
            entity.getClasses().clear();
            this.staffRepository.saveAndFlush(entity);
            log.info("STAFF_DEACTIVATED: {}", id);
            return;
        }
        this.staffRepository.delete(entity);
        log.info("STAFF_DELETED: {}", id);
    }

    @Transactional
    public StaffDto assignClasses(UUID id, List<UUID> classIds) {
        StaffEntity entity = this.load(id);
        for (UUID classId : classIds) {
            SchoolClassEntity schoolClass = this.schoolClassRepository.findById(classId)
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Class with provided id %s not found", classId)));
            // L'identifiant de classe arrive du client : sans ce contrôle, rien n'empêcherait de
            // rattacher un membre à la classe d'une autre école.
            if (!schoolClass.getEstablishment().getId().equals(entity.getEstablishment().getId())) {
                throw new NotFoundException(String.format("Class with provided id %s not found", classId));
            }
            entity.getClasses().add(schoolClass);
        }
        this.staffRepository.saveAndFlush(entity);
        return this.findById(id);
    }

    @Transactional
    public StaffDto unassignClass(UUID id, UUID classId) {
        StaffEntity entity = this.load(id);
        entity.getClasses().removeIf(schoolClass -> schoolClass.getId().equals(classId));
        this.staffRepository.saveAndFlush(entity);
        return this.findById(id);
    }

    /**
     * Charge une fiche du périmètre de l'appelant.
     *
     * <p>Répond {@code NotFound} — et non {@code Forbidden} — pour une fiche d'une autre école :
     * distinguer les deux révélerait qu'elle existe.
     */
    StaffEntity load(UUID id) {
        StaffEntity entity = this.staffRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Staff member with provided id %s not found", id)));
        UUID scope = this.scope();
        if (Objects.nonNull(scope) && !entity.getEstablishment().getId().equals(scope)) {
            throw new NotFoundException(String.format("Staff member with provided id %s not found", id));
        }
        return entity;
    }

    UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            // Un administrateur YPYit n'a pas de personnel à lui, et rendre celui de toutes les
            // écoles n'aurait aucun sens sur cet écran.
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }

    boolean canReadSalary() {
        return this.currentUserProvider.hasPermission(READ_SALARY);
    }

    Map<UUID, StaffAttendanceEntity> attendanceByStaff(UUID scope, LocalDate day) {
        return this.staffAttendanceRepository.findByStaff_Establishment_IdAndDay(scope, day).stream()
                .collect(Collectors.toMap(attendance -> attendance.getStaff().getId(),
                        Function.identity(), (first, second) -> first));
    }

    static StaffDto toDto(StaffEntity entity, StaffAttendanceEntity today, boolean salaryVisible) {
        UserEntity user = entity.getUser();
        return StaffDto.builder()
                .id(entity.getId().toString())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .role(entity.getRole())
                .jobTitle(entity.getJobTitle())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .contractType(entity.getContractType())
                .monthlySalary(salaryVisible ? entity.getMonthlySalary() : null)
                .weeklyHours(salaryVisible ? entity.getWeeklyHours() : null)
                .hiredAt(entity.getHiredAt())
                .active(entity.isActive())
                .classes(entity.getClasses().stream()
                        .sorted(java.util.Comparator.comparing(SchoolClassEntity::getName))
                        .map(schoolClass -> SchoolClassLiteDto.builder()
                                .id(schoolClass.getId().toString())
                                .name(schoolClass.getName())
                                .room(schoolClass.getRoom())
                                .build())
                        .toList())
                .userId(Objects.isNull(user) ? null : user.getId().toString())
                .username(Objects.isNull(user) ? null : user.getUsername())
                .roleCode(Objects.isNull(user) || Objects.isNull(user.getRole())
                        ? null : user.getRole().getCode())
                .accessEnabled(Objects.nonNull(user) && user.isEnabled())
                .todayStatus(Objects.isNull(today) ? null : today.getStatus())
                .build();
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
