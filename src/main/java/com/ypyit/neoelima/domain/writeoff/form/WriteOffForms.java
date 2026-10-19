package com.ypyit.neoelima.domain.writeoff.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public final class WriteOffForms {

    private WriteOffForms() {
    }

    @Getter
    @Setter
    public static class Proposal {
        @NotNull
        private UUID installmentId;
        /** Pourquoi la dette est jugée irrécouvrable : la direction tranche sur cette base. */
        @NotBlank
        @Size(max = 500)
        private String reason;
    }

    @Getter
    @Setter
    public static class Decision {
        @Size(max = 500)
        private String note;
    }
}
