package com.ypyit.neoelima;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Socle des tests d'intégration : un PostgreSQL réel, sur lequel Flyway applique les migrations
 * comme en production. Le conteneur est démarré une seule fois pour toute la JVM et partagé entre
 * les classes de test — Testcontainers le supprime à l'arrêt via son conteneur Ryuk.
 *
 * <p>On teste sur un vrai PostgreSQL et non sur une base en mémoire parce que le schéma est piloté
 * par Flyway et que la validation Hibernate au démarrage n'a de sens que face au moteur cible.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("nelima_test")
                    .withUsername("nelima")
                    .withPassword("nelima")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("nelima.initialize-data", () -> "false");
    }
}
