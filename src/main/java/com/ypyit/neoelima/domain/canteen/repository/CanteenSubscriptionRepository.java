package com.ypyit.neoelima.domain.canteen.repository;

import com.ypyit.neoelima.domain.canteen.entity.CanteenSubscriptionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CanteenSubscriptionRepository extends JpaRepository<CanteenSubscriptionEntity, UUID> {

    @EntityGraph(attributePaths = {"student", "formula"})
    List<CanteenSubscriptionEntity> findByStudent_Establishment_Id(UUID establishmentId);

    List<CanteenSubscriptionEntity> findByStudent_Id(UUID studentId);
}
