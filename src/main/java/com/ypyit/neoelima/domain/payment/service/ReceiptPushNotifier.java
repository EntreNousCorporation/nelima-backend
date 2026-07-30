package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Transactional(readOnly = true)
    public void announce(ReceiptEntity receipt) {
        List<UUID> recipients = ReceiptAudience.accountsOf(receipt.getPaymentIntent()).stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .toList();

        if (recipients.isEmpty()) {
            log.debug("RECEIPT_PUSH_SKIPPED: aucun compte à notifier pour le reçu {}", receipt.getNumber());
            return;
        }

        try {
            this.pushNotificationService.send(
                    recipients,
                    "Paiement enregistré",
                    String.format("%s reçus pour l'élève %s. Reçu n° %s.",
                            formatXof(receipt.getAmount()),
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

    private static String formatXof(BigDecimal amount) {
        if (Objects.isNull(amount)) {
            return "0 FCFA";
        }
        return String.format("%,d FCFA", amount.setScale(0, RoundingMode.HALF_UP).longValue())
                .replace(',', ' ');
    }
}
