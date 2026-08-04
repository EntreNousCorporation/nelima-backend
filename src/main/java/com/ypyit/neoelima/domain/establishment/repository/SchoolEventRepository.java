package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.SchoolEventEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SchoolEventRepository extends JpaRepository<SchoolEventEntity, UUID>,
        QuerydslPredicateExecutor<SchoolEventEntity> {

    /**
     * Événements d'un établissement sur une fenêtre de dates.
     *
     * <p>Les classes visées partent avec : le calendrier les affiche sur chaque entrée, et sans ce
     * graphe un mois chargé coûterait une requête par événement.
     */
    @EntityGraph(attributePaths = "classes")
    List<SchoolEventEntity> findByEstablishment_IdAndDateBetweenOrderByDateAsc(
            UUID establishmentId, LocalDate from, LocalDate to);
}
