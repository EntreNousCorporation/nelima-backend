package com.ypyit.neoelima.domain.establishment.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ReenrollmentForm {

    @NotEmpty
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        @NotNull
        private UUID studentId;
        /** Classe de la rentrée ; nulle, l'élève est réinscrit sans classe, à répartir ensuite. */
        private UUID schoolClassId;
    }
}
