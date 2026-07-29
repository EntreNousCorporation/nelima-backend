package com.ypyit.neoelima.domain.user.repository;

import com.ypyit.neoelima.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @Query(value = "select u.* from user_contacts uc " +
            "left join contact c on c.id =  uc.contact_id " +
            "left join users u on u.id =  uc.user_id " +
            "where c.is_primary = true and c.value = :username",
            nativeQuery = true)
    Optional<UserEntity> findByPrimaryContact(@Param("username") String username);

    @Query(value = "select exists (select 1 from user_contacts uc " +
            "left join contact c on c.id =  uc.contact_id " +
            "left join users u on u.id =  uc.user_id " +
            "where c.is_primary = true and c.value = :username)",
            nativeQuery = true)
    boolean existsByPrimaryContact(@Param("username") String username);

    @Modifying
    @Query(value = "update users set last_login = :lastLogin " +
            "where (select exists " +
            "(select 1 from user_contacts uc " +
            "left join contact c on c.id =  uc.contact_id " +
            "left join users u on u.id =  uc.user_id " +
            "where c.is_primary = true and c.value = :username)) = true", nativeQuery = true)
    void setLastLogin(@Param("lastLogin") Instant lastLogin, @Param("username") String username);
}
