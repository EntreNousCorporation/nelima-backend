package com.ypyit.neoelima.domain.prospect.repository;

import com.ypyit.neoelima.domain.prospect.entity.DemoRequestEntity;
import com.ypyit.neoelima.domain.prospect.entity.DemoRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DemoRequestRepository extends JpaRepository<DemoRequestEntity, UUID> {

    List<DemoRequestEntity> findByOrderByCreatedAtDesc();

    long countByStatus(DemoRequestStatus status);

    /**
     * Demandes récentes portant la même adresse.
     *
     * <p>Sert de garde-fou : la route est publique, et rien n'empêche un automate d'y déposer mille
     * lignes. Compter avant d'écrire coûte une requête et évite d'avoir à nettoyer la table.
     */
    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, Instant since);

    long countByCreatedAtAfter(Instant since);
}
