package com.ypyit.neoelima.domain.payment.service;

import com.ypy.paygw.payswitch.api.ProviderType;
import com.ypy.paygw.payswitch.configs.ConfigsService;
import com.ypyit.neoelima.domain.payment.dto.PaymentChannelDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Canaux de paiement mobile proposés au parent.
 *
 * <p>Remplace l'ancien {@code /payment-methods}, qui listait les opérateurs branchés en direct.
 * Ceux-ci ne sont plus intégrés un par un : l'agrégateur les expose derrière un seul tunnel, et
 * seul le canal choisi lui est transmis.
 *
 * <p>La liste dépend du fournisseur actif, parce que les codes attendus ne sont pas les mêmes d'un
 * agrégateur à l'autre. Sans fournisseur actif, elle est vide : l'application n'a alors rien à
 * proposer, ce qui est exact — aucun paiement en ligne n'est possible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentChannelCatalogue {

    /**
     * Codes attendus par Jeko. Ils sont énumérés ici et non lus dans sa configuration : celle-ci
     * ne porte qu'un canal par défaut, pas la liste de ce qu'il accepte.
     */
    private static final List<PaymentChannelDto> JEKO_CHANNELS = List.of(
            new PaymentChannelDto("wave", "Wave"),
            new PaymentChannelDto("orange", "Orange Money"),
            new PaymentChannelDto("mtn", "MTN MoMo"),
            new PaymentChannelDto("moov", "Moov Money"),
            new PaymentChannelDto("djamo", "Djamo"));

    private final ConfigsService configsService;

    public List<PaymentChannelDto> available() {
        return this.configsService.getActive()
                .map(config -> ProviderType.JEKO.equals(config.providerType())
                        ? JEKO_CHANNELS
                        : List.<PaymentChannelDto>of())
                .orElseGet(() -> {
                    log.warn("PAYMENT_CHANNELS_EMPTY: aucun fournisseur actif, aucun canal à proposer");
                    return List.of();
                });
    }
}
