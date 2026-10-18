package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.SchoolYearStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicYearDto {

    private String id;
    private String label;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean active;
    /** L'année que l'utilisateur consulte. */
    private boolean consulted;
    /** État de l'année dans l'école de l'utilisateur ; nul pour la plateforme. */
    private SchoolYearStatus status;

    @Builder.Default
    private List<AcademicPeriodDto> periods = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcademicPeriodDto {
        private String id;
        private String label;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer position;
    }
}
