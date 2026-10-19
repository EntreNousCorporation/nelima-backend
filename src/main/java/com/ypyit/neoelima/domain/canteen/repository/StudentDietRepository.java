package com.ypyit.neoelima.domain.canteen.repository;

import com.ypyit.neoelima.domain.canteen.entity.StudentDietEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StudentDietRepository extends JpaRepository<StudentDietEntity, UUID> {

    List<StudentDietEntity> findByStudent_Id(UUID studentId);

    List<StudentDietEntity> findByStudent_IdIn(Collection<UUID> studentIds);

    void deleteByStudent_Id(UUID studentId);
}
