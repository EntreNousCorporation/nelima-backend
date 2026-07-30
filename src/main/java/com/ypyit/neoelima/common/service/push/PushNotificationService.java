package com.ypyit.neoelima.common.service.push;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Envoi de notifications push aux comptes Nelima.
 *
 * <p>Interface et non classe concrète : la mise en œuvre dépend d'un service tiers, que les tests
 * doivent pouvoir remplacer et que le produit doit pouvoir changer sans toucher aux appelants.
 *
 * <p><strong>Une notification n'est jamais critique.</strong> Toute mise en œuvre doit être
 * asynchrone et avaler ses erreurs : un encaissement reçu, un reçu émis et numéroté ne peuvent pas
 * dépendre de la disponibilité d'un tiers. Le courriel reste le canal de la pièce comptable ; le
 * push ne fait que l'annoncer.
 */
public interface PushNotificationService {

    /**
     * Notifie les comptes désignés.
     *
     * @param recipients identifiants Nelima des comptes à joindre ; ceux qui n'ont aucun appareil
     *                   abonné sont simplement ignorés
     * @param title      titre affiché, lisible sur un écran verrouillé
     * @param message    corps du message. Ne doit contenir aucune donnée qu'un tiers ne devrait
     *                   pas lire par-dessus l'épaule du destinataire
     * @param data       charge utile transmise à l'application pour l'ouvrir au bon endroit
     */
    void send(Collection<UUID> recipients, String title, String message, Map<String, String> data);
}
