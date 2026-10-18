package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.TimetableSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlotEntity, UUID> {

    List<TimetableSlotEntity> findBySchoolClass_IdOrderByDayOfWeekAscStartTimeAsc(UUID schoolClassId);

    List<TimetableSlotEntity> findBySchoolClass_IdAndDayOfWeek(UUID schoolClassId, DayOfWeek dayOfWeek);

    List<TimetableSlotEntity> findByTeacher_IdAndDayOfWeek(UUID teacherId, DayOfWeek dayOfWeek);
}
