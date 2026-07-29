package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.establishment.dto.FeeDto;
import com.ypyit.neoelima.domain.establishment.form.FeeCreationForm;
import com.ypyit.neoelima.domain.establishment.form.FeeSearchForm;
import com.ypyit.neoelima.domain.establishment.form.FeeUpdateForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FeeService {

    FeeDto create(FeeCreationForm creationForm) throws BusinessException;

    FeeDto update(UUID id, FeeUpdateForm updateForm) throws BusinessException;

    Page<FeeDto> search(FeeSearchForm searchForm, Pageable pageable);

    FeeDto findById(UUID id) throws BusinessException;

    boolean existsById(UUID id) throws BusinessException;
}
