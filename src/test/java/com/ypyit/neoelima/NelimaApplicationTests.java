package com.ypyit.neoelima;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Démarre le contexte complet sur une base vierge : Flyway applique V1__baseline.sql puis
 * Hibernate valide le schéma. Ce test échoue donc dès qu'une entité diverge des migrations.
 */
class NelimaApplicationTests extends AbstractIntegrationTest {

    @Test
    @DisplayName("le contexte démarre et le schéma Flyway valide les entités")
    void contextLoads() {
    }

}
