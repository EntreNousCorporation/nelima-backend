package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.domain.establishment.enums.ActivityKind;
import com.ypyit.neoelima.domain.establishment.enums.ActivityStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
public class ActivityForm {

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    private ActivityKind kind;

    @Size(max = 128)
    private String place;

    private DayOfWeek dayOfWeek;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;

    @Size(max = 64)
    private String periodLabel;

    /**
     * Nombre de places.
     *
     * <p>La borne haute écarte la faute de frappe : une activité de plus de cinq cents places n'est
     * pas une activité, c'est un zéro de trop.
     */
    @NotNull
    @Min(1)
    @Max(500)
    private Integer capacity;

    private ActivityStatus status;

    /** Encadrant, pris dans le répertoire du personnel. Nul tant qu'il reste à désigner. */
    private UUID coachId;

    /** Tarif. Nul ou zéro pour une activité gratuite. */
    @PositiveOrZero
    private BigDecimal price;
}
