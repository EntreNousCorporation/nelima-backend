package com.ypyit.neoelima.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Migrations Nelima, déclarées explicitement.
 *
 * <p>Le starter PaySwitch expose un bean {@code Flyway} pour ses propres migrations. Or
 * l'autoconfiguration Flyway de Spring Boot est conditionnée par {@code @ConditionalOnMissingBean(Flyway.class)} :
 * la seule présence de ce bean la désactive intégralement, et les migrations de
 * {@code classpath:db/migration} ne s'exécutent plus. Le symptôme est trompeur — Hibernate échoue
 * au démarrage sur une table manquante, comme si la base n'avait jamais été migrée, ce qui est
 * exactement le cas.
 *
 * <p>On reprend donc la main sur notre instance Flyway. Chacune conserve sa propre table
 * d'historique.
 *
 * <p>Les migrations Nelima vivent sous {@code db/nelima} et non sous {@code db/migration} : le
 * scanner de classpath de Flyway fonctionne par préfixe, si bien que scanner {@code db/migration}
 * ramène aussi {@code db/migration-payswitch}. Les deux V1 entraient alors en collision avec un
 * « Found more than one migration with version 1 ».
 */
@Configuration
public class FlywayConfiguration {

    static final String NELIMA_FLYWAY = "nelimaFlyway";
    static final String PAYSWITCH_FLYWAY = "payswitchFlyway";

    @Bean(name = NELIMA_FLYWAY, initMethod = "migrate")
    public Flyway nelimaFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/nelima")
                .table("flyway_schema_history")
                .baselineOnMigrate(false)
                .load();
    }

    /**
     * Hibernate valide le schéma au démarrage, et son périmètre couvre aussi les entités de
     * PaySwitch. Les deux jeux de migrations doivent donc avoir été appliqués avant la
     * construction de l'{@code EntityManagerFactory}, sans quoi la validation échoue sur des
     * tables qui n'existent pas encore.
     */
    @Configuration
    static class JpaDependsOnFlyway extends EntityManagerFactoryDependsOnPostProcessor {

        JpaDependsOnFlyway() {
            super(NELIMA_FLYWAY, PAYSWITCH_FLYWAY);
        }
    }
}
