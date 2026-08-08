package com.ypyit.neoelima.domain.subscription.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Formule d'abonnement, telle que la console la lit et la règle. */
@Getter
@Setter
@Builder
public class SubscriptionPlanDto {

    private UUID id;
    /** Clé métier, écrite sur l'établissement et sur les factures. Elle ne change jamais. */
    private String plan;
    private String label;
    private String description;
    /** Nul pour le palier de tête : il n'a pas de plafond, son tarif se négocie. */
    private Integer maxStudents;
    private BigDecimal price;
    private boolean active;
    private int position;

    /**
     * Montrée sur nelima.ci. Une formule négociée reste interne.
     *
     * <p>Nom du champ figé explicitement : Jackson aurait publié {@code "public"}, en retirant le
     * préfixe du lecteur {@code isPublic()}. Les deux façades lisent {@code isPublic} ; sans cette
     * annotation elles recevaient {@code undefined}, l'interrupteur s'affichait éteint sur une
     * formule pourtant publiée, et l'enregistrer la retirait du site.
     */
    @JsonProperty("isPublic")
    private boolean isPublic;
    /** Mise en avant sur le site : une seule formule à la fois. */
    private boolean featured;

    /** Écoles actuellement sous cette formule. Ce qui la rend indispensable, ou supprimable. */
    private long establishmentCount;
    /** Factures déjà émises sous cette formule : elles interdisent la suppression. */
    private long invoiceCount;
}
