package com.ypyit.neoelima.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ypyit.neoelima.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import java.util.UUID;

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

    /**
     * Nature du compte : {@code ADMIN_USER}, {@code ESTABLISHMENT_USER} ou
     * {@code STUDENT_PARENT_USER}.
     *
     * <p>Indispensable aux frontaux : chaque surface n'accepte qu'un type de compte. Sans cette
     * information, le portail établissement laisserait entrer un administrateur YPYit ou un parent.
     */
    private String userType;

    /** Renseigné pour les seuls utilisateurs d'établissement. */
    private UUID establishmentId;

    private String establishmentName;
}
