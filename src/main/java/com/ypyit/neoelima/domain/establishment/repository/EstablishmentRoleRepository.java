package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EstablishmentRoleRepository extends JpaRepository<EstablishmentRoleEntity, UUID> {

    Optional<EstablishmentRoleEntity> findByEstablishment_IdAndRole_Id(UUID establishmentId, UUID roleId);

    List<EstablishmentRoleEntity> findByEstablishment_Id(UUID establishmentId);
}
