package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.NotificationKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Un élément du fil de notifications de l'école.
 *
 * <p>Rien n'est stocké : chaque élément est déduit d'un fait daté qui existe déjà en base. Il n'a
 * donc pas d'identifiant propre — la clé est composée de sa nature et de ce qu'il désigne, ce qui
 * suffit à la boucle de rendu et évite d'inventer une entité pour porter un compteur.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private String id;
    private NotificationKind kind;
    private String title;
    private String detail;

    /** Date du fait, jamais celle de la lecture : c'est elle qui décide du « non lu ». */
    private Instant occurredAt;

    /**
     * Écran du portail qui permet de traiter l'élément.
     *
     * <p>Nul pour les éléments servis à l'application parent : un chemin de portail web ne lui sert
     * à rien. Elle déduit sa destination de {@code kind}, comme elle le fait déjà du type porté par
     * la notification push.
     */
    private String link;

    /**
     * Enregistrement joint, à écouter plutôt qu'à lire.
     *
     * <p>Nul tant qu'aucune surface ne permet d'en déposer un — l'application n'affiche alors pas
     * le bouton. La place est réservée ici parce que le contrat se négocie une fois : le jour où
     * l'école saura enregistrer un message, rien ne bougera côté application.
     *
     * <p>C'est ce qui rend le produit lisible à un parent qui déchiffre mal le français écrit, et
     * cette part-là du marché n'est pas marginale.
     */
    private String voiceNoteUrl;

    private boolean unread;
}
