package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.ActivityKind;
import com.ypyit.neoelima.domain.establishment.enums.ActivityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDto {

    private String id;
    private String name;
    private ActivityKind kind;
    private String place;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private String periodLabel;
    private Integer capacity;
    private ActivityStatus status;

    private String coachId;
    private String coachName;

    private boolean openToAll;
    @Builder.Default
    private List<SchoolClassLiteDto> eligibleClasses = new ArrayList<>();

    /** Tarif, nul pour une activité gratuite. */
    private BigDecimal price;
    private String feeId;

    private long enrolledCount;
    private long waitlistedCount;

    /** Places restantes ; nul jamais négatif, la capacité étant contraignante. */
    private long remainingSeats;

    /** Produit du tarif par le nombre d'inscrits. Nul pour une activité gratuite. */
    private BigDecimal expectedRevenue;
}
