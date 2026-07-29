package com.ypyit.neoelima.config;

import com.ypy.paygw.payswitch.api.Environment;
import com.ypy.paygw.payswitch.api.ProviderType;
import com.ypy.paygw.payswitch.configs.ConfigsService;
import com.ypy.paygw.payswitch.configs.ProviderConfigDto;
import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.config.properties.JekoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'amorçage doit rendre la base conforme à l'environnement du serveur, quel que soit son état
 * de départ : première installation, redémarrage sans changement, ou rotation des clés.
 */
class PaymentProviderBootstrapTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentProviderBootstrap bootstrap;
    @Autowired
    private ConfigsService configsService;
    @Autowired
    private JekoProperties jekoProperties;

    @BeforeEach
    void setUp() {
        configsService.list().forEach(config -> configsService.delete(config.id()));
        jekoProperties.setApiKey("key-initial");
        jekoProperties.setApiKeyId("key-id");
        jekoProperties.setWebhookSecret("whsec");
        jekoProperties.setStoreId("store-1");
        jekoProperties.setDefaultPaymentMethod("wave");
        jekoProperties.setEnvironment("SANDBOX");
    }

    @Test
    @DisplayName("au premier démarrage, la configuration Jeko est créée et activée")
    void createsAndActivatesOnFirstBoot() {
        bootstrap.synchronizeJekoConfiguration();

        List<ProviderConfigDto> configs = configsService.list();
        assertThat(configs).hasSize(1);
        ProviderConfigDto config = configs.get(0);
        assertThat(config.providerType()).isEqualTo(ProviderType.JEKO);
        assertThat(config.environment()).isEqualTo(Environment.SANDBOX);
        assertThat(config.credentials())
                .containsEntry("api_key", "key-initial")
                .containsEntry("store_id", "store-1");
        assertThat(config.settings())
                .as("Jeko exige toujours un canal de paiement")
                .containsEntry("default_payment_method", "wave");
        assertThat(config.active()).isTrue();
    }

    @Test
    @DisplayName("un redémarrage sans changement ne duplique ni ne réécrit la configuration")
    void isIdempotentAcrossRestarts() {
        bootstrap.synchronizeJekoConfiguration();
        ProviderConfigDto first = configsService.list().get(0);

        bootstrap.synchronizeJekoConfiguration();
        bootstrap.synchronizeJekoConfiguration();

        List<ProviderConfigDto> configs = configsService.list();
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).id())
                .as("la configuration doit être réutilisée, pas recréée")
                .isEqualTo(first.id());
        assertThat(configs.get(0).updatedAt())
                .as("aucune écriture inutile, la piste d'audit reste lisible")
                .isEqualTo(first.updatedAt());
    }

    @Test
    @DisplayName("une rotation de clé côté serveur est répercutée au démarrage suivant")
    void propagatesRotatedCredentials() {
        bootstrap.synchronizeJekoConfiguration();
        ProviderConfigDto before = configsService.list().get(0);

        jekoProperties.setApiKey("key-rotated");
        bootstrap.synchronizeJekoConfiguration();

        List<ProviderConfigDto> configs = configsService.list();
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).id()).isEqualTo(before.id());
        assertThat(configs.get(0).credentials()).containsEntry("api_key", "key-rotated");
        assertThat(configs.get(0).active()).isTrue();
    }

    @Test
    @DisplayName("sans credentials complets, aucune configuration n'est créée")
    void doesNothingWithoutCredentials() {
        jekoProperties.setApiKey("");

        bootstrap.synchronizeJekoConfiguration();

        assertThat(configsService.list())
                .as("un fournisseur à moitié configuré serait pire que pas de fournisseur")
                .isEmpty();
    }
}
