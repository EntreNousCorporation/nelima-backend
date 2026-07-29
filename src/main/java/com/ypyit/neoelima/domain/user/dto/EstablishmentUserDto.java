package com.ypyit.neoelima.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ypyit.neoelima.common.dto.BaseDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentLiteDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstablishmentUserDto extends BaseDto {

    private EstablishmentLiteDto establishment;
    private String firstName;
    private String lastName;
}
