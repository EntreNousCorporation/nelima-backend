package com.ypyit.neoelima.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tâches planifiées (rattrapage des paiements, rappels d'échéance), actives sauf avis contraire.
 *
 * <p>La suite de tests les coupe ({@code nelima.scheduling.enabled=false}) : le rattrapage des
 * paiements démarrait avec chaque contexte et appelait l'agrégateur bouchonné pendant qu'un test
 * le programmait. Mockito ne supporte pas ces appels concurrents, la réponse programmée se perdait
 * et le test échouait au hasard. Les tests des tâches les appellent directement.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "nelima.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
