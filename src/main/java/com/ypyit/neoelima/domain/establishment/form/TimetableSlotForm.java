package com.ypyit.neoelima.domain.establishment.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
public class TimetableSlotForm {

    @NotNull
    private DayOfWeek dayOfWeek;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotBlank
    @Size(max = 96)
    private String subject;

    /** Enseignant du créneau, parmi le personnel ; nul tant qu'il n'est pas désigné. */
    private UUID teacherId;

    @Size(max = 64)
    private String room;
}
