package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FeeScheduleDto extends BaseDto {

    private String label;
    private BigDecimal amount;
    private LocalDate dueDate;
    private Integer position;
}
