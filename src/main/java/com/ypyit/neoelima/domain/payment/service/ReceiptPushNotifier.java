package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.common.utils.XofFormat;
import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import com.ypyit.neoelima.domain.establishment.service.NotificationPreferenceService;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.service.ParentNotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Annonce un encaissement aux personnes concernées.
 *
 * <p>La notification <strong>annonce</strong> le reçu, elle ne le remplace pas. Un push n'est pas
 * fiable — appareil éteint, autorisation refusée, jeton expiré — et ne peut donc pas porter une pièce
 * comptable. Le courriel avec le PDF reste le canal de la quittance ; le push fait gagner l'immédiat.
 *
 * <p>Le message ne nomme jamais l'élève. Une notification s'affiche sur un écran verrouillé, visible
 * de quiconque passe : le matricule et le montant suffisent au tuteur pour reconnaître son paiement,
 * sans exposer le nom d'un enfant à un tiers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptPushNotifier {

    private final PushNotificationService pushNotificationService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final ParentNotificationPreferenceService parentNotificationPreferenceService;

    @Transactional(readOnly = true)
    public void announce(ReceiptEntity receipt) {
        UUID establishmentId = Objects.isNull(receipt.getEstablishment())
                ? null : receipt.getEstablishment().getId();
        if (!this.notificationPreferenceService.isEnabled(establishmentId,
                NotificationEvent.RECEIPT_ISSUED, NotificationChannel.PUSH)) {
            // L'école a coupé l'annonce. Le courriel portant le reçu, lui, part quoi qu'il arrive :
            // c'est la pièce comptable, et elle n'est pas débrayable.
            log.debug("RECEIPT_PUSH_DISABLED: reçu {} non annoncé, réglage de l'établissement",
                    receipt.getNumber());
            return;
        }

        List<UUID> audience = ReceiptAudience.accountsOf(receipt.getPaymentIntent()).stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .toList();

        // L'école autorise (ci-dessus), et chaque destinataire accepte. Les deux réglages répondent
        // à des questions différentes : ce que l'école envoie, ce que la famille veut recevoir.
        // Les heures calmes de chacun sont prises ici aussi — elles taisent la sonnerie, le reçu
        // reste dans le fil et compte dans la pastille au réveil.
        List<UUID> recipients = this.parentNotificationPreferenceService.accepting(
                audience, NotificationEvent.RECEIPT_ISSUED, NotificationChannel.PUSH);

        if (recipients.isEmpty()) {
            log.debug("RECEIPT_PUSH_SKIPPED: aucun compte à notifier pour le reçu {}", receipt.getNumber());
            return;
        }

        try {
            this.pushNotificationService.send(
                    recipients,
                    "Paiement enregistré",
                    String.format("%s reçus pour l'élève %s. Reçu n° %s.",
                            XofFormat.format(receipt.getAmount()),
                            Objects.toString(receipt.getStudentRegistrationNumber(), "—"),
                            receipt.getNumber()),
                    // Permet à l'application d'ouvrir directement le reçu concerné.
                    Map.of("type", "RECEIPT_ISSUED", "receiptId", receipt.getId().toString()));
        } catch (RuntimeException e) {
            // La garantie est tenue ici, et non déléguée au caractère asynchrone de l'envoi : cet
            // appel est fait dans la transaction qui émet le reçu, et une exception qui remonterait
            // l'annulerait. Le reçu est déjà numéroté, l'argent déjà encaissé — un échec de
            // notification ne peut pas défaire cela.
            log.error("RECEIPT_PUSH_FAILED: reçu {} non annoncé à {} compte(s) : {}",
                    receipt.getNumber(), recipients.size(), e.getMessage());
        }
    }
}
