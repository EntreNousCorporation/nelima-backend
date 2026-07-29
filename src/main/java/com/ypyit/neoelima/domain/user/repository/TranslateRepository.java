package com.ypyit.neoelima.domain.user.repository;

import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TranslateRepository extends JpaRepository<TranslateEntity, UUID> {

}
