package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface LevelOfStudyRepository extends JpaRepository<LevelOfStudyEntity, UUID> {

    Optional<LevelOfStudyEntity> findByCode(String code);

    boolean existsByCode(String code);

    Set<LevelOfStudyEntity> findAllByCodeIn(Set<String> codes);
}
