package com.ypyit.neoelima.domain.storage.service.impl;

import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Au démarrage, remet en lecture seule les buckets publics des établissements.
 *
 * <p>Ils ont longtemps été créés avec une politique qui laissait n'importe qui lister, déposer et
 * supprimer des fichiers sans clé. Corriger la politique ne suffit pas pour ceux qui existent
 * déjà : ce passage les remet d'aplomb à chaque déploiement, sans geste manuel en production.
 *
 * <p>Un stockage injoignable ne bloque pas le démarrage : l'application sert sans lui, et la
 * correction se fera au démarrage suivant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicBucketPolicyRepair implements ApplicationRunner {

    private final EstablishmentRepository establishmentRepository;
    private final StorageService storageService;

    @Override
    public void run(ApplicationArguments args) {
        int repaired = 0;
        for (String bucket : this.establishmentRepository.findBucketNames()) {
            try {
                this.storageService.enforceReadOnlyPolicy(bucket);
                repaired++;
            } catch (RuntimeException e) {
                log.warn("PUBLIC_BUCKET_POLICY_NOT_REPAIRED: {} : {}", bucket, e.getMessage());
            }
        }
        log.info("PUBLIC_BUCKET_POLICY_REPAIRED: {} bucket(s) d'établissement vérifié(s)", repaired);
    }
}
