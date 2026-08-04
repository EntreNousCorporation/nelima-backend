package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InstallmentRepository extends JpaRepository<InstallmentEntity, UUID>, QuerydslPredicateExecutor<InstallmentEntity> {

    List<InstallmentEntity> findByStudentFee_Fee_Id(UUID feeId);

    /** Sert à refuser la refonte d'un échéancier déjà engagé auprès des familles. */
    boolean existsByStudentFee_Fee_IdAndStatusNot(UUID feeId, InstallmentStatus status);

    /** Tranches encore dues à une échéance donnée. Base du rappel aux familles. */
    List<InstallmentEntity> findByStatusAndDueDate(InstallmentStatus status, LocalDate dueDate);

    List<InstallmentEntity> findByStudentFee_Id(UUID studentFeeId);

    /**
     * Vrai dès qu'une tranche de cette dette a été réglée.
     *
     * <p>Commande le refus d'annuler une inscription à une activité : les remboursements sont hors
     * V1, annuler laisserait un encaissement sans contrepartie.
     */
    boolean existsByStudentFee_IdAndStatus(UUID studentFeeId, InstallmentStatus status);
}
