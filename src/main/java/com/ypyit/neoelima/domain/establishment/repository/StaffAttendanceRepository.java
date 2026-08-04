package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.StaffAttendanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAttendanceRepository extends JpaRepository<StaffAttendanceEntity, UUID>,
        QuerydslPredicateExecutor<StaffAttendanceEntity> {

    Optional<StaffAttendanceEntity> findByStaff_IdAndDay(UUID staffId, LocalDate day);

    List<StaffAttendanceEntity> findByStaff_Establishment_IdAndDay(UUID establishmentId, LocalDate day);

    List<StaffAttendanceEntity> findByStaff_Establishment_IdAndDayBetween(UUID establishmentId,
                                                                         LocalDate from,
                                                                         LocalDate to);

    boolean existsByStaff_Id(UUID staffId);
}
