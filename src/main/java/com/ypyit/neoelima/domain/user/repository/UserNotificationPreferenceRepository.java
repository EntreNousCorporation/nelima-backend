package com.ypyit.neoelima.domain.user.repository;

import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import com.ypyit.neoelima.domain.user.entity.UserNotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserNotificationPreferenceRepository
        extends JpaRepository<UserNotificationPreferenceEntity, UUID> {

    Optional<UserNotificationPreferenceEntity> findByUser_IdAndEventAndChannel(
            UUID userId, NotificationEvent event, NotificationChannel channel);
}
