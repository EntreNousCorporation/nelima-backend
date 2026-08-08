package com.ypyit.neoelima.domain.establishment.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AcademicYearForm {

    @NotBlank
    @Size(max = 32)
    private String label;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /**
     * Année en cours.
     *
     * <p>Activer celle-ci désactive la précédente : le service s'en charge, l'écran n'a pas à le
     * faire en deux appels.
     */
    private boolean active;

    /**
     * Périodes de l'année, dans l'ordre de saisie.
     *
     * <p>Envoyées avec l'année plutôt que gérées à part : une école qui déclare ses trimestres les
     * saisit d'un seul geste, et le remplacement intégral évite d'avoir à distinguer ajout,
     * modification et suppression pour trois lignes.
     */
    @Valid
    private List<AcademicPeriodForm> periods = new ArrayList<>();

    @Getter
    @Setter
    public static class AcademicPeriodForm {

        @NotBlank
        @Size(max = 64)
        private String label;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;
    }
}
