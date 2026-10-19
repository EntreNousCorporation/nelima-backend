package com.ypyit.neoelima.domain.transport.repository;

import com.ypyit.neoelima.domain.transport.entity.TransportSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransportSubscriptionRepository extends JpaRepository<TransportSubscriptionEntity, UUID> {

    List<TransportSubscriptionEntity> findByStudent_Establishment_Id(UUID establishmentId);

    List<TransportSubscriptionEntity> findByStudent_Id(UUID studentId);

    List<TransportSubscriptionEntity> findByLine_Id(UUID lineId);
}
