package com.ypyit.neoelima.domain.canteen.repository;

import com.ypyit.neoelima.domain.canteen.entity.CanteenMenuDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanteenMenuDayRepository extends JpaRepository<CanteenMenuDayEntity, UUID> {

    List<CanteenMenuDayEntity> findByEstablishment_IdAndDayBetweenOrderByDayAsc(UUID establishmentId, LocalDate from, LocalDate to);

    Optional<CanteenMenuDayEntity> findByEstablishment_IdAndDay(UUID establishmentId, LocalDate day);
}
