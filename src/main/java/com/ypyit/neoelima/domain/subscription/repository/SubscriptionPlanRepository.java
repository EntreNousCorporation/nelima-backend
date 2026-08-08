package com.ypyit.neoelima.domain.subscription.repository;

import com.ypyit.neoelima.domain.subscription.entity.SubscriptionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, java.util.UUID> {

    List<SubscriptionPlanEntity> findByOrderByPositionAscLabelAsc();

    List<SubscriptionPlanEntity> findByActiveTrueOrderByPositionAscLabelAsc();

    Optional<SubscriptionPlanEntity> findByCode(String code);

    /** Grille du site public : actives et publiées, dans l'ordre voulu. */
    List<SubscriptionPlanEntity> findByActiveTrueAndIsPublicTrueOrderByPositionAscLabelAsc();

    List<SubscriptionPlanEntity> findByFeaturedTrue();

    boolean existsByCode(String code);
}
