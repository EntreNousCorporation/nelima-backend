package com.ypyit.neoelima.domain.establishment.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import com.ypyit.neoelima.domain.user.enums.RoleType;

@Getter
@Setter
public class StaffAccessForm {

    /**
     * Adresse de connexion, et destination du courriel de bienvenue.
     *
     * <p>Distincte de l'adresse portée par la fiche : celle-ci sert à joindre la personne, celle-là
     * à l'identifier. Les confondre d'office créerait un compte sur une adresse familiale.
     */
    @NotBlank
    @Email
    private String username;

    /** Rôle du compte, parmi les seuls rôles d'établissement. */
    @NotNull
    private RoleType role;
}
