package com.ypyit.neoelima.domain.storage.service;

/**
 * Fichiers confidentiels : jamais d'URL publique, lus et écrits par le seul backend.
 *
 * <p>Séparé de {@link StorageService}, dont chaque bucket reçoit une politique de lecture
 * publique à sa création — l'y ajouter aurait rendu trop facile de ranger une pièce d'identité
 * dans un bucket ouvert.
 */
public interface PrivateFileStore {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void deleteQuietly(String key);
}
