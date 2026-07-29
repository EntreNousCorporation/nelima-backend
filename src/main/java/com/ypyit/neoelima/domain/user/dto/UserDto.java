package com.ypyit.neoelima.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ypyit.neoelima.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto extends BaseDto {

    private String username;
    private String firstName;
    private String lastName;
    private Set<ContactDto> contacts;
}
