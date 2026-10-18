package com.ypyit.neoelima.config.properties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@NoArgsConstructor
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String url;
    /**
     * Adresse de MinIO vue depuis le réseau des conteneurs, par exemple {@code http://storage:9000}.
     *
     * <p>Le stockage privé ne sert jamais d'URL au navigateur : il n'a aucune raison de sortir sur
     * Internet pour revenir sur la même machine, ni de dépendre du DNS public de {@link #url}.
     * Vide, il reprend {@link #url}.
     */
    private String internalUrl;
    private String apiKey;
    private String apiSecret;
    private String bucketName;
    /**
     * Bucket des pièces confidentielles (dossiers d'élèves), créé sans politique d'accès.
     *
     * <p>Distinct de {@link #bucketName}, dont la politique rend les objets lisibles par tous : un
     * extrait de naissance d'enfant n'a rien à faire à une URL publique. Ces fichiers ne sortent
     * que par le backend, après contrôle des droits.
     */
    private String privateBucketName = "student-records";
}
