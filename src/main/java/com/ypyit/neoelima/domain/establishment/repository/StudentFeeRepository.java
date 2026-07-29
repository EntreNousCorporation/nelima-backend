package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StudentFeeRepository extends JpaRepository<StudentFeeEntity, UUID>, QuerydslPredicateExecutor<StudentFeeEntity> {

}
