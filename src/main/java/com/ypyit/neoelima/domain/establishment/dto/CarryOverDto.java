package com.ypyit.neoelima.domain.establishment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Bilan d'un report d'impayés sur l'année suivante. */
@Getter
@Builder
public class CarryOverDto {
    private int installments;
    private int students;
    private BigDecimal amount;
    private String targetLabel;
}
