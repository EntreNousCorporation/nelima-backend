package com.ypyit.neoelima.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fabrique de requêtes QueryDSL.
 *
 * <p>Les dépôts Spring Data suffisent aux lectures d'entités ; les agrégats — sommes, comptages,
 * projections en colonnes — n'ont pas d'équivalent dérivé et passeraient sinon par du JPQL en
 * chaînes de caractères, que rien ne vérifie à la compilation.
 */
@Configuration
public class QueryDslConfiguration {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(this.entityManager);
    }
}
