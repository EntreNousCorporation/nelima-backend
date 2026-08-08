package com.ypyit.neoelima.domain.prospect.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Demande déposée depuis le site public.
 *
 * <p>Le formulaire est volontairement court : chaque champ de plus fait perdre des visiteurs, et
 * tout ce qui manque se demande de vive voix lors du rappel. Seuls l'école, le contact et l'adresse
 * sont exigés.
 */
@Getter
@Setter
public class DemoRequestForm {

    @NotBlank
    @Size(max = 160)
    private String schoolName;

    @NotBlank
    @Size(max = 120)
    private String contactName;

    @NotBlank
    @Email
    @Size(max = 160)
    private String email;

    @Size(max = 40)
    private String phone;

    @Size(max = 120)
    private String city;

    @Positive
    private Integer studentCount;

    @Size(max = 2000)
    private String message;

    @Size(max = 120)
    private String sourcePage;

    /**
     * Pot de miel.
     *
     * <p>Champ invisible dans la page : un humain ne le remplit jamais, un automate de formulaire
     * le remplit toujours. C'est le filtre le moins coûteux et le seul qui n'impose rien au
     * visiteur — pas de captcha à résoudre pour un directeur d'école sur un téléphone.
     */
    @Size(max = 200)
    private String website;
}
