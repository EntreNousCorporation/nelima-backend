package com.ypyit.neoelima.domain.establishment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ce que disent les pointages d'un mois. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryDto {

    private LocalDate from;
    private LocalDate to;

    private long present;
    private long late;
    private long absent;
    private long leave;

    /** Jours travaillés rapportés aux jours pointés. Nul quand rien n'a été pointé du mois. */
    private BigDecimal presenceRate;
}
