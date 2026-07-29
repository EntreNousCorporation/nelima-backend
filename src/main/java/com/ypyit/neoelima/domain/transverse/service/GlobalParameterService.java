package com.ypyit.neoelima.domain.transverse.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.transverse.dto.GlobalParameterDto;
import com.ypyit.neoelima.domain.transverse.form.GlobalParameterUpdateForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GlobalParameterService {

    GlobalParameterDto update(String code, GlobalParameterUpdateForm updateForm) throws BusinessException;

    GlobalParameterDto findByCode(String code) throws BusinessException;

    Object getValue(String key) throws BusinessException;

    Object getOrDefaultValue(String key, Object defaultValue) throws BusinessException;

    Page<GlobalParameterDto> getAll(Pageable pageable) throws BusinessException;
}
