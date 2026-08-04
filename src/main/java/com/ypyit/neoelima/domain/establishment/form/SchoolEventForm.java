package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.domain.establishment.enums.SchoolEventKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SchoolEventForm {

    @NotBlank
    @Size(max = 160)
    private String title;

    @NotNull
    private SchoolEventKind kind;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    private boolean allDay;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;

    @Size(max = 255)
    private String details;

    /** Concerne tout l'établissement : la liste de classes est alors ignorée. */
    private boolean wholeSchool;

    private List<UUID> classIds = new ArrayList<>();

    /**
     * Paraît dans l'application des familles.
     *
     * <p>N'envoie aucune notification : l'envoi est un geste distinct, sur la fiche.
     */
    private boolean visibleToFamilies;
}
