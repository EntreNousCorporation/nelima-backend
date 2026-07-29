package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.establishment.dto.LevelOfStudyDto;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.form.LevelOfStudySelectForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;

import java.util.Set;

public interface LevelOfStudyService {

    Pair<Set<LevelOfStudyEntity>, Set<String>> selectValues(LevelOfStudySelectForm selectForm) throws BusinessException;

    Page<LevelOfStudyDto> getAll(Pageable pageable) throws BusinessException;
}
