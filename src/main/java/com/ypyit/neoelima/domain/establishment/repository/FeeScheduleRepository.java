package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.FeeScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeeScheduleRepository extends JpaRepository<FeeScheduleEntity, UUID>,
        QuerydslPredicateExecutor<FeeScheduleEntity> {

    List<FeeScheduleEntity> findByFee_IdOrderByPositionAsc(UUID feeId);

    void deleteByFee_Id(UUID feeId);
}
