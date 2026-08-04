package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.domain.establishment.enums.ContractType;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class StaffForm {

    @NotBlank
    @Size(max = 128)
    private String firstName;

    @NotBlank
    @Size(max = 128)
    private String lastName;

    /** Famille de fonction. L'intitulé exact du poste, lui, reste libre. */
    @NotNull
    private StaffRole role;

    @Size(max = 128)
    private String jobTitle;

    @Size(max = 64)
    private String phone;

    @Email
    @Size(max = 128)
    private String email;

    private ContractType contractType;

    @PositiveOrZero
    private BigDecimal monthlySalary;

    /**
     * Une semaine compte cent-soixante-huit heures : au-delà, c'est une saisie erronée, et la
     * borne évite qu'elle contamine la charge d'enseignement de tout l'établissement.
     */
    @Min(0)
    @Max(60)
    private Integer weeklyHours;

    private LocalDate hiredAt;
}
