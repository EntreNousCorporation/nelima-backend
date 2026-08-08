package com.ypyit.neoelima.domain.subscription.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Création ou modification d'une formule.
 *
 * <p>Le code n'y figure pas : il est dérivé du libellé à la création, puis figé. Il est recopié sur
 * chaque établissement et chaque facture, et le changer ferait désigner à ces lignes une formule
 * qui n'existe plus.
 */
@Getter
@Setter
public class SubscriptionPlanForm {

    @NotBlank
    @Size(max = 80)
    private String label;

    @Size(max = 160)
    private String description;

    /** Nul = sans plafond : le palier de tête, celui qui se négocie. */
    @Positive
    private Integer maxStudents;

    @NotNull
    @PositiveOrZero
    private BigDecimal price;

    private Boolean active;

    private Integer position;

    /** Montrer la formule sur le site public. */
    private Boolean isPublic;

    /** La mettre en avant. Le service retire la mise en avant des autres. */
    private Boolean featured;
}
