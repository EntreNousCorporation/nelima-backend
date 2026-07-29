package com.ypyit.neoelima.domain.establishment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ypyit.neoelima.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeeLiteDto extends BaseDto {

    private UUID id;
    private String name;
    private BigDecimal price;
}
