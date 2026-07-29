package com.ypyit.neoelima.domain.establishment.service.impl;

import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.exception.ValidationException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeScheduleEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.form.FeeScheduleForm;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeScheduleRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.service.FeeScheduleService;
import com.ypyit.neoelima.domain.establishment.service.InstallmentGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FeeScheduleServiceImpl implements FeeScheduleService {

    private final FeeRepository feeRepository;
    private final FeeScheduleRepository feeScheduleRepository;
    private final InstallmentRepository installmentRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final InstallmentGenerator installmentGenerator;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public List<FeeScheduleEntity> defineSchedules(UUID feeId, List<FeeScheduleForm> schedules) {
        FeeEntity fee = this.feeRepository.findById(feeId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Fee with provided id %s not found", feeId)));
        this.assertCallerOwns(fee);

        if (CollectionUtils.isEmpty(schedules)) {
            throw new ValidationException("schedules", "At least one installment must be defined");
        }
        this.assertAmountsMatchFeePrice(fee, schedules);

        // Refondre un échéancier déjà engagé fausserait la comptabilité de l'école et ce que les
        // familles ont déjà vu. On l'interdit dès qu'une tranche a quitté l'état PENDING.
        if (this.installmentRepository.existsByStudentFee_Fee_IdAndStatusNot(feeId, InstallmentStatus.PENDING)) {
            throw new ValidationException("schedules",
                    "Installments have already been collected for this fee, its schedule can no longer be changed");
        }

        this.installmentRepository.deleteAll(this.installmentRepository.findByStudentFee_Fee_Id(feeId));
        this.feeScheduleRepository.deleteByFee_Id(feeId);
        this.feeScheduleRepository.flush();

        List<FeeScheduleEntity> saved = new ArrayList<>();
        int position = 1;
        for (FeeScheduleForm form : schedules) {
            saved.add(this.feeScheduleRepository.saveAndFlush(FeeScheduleEntity.builder()
                    .fee(fee)
                    .label(form.getLabel())
                    .amount(form.getAmount())
                    .dueDate(form.getDueDate())
                    .position(position++)
                    .build()));
        }

        this.regenerateStudentInstallments(feeId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeeScheduleEntity> findByFee(UUID feeId) {
        FeeEntity fee = this.feeRepository.findById(feeId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Fee with provided id %s not found", feeId)));
        this.assertCallerOwns(fee);
        return this.feeScheduleRepository.findByFee_IdOrderByPositionAsc(feeId);
    }

    /**
     * Un échéancier qui ne totalise pas le prix du frais laisserait une dette impossible à solder,
     * ou ferait payer trop. La contrainte n'est pas exprimable en SQL, elle est tenue ici.
     */
    private void assertAmountsMatchFeePrice(FeeEntity fee, List<FeeScheduleForm> schedules) {
        BigDecimal total = schedules.stream()
                .map(FeeScheduleForm::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (Objects.isNull(fee.getPrice()) || total.compareTo(fee.getPrice()) != 0) {
            throw new ValidationException("schedules", String.format(
                    "Installments total %s but the fee is priced at %s", total, fee.getPrice()));
        }
    }

    /** Les élèves déjà porteurs de ce frais reçoivent le nouvel échéancier. */
    private void regenerateStudentInstallments(UUID feeId) {
        List<StudentFeeEntity> studentFees = this.studentFeeRepository.findByFee_Id(feeId);
        for (StudentFeeEntity studentFee : studentFees) {
            List<InstallmentEntity> generated = this.installmentGenerator.generateFor(studentFee);
            log.debug("Generated {} installments for student fee {}", generated.size(), studentFee.getId());
        }
    }

    private void assertCallerOwns(FeeEntity fee) {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(
                Objects.isNull(fee.getEstablishment()) ? null : fee.getEstablishment().getId());
        if (Objects.nonNull(scope)
                && Objects.nonNull(fee.getEstablishment())
                && !scope.equals(fee.getEstablishment().getId())) {
            throw new AccessDeniedException("Fee " + fee.getId() + " belongs to another establishment");
        }
    }
}
