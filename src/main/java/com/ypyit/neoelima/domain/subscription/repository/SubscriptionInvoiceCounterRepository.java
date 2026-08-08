package com.ypyit.neoelima.domain.subscription.repository;

import com.ypyit.neoelima.domain.subscription.entity.SubscriptionInvoiceCounterEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionInvoiceCounterRepository
        extends JpaRepository<SubscriptionInvoiceCounterEntity, UUID> {

    /**
     * Crée la ligne de l'année si elle n'existe pas, sans jamais lever d'erreur.
     *
     * <p>{@code ON CONFLICT DO NOTHING} plutôt qu'un INSERT rattrapé : sur PostgreSQL une violation
     * de contrainte avorte la transaction entière, et toute requête suivante échoue avec
     * « current transaction is aborted ». Le rattrapage applicatif est donc impossible ici.
     */
    @Modifying
    @Query(value = """
            insert into subscription_invoice_counter (id, year, last_sequence, created_at, updated_at)
            values (:id, :year, 0, now(), now())
            on conflict (year) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("year") int year);

    /**
     * Charge le compteur en verrou exclusif. Deux émissions simultanées s'attendent ici, ce qui
     * garantit une numérotation sans doublon ni trou.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SubscriptionInvoiceCounterEntity c where c.year = :year")
    Optional<SubscriptionInvoiceCounterEntity> findByYearForUpdate(@Param("year") int year);
}
