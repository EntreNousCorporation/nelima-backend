package com.ypyit.neoelima.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDto {

    private UUID id;
    private ContactType type;
    private String value;

    /**
     * Contact principal : l'identifiant de connexion, et le destinataire des codes.
     *
     * <p>Le nom est imposé. Jackson tire « primary » du lecteur {@code isPrimary()} que Lombok
     * produit, si bien que la route <em>rendait</em> {@code primary} là où elle <em>accepte</em>
     * {@code isPrimary} — les formulaires portent un {@code Boolean}, dont le lecteur garde son
     * préfixe. Envoyer et recevoir sous deux noms différents oblige chaque appelant à connaître
     * l'asymétrie, et le premier qui l'ignore lit un contact principal qui n'existe pas.
     */
    @JsonProperty("isPrimary")
    private boolean isPrimary;

    private boolean whatsApp;
}
