package com.ypyit.neoelima.domain.establishment.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.InstallmentDto;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.form.InstallmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.InstallmentSearchForm;
import com.ypyit.neoelima.domain.establishment.mapper.InstallmentMapper;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.service.InstallmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class InstallmentServiceImpl implements InstallmentService {

    private final InstallmentRepository installmentRepository;

    private final InstallmentMapper installmentMapper;

    private final StudentFeeRepository studentFeeRepository;

    @Override
    public InstallmentDto create(InstallmentCreationForm creationForm) throws BusinessException {
        try {
            StudentFeeEntity studentFee = this.studentFeeRepository
                    .findById(creationForm.getStudentFeeId())
                    .orElseThrow(() -> new NotFoundException(String
                            .format("Student fee with id %s not found", creationForm.getStudentFeeId())));
            InstallmentEntity installment = this.installmentMapper.toEntity(creationForm);
            installment.setStudentFee(studentFee);
            return this.installmentMapper.toDto(this.installmentRepository.save(installment));
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public Page<InstallmentDto> findAll(InstallmentSearchForm searchForm, Pageable pageable) {
        try {

            BooleanBuilder builder = new BooleanBuilder();
            QInstallmentEntity installment = QInstallmentEntity.installmentEntity;

            if (Objects.nonNull(searchForm.getStudentId())) {
                builder.and(installment.studentFee.student.id.eq(searchForm.getStudentId()));
            }

            if (Objects.nonNull(searchForm.getEstablishmentId())) {
                builder.and(installment.studentFee.student.establishment.id.eq(searchForm.getEstablishmentId()));
            }
            Page<InstallmentEntity> result = this.installmentRepository.findAll(builder, pageable);

            List<InstallmentDto> response = result.get()
                    .map(this.installmentMapper::toDto).collect(Collectors.toList());

            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}
