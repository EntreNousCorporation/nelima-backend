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
     * Tranches d'un établissement échéant dans une fenêtre de dates.
     *
     * <p>Base des échéances du calendrier : elles ne sont pas stockées, elles sont déduites de ce
     * que les familles doivent réellement.
     */
    List<InstallmentEntity> findByStudentFee_Student_Establishment_IdAndDueDateBetween(
            UUID establishmentId, LocalDate from, LocalDate to);

    /** Même fenêtre, pour un élève : ce que le calendrier d'un parent doit montrer. */
    List<InstallmentEntity> findByStudentFee_Student_IdAndDueDateBetween(
            UUID studentId, LocalDate from, LocalDate to);

    /**
     * Vrai dès qu'une tranche de cette dette a été réglée.
     *
     * <p>Commande le refus d'annuler une inscription à une activité : les remboursements sont hors
     * V1, annuler laisserait un encaissement sans contrepartie.
     */
    boolean existsByStudentFee_IdAndStatus(UUID studentFeeId, InstallmentStatus status);
}
