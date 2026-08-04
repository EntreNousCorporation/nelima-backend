package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.AttendanceStatus;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Une ligne de la feuille du jour.
 *
 * <p>Le membre y figure même sans pointage, {@code status} nul : une feuille d'appel qui n'affiche
 * que les personnes déjà traitées ne sert à rien le matin, c'est justement les autres qu'on cherche.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffAttendanceDto {

    private String staffId;
    private String firstName;
    private String lastName;
    private StaffRole role;
    private String jobTitle;
    private LocalDate day;
    private AttendanceStatus status;
    private String note;
}
