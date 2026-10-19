package com.ypyit.neoelima.domain.canteen.repository;

import com.ypyit.neoelima.domain.canteen.entity.CanteenAttendanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanteenAttendanceRepository extends JpaRepository<CanteenAttendanceEntity, UUID> {

    List<CanteenAttendanceEntity> findByStudent_Establishment_IdAndDay(UUID establishmentId, LocalDate day);

    Optional<CanteenAttendanceEntity> findByStudent_IdAndDay(UUID studentId, LocalDate day);

    List<CanteenAttendanceEntity> findByStudent_IdAndDayBetween(UUID studentId, LocalDate from, LocalDate to);
}
