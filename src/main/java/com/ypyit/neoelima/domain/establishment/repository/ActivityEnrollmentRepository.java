package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.ActivityEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityEnrollmentRepository extends JpaRepository<ActivityEnrollmentEntity, UUID>,
        QuerydslPredicateExecutor<ActivityEnrollmentEntity> {

    Optional<ActivityEnrollmentEntity> findByActivity_IdAndStudent_Id(UUID activityId, UUID studentId);

    long countByActivity_IdAndStatus(UUID activityId, EnrollmentStatus status);

    /** Liste d'attente dans l'ordre d'arrivée : la place libérée revient au premier demandeur. */
    List<ActivityEnrollmentEntity> findByActivity_IdAndStatusOrderByRequestedAtAsc(
            UUID activityId, EnrollmentStatus status);

    @EntityGraph(attributePaths = {"student", "activity"})
    List<ActivityEnrollmentEntity> findByActivity_Establishment_IdOrderByRequestedAtDesc(UUID establishmentId);

    List<ActivityEnrollmentEntity> findByStudent_IdAndStatusIn(UUID studentId, List<EnrollmentStatus> statuses);

    List<ActivityEnrollmentEntity> findByActivity_IdAndStatusIn(UUID activityId, List<EnrollmentStatus> statuses);
}
