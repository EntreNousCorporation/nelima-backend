package com.ypyit.neoelima.domain.establishment.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.establishment.dto.StudentFeeDto;
import com.ypyit.neoelima.domain.establishment.entity.QStudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.form.StudentFeeSearchForm;
import com.ypyit.neoelima.domain.establishment.mapper.StudentFeeMapper;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentFeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class StudentFeeServiceImpl implements StudentFeeService {

    private final StudentFeeRepository studentFeeRepository;

    private final StudentFeeMapper studentFeeMapper;


    @Override
    public Page<StudentFeeDto> findByStudentId(UUID studentId, Boolean academical, Pageable pageable) {
        try {
            BooleanBuilder builder = new BooleanBuilder();
            QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;

            builder.and(studentFee.student.id.eq(studentId));
            if (Objects.nonNull(academical)) {
                builder.and(studentFee.fee.academical.eq(academical));
            }

            Page<StudentFeeEntity> result = this.studentFeeRepository.findAll(builder, pageable);

            List<StudentFeeDto> response = result.get()
                    .map(this.studentFeeMapper::toDto).collect(Collectors.toList());

            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public Page<StudentFeeDto> findAll(StudentFeeSearchForm searchForm, Pageable pageable) {
        try {
            BooleanBuilder builder = new BooleanBuilder();
            QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;


            if (Objects.nonNull(searchForm.getAcademical())) {
                builder.and(studentFee.fee.academical.eq(searchForm.getAcademical()));
            }
            if (Objects.nonNull(searchForm.getStudentId())) {
                builder.and(studentFee.student.id.eq(searchForm.getStudentId()));
            }
            Page<StudentFeeEntity> result = this.studentFeeRepository.findAll(builder, pageable);

            List<StudentFeeDto> response = result.get()
                    .map(this.studentFeeMapper::toDto).collect(Collectors.toList());

            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}
