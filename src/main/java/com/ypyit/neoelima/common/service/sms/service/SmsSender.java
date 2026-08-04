package com.ypyit.neoelima.common.service.sms.service;

/**
 * Envoi d'un SMS à un numéro.
 *
 * <p>Interface et non classe concrète, pour la raison déjà retenue sur les notifications : la mise
 * en œuvre dépend d'un service tiers, que les tests doivent pouvoir remplacer et que le produit
 * doit pouvoir changer sans toucher aux appelants. {@link SmsService}, qui est une classe abstraite
 * liée à l'API Orange, ne se prête ni à l'un ni à l'autre.
 *
 * <p><strong>Un SMS se facture à l'envoi.</strong> Contrairement à une notification, un doublon
 * coûte de l'argent : les appelants doivent s'assurer qu'ils n'envoient pas deux fois.
 */
public interface SmsSender {

    /**
     * Envoie le message, ou signale l'échec par une exception.
     *
     * @param phoneNumber numéro au format international
     */
    void send(String message, String phoneNumber);
}
