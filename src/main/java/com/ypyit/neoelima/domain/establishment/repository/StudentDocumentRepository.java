package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.StudentDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentDocumentRepository extends JpaRepository<StudentDocumentEntity, UUID> {

    List<StudentDocumentEntity> findByStudent_IdOrderByCreatedAtDesc(UUID studentId);
}
