package com.ypyit.neoelima.domain.establishment.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.StudentFeeDto;
import com.ypyit.neoelima.domain.establishment.entity.QStudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.form.StudentFeeSearchForm;
import com.ypyit.neoelima.domain.establishment.mapper.StudentFeeMapper;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentFeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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

    private final StudentRepository studentRepository;

    private final CurrentUserProvider currentUserProvider;


    @Override
    public Page<StudentFeeDto> findByStudentId(UUID studentId, Boolean academical, Pageable pageable) {
        try {
            // Les frais d'un élève sont lisibles par son école ou par un tuteur rattaché,
            // jamais par n'importe quel utilisateur authentifié qui connaîtrait l'identifiant.
            this.assertCanAccessStudent(studentId);

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
        } catch (NotFoundException | AccessDeniedException e) {
            throw e;
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
                this.assertCanAccessStudent(searchForm.getStudentId());
                builder.and(studentFee.student.id.eq(searchForm.getStudentId()));
            }
            // Sans élève ciblé, la requête reste bornée à l'établissement de l'appelant :
            // sinon elle retournerait les dettes de toutes les écoles de la plateforme.
            UUID establishmentScope = this.currentUserProvider.resolveEstablishmentScope(null);
            if (Objects.nonNull(establishmentScope)) {
                builder.and(studentFee.student.establishment.id.eq(establishmentScope));
            }
            Page<StudentFeeEntity> result = this.studentFeeRepository.findAll(builder, pageable);

            List<StudentFeeDto> response = result.get()
                    .map(this.studentFeeMapper::toDto).collect(Collectors.toList());

            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (NotFoundException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    private void assertCanAccessStudent(UUID studentId) {
        StudentEntity student = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Student with provided id %s not found", studentId)));
        this.currentUserProvider.assertCanAccessStudent(student);
    }
}
