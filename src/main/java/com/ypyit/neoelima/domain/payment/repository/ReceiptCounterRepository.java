package com.ypyit.neoelima.domain.payment.repository;

import com.ypyit.neoelima.domain.payment.entity.ReceiptCounterEntity;
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
public interface ReceiptCounterRepository extends JpaRepository<ReceiptCounterEntity, UUID> {

    /**
     * Crée la ligne de compteur si elle n'existe pas encore, sans jamais lever d'erreur.
     *
     * <p>{@code ON CONFLICT DO NOTHING} plutôt qu'un INSERT rattrapé par un catch : sur PostgreSQL
     * une violation de contrainte avorte la transaction entière, et toute requête suivante échoue
     * avec « current transaction is aborted ». Le rattrapage applicatif est donc impossible ici.
     *
     * <p>Si une transaction concurrente a inséré la même ligne sans avoir encore validé, cet ordre
     * attend qu'elle se termine puis ne fait rien — le comportement recherché.
     */
    @Modifying
    @Query(value = """
            insert into receipt_counter (id, establishment_id, last_sequence, created_at, updated_at)
            values (:id, :establishmentId, 0, now(), now())
            on conflict (establishment_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("establishmentId") UUID establishmentId);

    /**
     * Charge le compteur en verrou exclusif ({@code SELECT ... FOR UPDATE}). Les transactions
     * concurrentes du même établissement s'attendent ici, ce qui garantit une numérotation sans
     * doublon ni trou. Le verrou est relâché à la fin de la transaction appelante.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReceiptCounterEntity c where c.establishment.id = :establishmentId")
    Optional<ReceiptCounterEntity> findByEstablishmentIdForUpdate(@Param("establishmentId") UUID establishmentId);

    Optional<ReceiptCounterEntity> findByEstablishment_Id(UUID establishmentId);
}
