package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentLevelOfStudyCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentRootCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentUpdateForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EstablishmentService {

    Page<EstablishmentLiteDto> findAll(EstablishmentSearchForm searchForm, Pageable pageable) throws BusinessException;

    EstablishmentDto findById(UUID id) throws BusinessException;

    boolean existsById(UUID id) throws BusinessException;

    EstablishmentDto update(UUID id, EstablishmentUpdateForm updateForm) throws BusinessException;

    EstablishmentEntity create(EstablishmentRootCreationForm creationForm) throws BusinessException;

    EstablishmentDto createInternal(EstablishmentCreationForm creationForm) throws BusinessException;

    EstablishmentLiteDto createLevelOfStudies(UUID id, EstablishmentLevelOfStudyCreationForm creationForm) throws BusinessException;
}
