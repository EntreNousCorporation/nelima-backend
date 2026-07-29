package com.ypyit.neoelima.domain.transverse.service.impl;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.DuplicateResourceException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.transverse.dto.GlobalParameterDto;
import com.ypyit.neoelima.domain.transverse.entity.GlobalParameterEntity;
import com.ypyit.neoelima.domain.transverse.form.GlobalParameterUpdateForm;
import com.ypyit.neoelima.domain.transverse.mapper.GlobalParameterMapper;
import com.ypyit.neoelima.domain.transverse.repository.GlobalParameterRepository;
import com.ypyit.neoelima.domain.transverse.service.GlobalParameterService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class GlobalParameterServiceImpl implements GlobalParameterService {

    private final GlobalParameterMapper globalParameterMapper;
    private final GlobalParameterRepository globalParameterRepository;

    @Override
    public GlobalParameterDto update(String code, GlobalParameterUpdateForm updateForm) throws BusinessException {
        try {
            GlobalParameterEntity globalParameter = globalParameterRepository.findByCode(code)
                    .orElseThrow(() -> new
                            NotFoundException(String.format("Global parameter with code %s not found", code)));
            if (StringUtils.isNotBlank(updateForm.getName())) {
                Optional<GlobalParameterEntity> actual = this.globalParameterRepository
                        .findByNameIgnoreCase(updateForm.getName());
                if (actual.isPresent() && !code.equals(actual.get().getCode())) {
                    throw new DuplicateResourceException(String
                            .format("Global parameter with name %s already exists", updateForm.getName()));
                }
                globalParameter.setName(updateForm.getName());
            }
            this.globalParameterMapper.toUpdate(updateForm, globalParameter);
            return this.globalParameterMapper.toDto(this.globalParameterRepository.save(globalParameter));
        } catch (NotFoundException | DuplicateResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GlobalParameterDto findByCode(String code) throws BusinessException {
        try {
            GlobalParameterEntity globalParameter = globalParameterRepository.findByCode(code)
                    .orElseThrow(() -> new
                            NotFoundException(String.format("Global parameter with code %s not found", code)));
            return this.globalParameterMapper.toDto(globalParameter);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Object getValue(String key) throws BusinessException {
        try {
            Optional<GlobalParameterEntity> globalParameters = this.globalParameterRepository.findByCode(key);
            return globalParameters.<Object>map(GlobalParameterEntity::getValue).orElse(null);
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Object getOrDefaultValue(String key, Object defaultValue) throws BusinessException {
        try {
            Optional<GlobalParameterEntity> globalParameters = this.globalParameterRepository.findByCode(key);
            if (globalParameters.isEmpty()) {
                Objects.requireNonNull(defaultValue, "Default value must not be null");
                return defaultValue;
            }
            return globalParameters.get().getValue();
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GlobalParameterDto> getAll(Pageable pageable) throws BusinessException {
        try {
            Page<GlobalParameterEntity> result = this.globalParameterRepository.findAll(pageable);

            List<GlobalParameterDto> response = result.get()
                    .map(this.globalParameterMapper::toDto)
                    .collect(Collectors.toList());

            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}
