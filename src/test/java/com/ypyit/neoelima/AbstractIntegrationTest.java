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
        // Les tâches planifiées sont appelées directement par leurs tests ; en arrière-plan, elles
        // touchaient aux bouchons et aux données des autres tests (voir SchedulingConfiguration).
        registry.add("nelima.scheduling.enabled", () -> "false");
        // Le secret de signature n'a plus de repli dans `application.yml` : un défaut fonctionnel
        // publié dans le dépôt laissait démarrer en silence un environnement aux jetons forgeables.
        // La suite fournit donc le sien, propre à elle et sans valeur ailleurs.
        registry.add("security.jwt.secret",
                () -> "secret-de-test-uniquement-32-caracteres-minimum");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.ypyit.neoelima.domain.user.repository.RoleRepository testRoleRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ypyit.neoelima.domain.user.repository.PermissionRepository testPermissionRepository;

    /**
     * Un rôle de test portant exactement ces permissions. Les contrôles se jouent sur les
     * permissions : un compte d'école sans rôle ne lit plus les montants, comme en vrai.
     */
    protected com.ypyit.neoelima.domain.user.entity.RoleEntity roleWith(String... permissions) {
        java.util.Set<com.ypyit.neoelima.domain.user.entity.PermissionEntity> granted = new java.util.HashSet<>();
        for (String code : permissions) {
            granted.add(this.testPermissionRepository.findByCode(code)
                    .orElseGet(() -> this.testPermissionRepository.saveAndFlush(com.ypyit.neoelima.domain.user.entity.PermissionEntity.builder()
                            .code(code).name(com.ypyit.neoelima.domain.user.entity.TranslateEntity.builder().fr(code).en(code).build()).build())));
        }
        return this.testRoleRepository.saveAndFlush(com.ypyit.neoelima.domain.user.entity.RoleEntity.builder()
                .code("TEST-" + java.util.UUID.randomUUID())
                .name(com.ypyit.neoelima.domain.user.entity.TranslateEntity.builder().fr("Rôle de test").en("Test role").build())
                .permissions(granted)
                .build());
    }
}
