package com.ypyit.neoelima.domain.feedback.repository;

import com.ypyit.neoelima.domain.feedback.entity.FeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, UUID> {

    /** Sert la limitation de débit : un compte ne dépose pas dix suggestions à la minute. */
    long countByUser_IdAndCreatedAtAfter(UUID userId, Instant since);
}
