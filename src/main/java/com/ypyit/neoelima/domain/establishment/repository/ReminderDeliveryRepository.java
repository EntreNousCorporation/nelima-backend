package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.ReminderDeliveryEntity;
import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReminderDeliveryRepository extends JpaRepository<ReminderDeliveryEntity, UUID>,
        QuerydslPredicateExecutor<ReminderDeliveryEntity> {

    /**
     * Ce destinataire a-t-il déjà été relancé aujourd'hui, sur ce canal, pour cette tranche ?
     *
     * <p>Consulté avant chaque envoi. La contrainte d'unicité en base reste le garde-fou final :
     * cette vérification évite l'échec, elle ne le remplace pas.
     */
    boolean existsByInstallment_IdAndRecipient_IdAndChannelAndSentOn(
            UUID installmentId, UUID recipientId, ReminderChannel channel, LocalDate sentOn);

    List<ReminderDeliveryEntity> findByCampaign_Id(UUID campaignId);
}
