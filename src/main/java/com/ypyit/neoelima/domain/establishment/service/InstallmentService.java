package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.establishment.dto.InstallmentDto;
import com.ypyit.neoelima.domain.establishment.form.InstallmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.InstallmentSearchForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InstallmentService {

    InstallmentDto create(InstallmentCreationForm creationForm) throws BusinessException;

    Page<InstallmentDto> findAll(InstallmentSearchForm searchForm, Pageable pageable);
}
