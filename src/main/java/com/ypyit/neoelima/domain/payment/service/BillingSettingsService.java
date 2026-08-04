package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.properties.BillingProperties;
import com.ypyit.neoelima.domain.transverse.enums.GlobalParameterKey;
import com.ypyit.neoelima.domain.transverse.entity.GlobalParameterEntity;
import com.ypyit.neoelima.domain.transverse.repository.GlobalParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Taux de commission appliqué aux paiements en ligne.
 *
 * <p>Le taux vivait dans une variable d'environnement : le changer imposait un redéploiement. Il est
 * désormais lu en base à chaque calcul, l'environnement n'en étant plus que la valeur de repli — au
 * premier démarrage, ou si le paramètre venait à disparaître.
 *
 * <p>Lu à chaque appel et non mis en cache : un taux modifié doit s'appliquer au paiement suivant,
 * pas au prochain redémarrage. Le coût est une lecture indexée par transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingSettingsService {

    /**
     * Bornes de saisie.
     *
     * <p>Une commission ne peut pas être négative, et au-delà de 20 % il s'agit presque à coup sûr
     * d'une saisie en pourcentage là où l'on attend une fraction — 20 au lieu de 0,20 débiterait
     * vingt fois le montant de la scolarité. Le refus explicite vaut mieux que la confiance.
     */
    private static final BigDecimal MIN_RATE = BigDecimal.ZERO;
    private static final BigDecimal MAX_RATE = new BigDecimal("0.20");

    private final GlobalParameterRepository globalParameterRepository;
    private final BillingProperties billingProperties;

    /**
     * Taux courant, en fraction.
     *
     * <p>Lu par le dépôt et non par le service de paramètres : celui-ci lève une exception quand la
     * clé n'existe pas encore, ce qui marque la transaction en `rollback-only`. La rattraper ne
     * lève pas cette marque, et la validation échouait ensuite avec un message sans rapport
     * (« Transaction silently rolled back »). Un `Optional` évite le problème à la racine.
     */
    @Transactional(readOnly = true)
    public BigDecimal currentRate() {
        String stored = this.globalParameterRepository
                .findByCode(GlobalParameterKey.COMMISSION_RATE.name())
                .map(GlobalParameterEntity::getValue)
                .orElse(null);

        if (Objects.isNull(stored) || stored.isBlank()) {
            return this.billingProperties.getCommissionRate();
        }
        try {
            return new BigDecimal(stored.trim());
        } catch (NumberFormatException e) {
            // Une valeur illisible ne doit pas empêcher d'encaisser : repli sur la configuration,
            // dit assez fort pour être corrigé.
            log.error("COMMISSION_RATE_UNREADABLE: valeur « {} » ininterprétable, repli sur la "
                    + "configuration", stored);
            return this.billingProperties.getCommissionRate();
        }
    }

    /** Enregistre un nouveau taux. Réservé à l'admin YPYit par la configuration de sécurité. */
    @Transactional
    public BigDecimal updateRate(BigDecimal rate) {
        if (Objects.isNull(rate) || rate.compareTo(MIN_RATE) < 0 || rate.compareTo(MAX_RATE) > 0) {
            throw new BadRequestException(String.format(
                    "Le taux doit être une fraction comprise entre %s et %s (0,02 pour 2 %%)",
                    MIN_RATE.toPlainString(), MAX_RATE.toPlainString()));
        }

        // Quatre décimales : de quoi exprimer 0,25 % sans traîner d'arrondi flottant.
        BigDecimal normalized = rate.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();

        // Créé s'il n'existe pas encore : le paramètre n'était pas prévu à l'amorçage, et faire
        // dépendre le premier réglage d'un seed déjà passé le rendrait impossible en production.
        GlobalParameterEntity parameter = this.globalParameterRepository
                .findByCode(GlobalParameterKey.COMMISSION_RATE.name())
                .orElseGet(() -> GlobalParameterEntity.builder()
                        .code(GlobalParameterKey.COMMISSION_RATE.name())
                        .name("Taux de commission sur les paiements en ligne")
                        .build());
        parameter.setValue(normalized.toPlainString());
        this.globalParameterRepository.saveAndFlush(parameter);

        log.info("COMMISSION_RATE_UPDATED: nouveau taux {}", normalized.toPlainString());
        return normalized;
    }
}
