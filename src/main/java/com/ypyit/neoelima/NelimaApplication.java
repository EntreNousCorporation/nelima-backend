package com.ypyit.neoelima;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Les deux déclarations ci-dessous ne sont pas redondantes avec l'autoconfiguration Spring Boot :
 * le starter PaySwitch porte son propre {@code @EntityScan} et son propre
 * {@code @EnableJpaRepositories}. Dès qu'une de ces annotations est présente dans le contexte,
 * Spring Boot cesse de scanner le package de l'application, et toutes les entités et repositories
 * Nelima disparaissent du contexte. Les redéclarer explicitement rétablit le scan, les deux jeux
 * cohabitant sans conflit.
 */
@EnableAsync
@EnableCaching
@EnableScheduling
@SpringBootApplication
@EnableAspectJAutoProxy
@EntityScan(basePackages = "com.ypyit.neoelima")
@EnableJpaRepositories(basePackages = "com.ypyit.neoelima")
public class NelimaApplication {

    public static void main(String[] args) {
        SpringApplication.run(NelimaApplication.class, args);
    }
}
