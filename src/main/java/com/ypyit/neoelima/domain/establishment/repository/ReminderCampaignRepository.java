package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.ReminderCampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;
import java.util.UUID;

public interface ReminderCampaignRepository extends JpaRepository<ReminderCampaignEntity, UUID>,
        QuerydslPredicateExecutor<ReminderCampaignEntity> {

    List<ReminderCampaignEntity> findByEstablishment_IdOrderBySentAtDesc(UUID establishmentId);
}
