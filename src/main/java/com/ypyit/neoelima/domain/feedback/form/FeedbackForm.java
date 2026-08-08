package com.ypyit.neoelima.domain.feedback.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackForm {

    @NotBlank
    @NoXssContent
    @Size(min = 5, max = 2000)
    private String message;

    /**
     * Version de l'application, renseignée par le client.
     *
     * <p>Non vérifiée : elle sert à situer un retour, pas à autoriser quoi que ce soit.
     */
    @NoXssContent
    @Size(max = 32)
    private String appVersion;
}
