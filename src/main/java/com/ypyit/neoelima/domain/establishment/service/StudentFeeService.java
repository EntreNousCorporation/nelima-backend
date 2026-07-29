package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.dto.StudentFeeDto;
import com.ypyit.neoelima.domain.establishment.form.StudentFeeSearchForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StudentFeeService {

    Page<StudentFeeDto> findByStudentId(UUID studentId, Boolean academical, Pageable pageable);

    Page<StudentFeeDto> findAll(StudentFeeSearchForm searchForm, Pageable pageable);
}
