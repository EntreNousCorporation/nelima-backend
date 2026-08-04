package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.AttendanceStatus;
import com.ypyit.neoelima.domain.establishment.enums.ContractType;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Membre du personnel tel que le portail l'affiche.
 *
 * <p>{@code monthlySalary} et {@code weeklyHours} ne sont renseignés que pour un appelant qui porte
 * {@code staff:read_salary}. Le champ est absent de la réponse, et non masqué à l'écran : un champ
 * masqué côté client reste lisible par quiconque sait interroger l'API.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffDto {

    private String id;
    private String firstName;
    private String lastName;
    private StaffRole role;
    private String jobTitle;
    private String phone;
    private String email;
    private ContractType contractType;
    private BigDecimal monthlySalary;
    private Integer weeklyHours;
    private LocalDate hiredAt;
    private boolean active;

    /** Classes où le membre intervient. */
    @Builder.Default
    private List<SchoolClassLiteDto> classes = new ArrayList<>();

    /** Renseigné quand un accès au portail lui a été ouvert. */
    private String userId;
    private String username;
    private String roleCode;
    private boolean accessEnabled;

    /** Pointage du jour demandé ; nul quand la personne n'a pas encore été pointée. */
    private AttendanceStatus todayStatus;
}
