package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeScheduleEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.FeeScheduleRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Décline l'échéancier d'un frais en dettes datées pour un élève.
 *
 * <p>Les tranches sont recopiées et non lues à la volée depuis le gabarit : l'échéancier d'un élève
 * ne doit pas se déformer si l'école ajuste le frais en cours d'année pour les inscriptions
 * suivantes. C'est le même principe que la recopie des libellés sur un reçu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallmentGenerator {

    private final FeeScheduleRepository feeScheduleRepository;
    private final InstallmentRepository installmentRepository;

    /**
     * Crée les tranches dues par l'élève pour ce frais.
     *
     * <p>Sans échéancier défini, le frais est dû en une fois : c'est le cas des frais
     * d'inscription et des frais ponctuels, qui ne se morcellent pas.
     */
    @Transactional
    public List<InstallmentEntity> generateFor(StudentFeeEntity studentFee) {
        FeeEntity fee = studentFee.getFee();
        List<FeeScheduleEntity> schedules = this.feeScheduleRepository
                .findByFee_IdOrderByPositionAsc(fee.getId());

        if (CollectionUtils.isEmpty(schedules)) {
            return List.of(this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                    .studentFee(studentFee)
                    .label(fee.getName())
                    .amount(fee.getPrice())
                    .dueDate(dueDateOf(studentFee))
                    .status(InstallmentStatus.PENDING)
                    .build()));
        }

        return schedules.stream()
                .map(schedule -> this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                        .studentFee(studentFee)
                        .feeSchedule(schedule)
                        .label(schedule.getLabel())
                        .amount(schedule.getAmount())
                        .dueDate(schedule.getDueDate())
                        .status(InstallmentStatus.PENDING)
                        .build()))
                .toList();
    }

    /** Un frais réglé en une fois hérite de l'échéance portée par la dette, si elle existe. */
    private static LocalDate dueDateOf(StudentFeeEntity studentFee) {
        if (Objects.isNull(studentFee.getDeadline())) {
            return null;
        }
        return studentFee.getDeadline().atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
