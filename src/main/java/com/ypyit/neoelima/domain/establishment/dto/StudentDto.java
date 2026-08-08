package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.common.dto.BaseDto;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDto extends BaseDto {

    private String firstName;
    private String lastName;
    private String registrationNumber;
    private LocalDate birthDay;
    private LevelOfStudyDto levelOfStudy;
    private String placeOfBirth;
    private Set<UserDto> parentUsers;
    private EstablishmentLiteDto establishment;
    private SchoolClassLiteDto schoolClass;

    /**
     * Ce que la famille doit encore, toutes tranches non réglées confondues.
     *
     * <p>Servi sur la liste parce que c'est la question que l'école se pose en la parcourant.
     * Nul plutôt que zéro quand la liste n'est pas enrichie : un zéro affirmerait que la famille
     * est à jour, ce qu'on ne sait pas.
     */
    private BigDecimal outstandingAmount;

    /**
     * Part de ce solde dont l'échéance est déjà passée.
     *
     * <p>Distingue l'acompte du retard : devoir 100 000 F payables en mai n'est pas devoir
     * 100 000 F depuis mars, et les deux appellent des gestes différents.
     */
    private BigDecimal overdueAmount;
}
