package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.AttendanceSummaryDto;
import com.ypyit.neoelima.domain.establishment.dto.StaffAttendanceDto;
import com.ypyit.neoelima.domain.establishment.entity.StaffAttendanceEntity;
import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import com.ypyit.neoelima.domain.establishment.enums.AttendanceStatus;
import com.ypyit.neoelima.domain.establishment.form.StaffAttendanceForm;
import com.ypyit.neoelima.domain.establishment.repository.StaffAttendanceRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pointage du personnel.
 *
 * <p>Une ligne par personne et par jour. C'est l'unicité en base qui le garantit, et le service qui
 * la respecte : on met à jour la ligne du jour au lieu d'en créer une seconde, sans quoi un double
 * pointage produirait deux vérités contradictoires dans le même tableau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAttendanceService {

    private final StaffRepository staffRepository;
    private final StaffAttendanceRepository staffAttendanceRepository;
    private final StaffService staffService;

    /**
     * Feuille du jour : tout le personnel actif, pointé ou non.
     *
     * <p>Les personnes sans pointage figurent avec un statut nul. Ce sont elles qu'on cherche le
     * matin ; une feuille limitée aux lignes déjà saisies serait vide au moment où elle sert.
     */
    public List<StaffAttendanceDto> sheet(LocalDate day) {
        UUID scope = this.staffService.scope();
        LocalDate target = Objects.requireNonNullElseGet(day, LocalDate::now);

        Map<UUID, StaffAttendanceEntity> pointed = this.staffService.attendanceByStaff(scope, target);

        return this.staffRepository
                .findByEstablishment_IdAndActiveTrueOrderByLastNameAscFirstNameAsc(scope).stream()
                .map(member -> {
                    StaffAttendanceEntity attendance = pointed.get(member.getId());
                    return StaffAttendanceDto.builder()
                            .staffId(member.getId().toString())
                            .firstName(member.getFirstName())
                            .lastName(member.getLastName())
                            .role(member.getRole())
                            .jobTitle(member.getJobTitle())
                            .day(target)
                            .status(Objects.isNull(attendance) ? null : attendance.getStatus())
                            .note(Objects.isNull(attendance) ? null : attendance.getNote())
                            .build();
                })
                .toList();
    }

    @Transactional
    public StaffAttendanceDto record(UUID staffId, StaffAttendanceForm form) {
        StaffEntity member = this.staffService.load(staffId);
        if (form.getDay().isAfter(LocalDate.now())) {
            // Pointer une journée à venir affirmerait une présence que personne n'a constatée.
            throw new BadRequestException("Une journée future ne peut pas être pointée.");
        }

        StaffAttendanceEntity attendance = this.staffAttendanceRepository
                .findByStaff_IdAndDay(staffId, form.getDay())
                .orElseGet(() -> StaffAttendanceEntity.builder()
                        .staff(member).day(form.getDay()).build());

        attendance.setStatus(form.getStatus());
        attendance.setNote(StringUtils.isBlank(form.getNote()) ? null : form.getNote().trim());
        StaffAttendanceEntity saved = this.staffAttendanceRepository.saveAndFlush(attendance);

        log.info("STAFF_ATTENDANCE_RECORDED: {} on {}", staffId, form.getDay());
        return StaffAttendanceDto.builder()
                .staffId(member.getId().toString())
                .firstName(member.getFirstName())
                .lastName(member.getLastName())
                .role(member.getRole())
                .jobTitle(member.getJobTitle())
                .day(saved.getDay())
                .status(saved.getStatus())
                .note(saved.getNote())
                .build();
    }

    /**
     * Ce que disent les pointages d'un mois.
     *
     * <p>Le taux rapporte les jours travaillés aux jours <em>pointés</em>, et non aux jours ouvrés :
     * une école qui ne pointe qu'un jour sur deux afficherait sinon un taux de 50 % alors que
     * personne n'a manqué.
     */
    public AttendanceSummaryDto summary(YearMonth month) {
        UUID scope = this.staffService.scope();
        YearMonth target = Objects.requireNonNullElseGet(month, YearMonth::now);
        LocalDate from = target.atDay(1);
        LocalDate to = target.atEndOfMonth();

        List<StaffAttendanceEntity> entries = this.staffAttendanceRepository
                .findByStaff_Establishment_IdAndDayBetween(scope, from, to);

        long present = count(entries, AttendanceStatus.PRESENT);
        long late = count(entries, AttendanceStatus.LATE);
        long absent = count(entries, AttendanceStatus.ABSENT);
        long leave = count(entries, AttendanceStatus.LEAVE);

        // Le congé ne compte ni au numérateur ni au dénominateur : il n'est ni un manquement ni
        // une journée travaillée, et le compter en absence ferait plonger le taux des mois de
        // vacances scolaires.
        long worked = present + late;
        long due = worked + absent;

        return AttendanceSummaryDto.builder()
                .from(from).to(to)
                .present(present).late(late).absent(absent).leave(leave)
                .presenceRate(due == 0 ? null : BigDecimal.valueOf(worked)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(due), 1, RoundingMode.HALF_UP))
                .build();
    }

    private static long count(List<StaffAttendanceEntity> entries, AttendanceStatus status) {
        return entries.stream().filter(entry -> status.equals(entry.getStatus())).count();
    }
}
