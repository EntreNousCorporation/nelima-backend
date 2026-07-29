package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.entity.FeeScheduleEntity;
import com.ypyit.neoelima.domain.establishment.form.FeeScheduleForm;

import java.util.List;
import java.util.UUID;

public interface FeeScheduleService {

    /**
     * Remplace l'échéancier d'un frais. La somme des tranches doit valoir le prix du frais, et
     * l'échéancier ne peut plus être modifié dès qu'une tranche a été encaissée.
     */
    List<FeeScheduleEntity> defineSchedules(UUID feeId, List<FeeScheduleForm> schedules);

    List<FeeScheduleEntity> findByFee(UUID feeId);
}
