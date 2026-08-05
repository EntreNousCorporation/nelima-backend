package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReminderCampaignForm {

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    private ReminderTarget target;

    /** Au moins un canal : une campagne sans canal n'envoie rien et ferait croire le contraire. */
    @NotEmpty
    private List<ReminderChannel> channels;

    /**
     * Message envoyé, avec ses variables : {parent}, {eleve}, {classe}, {montant}, {retard}.
     *
     * <p>Borné à trois cents caractères : au-delà, un SMS est découpé en plusieurs, et chaque
     * morceau est facturé.
     */
    @NotBlank
    @Size(max = 300)
    private String messageTemplate;
}
