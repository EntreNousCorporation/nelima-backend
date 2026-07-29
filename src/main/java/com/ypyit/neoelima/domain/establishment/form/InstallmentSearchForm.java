package com.ypyit.neoelima.domain.establishment.form;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallmentSearchForm {

    private UUID establishmentId;

    private UUID studentId;

    /** Restreint aux tranches dans cet état. Sert notamment à isoler ce qui reste dû. */
    private InstallmentStatus status;

    /**
     * Ne retient que les tranches dont l'échéance précède cette date. Combiné au statut
     * {@code PENDING}, c'est la définition d'un impayé.
     */
    private LocalDate dueBefore;
}
