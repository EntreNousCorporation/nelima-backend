package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.user.dto.ContactDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * De quoi joindre une école, servi à une famille.
 *
 * <p>Ces coordonnées existaient déjà en base mais restaient inaccessibles au parent : la liste des
 * établissements ne les porte pas, et le détail complet est fermé à qui n'administre pas l'école.
 * Un parent se retrouvait donc à chercher le numéro de l'école de son enfant ailleurs que dans
 * l'application censée l'y relier.
 *
 * <p>Volontairement plus étroit que {@link EstablishmentDto} : ce qui sert à appeler, écrire ou
 * trouver l'école, et rien d'autre. Ni effectifs, ni abonnement, ni comptes du personnel.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstablishmentContactCardDto {

    private UUID id;
    private String name;
    private String webSite;

    /** Ville et adresse, telles que l'école les a saisies. Nulles si elle ne l'a pas fait. */
    private String city;
    private String address;

    /**
     * Téléphones et courriels de l'école.
     *
     * <p>Chaque contact porte son drapeau {@code whatsApp} : c'est lui qui décide si l'application
     * propose d'ouvrir une conversation plutôt qu'un appel. Rien ne permet de le deviner d'un
     * numéro.
     */
    private List<ContactDto> contacts;
}
