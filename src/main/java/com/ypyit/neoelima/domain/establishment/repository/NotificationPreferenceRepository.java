package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.NotificationPreferenceEntity;
import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreferenceEntity, UUID> {

    List<NotificationPreferenceEntity> findByEstablishment_Id(UUID establishmentId);

    Optional<NotificationPreferenceEntity> findByEstablishment_IdAndEventAndChannel(
            UUID establishmentId, NotificationEvent event, NotificationChannel channel);
}
