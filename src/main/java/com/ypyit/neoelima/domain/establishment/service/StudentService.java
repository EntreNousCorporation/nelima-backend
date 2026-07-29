package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentLiteDto;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentStudentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.StudentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.StudentUpdateForm;
import com.ypyit.neoelima.domain.storage.form.StorageCreationForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StudentService {

    StudentDto findByEstablishment(EstablishmentStudentSearchForm searchForm) throws BusinessException;

    Page<StudentDto> search(StudentSearchForm searchForm, Pageable pageable) throws BusinessException;

    StudentDto create(StudentCreationForm creationForm) throws BusinessException;

    StudentDto update(UUID id, StudentUpdateForm updateForm) throws BusinessException;

    List<StudentDto> importFromFile(StorageCreationForm creationForm) throws BusinessException;

    Page<StudentLiteDto> findByEstablishmentId(UUID establishmentId, Pageable pageable) throws BusinessException;
}
