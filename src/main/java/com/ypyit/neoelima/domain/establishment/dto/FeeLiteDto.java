package com.ypyit.neoelima.domain.establishment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ypyit.neoelima.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeeLiteDto extends BaseDto {

    private UUID id;
    private String name;
    private BigDecimal price;

    /**
     * Frais de scolarité, par opposition à une activité extrascolaire.
     *
     * <p>Sert à l'application parent, qui sépare les deux dans « Mon espace ». Un frais d'activité
     * est créé {@code academical = false} à l'inscription : sans ce champ, le téléphone ne peut pas
     * distinguer une cantine d'une tranche de scolarité, et les affiche pêle-mêle.
     */
    private boolean academical;
}
