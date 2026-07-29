package com.ypyit.neoelima.domain.establishment.service.impl;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.exception.ValidationException;
import com.ypyit.neoelima.domain.establishment.dto.LevelOfStudyDto;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.form.LevelOfStudySelectForm;
import com.ypyit.neoelima.domain.establishment.mapper.LevelOfStudyMapper;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.service.LevelOfStudyService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class LevelOfStudyServiceImpl implements LevelOfStudyService {

    private final LevelOfStudyRepository levelOfStudyRepository;
    private final LevelOfStudyMapper levelOfStudyMapper;

    @Override
    public Pair<Set<LevelOfStudyEntity>, Set<String>> selectValues(LevelOfStudySelectForm selectForm) throws BusinessException {
        try {
            Set<LevelOfStudyEntity> allByCodeIn = new HashSet<>();
            Set<String> levelOfStudyCodes = this.getLevelOfStudies(selectForm.getStartLevelOfStudy(), selectForm.getEndLevelOfStudy());
            if (CollectionUtils.isNotEmpty(levelOfStudyCodes)) {
                allByCodeIn.addAll(this.levelOfStudyRepository.findAllByCodeIn(levelOfStudyCodes));
            }
            if (CollectionUtils.isNotEmpty(selectForm.getLevelOfStudiesCodes())) {
                if (StringUtils.isNotBlank(selectForm.getStartLevelOfStudy())
                        && selectForm.getLevelOfStudiesCodes().contains(selectForm.getStartLevelOfStudy())) {
                    throw new ValidationException("levelOfStudy", "Level of studies already exists");
                }
                if (StringUtils.isNotBlank(selectForm.getEndLevelOfStudy())
                        && selectForm.getLevelOfStudiesCodes().contains(selectForm.getEndLevelOfStudy())) {
                    throw new ValidationException("levelOfStudy", "Level of studies already exists");
                }
                selectForm.getLevelOfStudiesCodes().forEach(levelOfStudyCode -> {
                    if (!this.levelOfStudyRepository.existsByCode(levelOfStudyCode)) {
                        throw new NotFoundException(String.format("Level of study with code %s not found", levelOfStudyCode));
                    }
                });
                allByCodeIn.addAll(this.levelOfStudyRepository.findAllByCodeIn(selectForm.getLevelOfStudiesCodes()));
            }
            return Pair.of(allByCodeIn, levelOfStudyCodes);
        } catch (NotFoundException | ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }

    @Override
    public Page<LevelOfStudyDto> getAll(Pageable pageable) throws BusinessException {
        try {
            Page<LevelOfStudyEntity> result = this.levelOfStudyRepository.findAll(pageable);
            List<LevelOfStudyDto> response = result.get()
                    .map(this.levelOfStudyMapper::toDto)
                    .collect(Collectors.toList());

            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    private Set<String> getLevelOfStudies(String startLevelOfStudy, String endLevelOfStudy) {
        if (StringUtils.isBlank(startLevelOfStudy) || StringUtils.isBlank(endLevelOfStudy)) {
            return Set.of();
        }
        if (!this.levelOfStudyRepository.existsByCode(startLevelOfStudy)) {
            throw new NotFoundException(String.format("Level of study with code %s not found", startLevelOfStudy));
        }
        if (!this.levelOfStudyRepository.existsByCode(endLevelOfStudy)) {
            throw new NotFoundException(String.format("Level of study with code %s not found", endLevelOfStudy));
        }
        List<LevelOfStudyEntity> levelOfStudies = this.levelOfStudyRepository.findAll();
        levelOfStudies.sort(Comparator.comparingInt(LevelOfStudyEntity::getPosition));
        return getLevelOfStudiesToSaved(startLevelOfStudy, endLevelOfStudy, levelOfStudies);
    }

    private Set<String> getLevelOfStudiesToSaved(String startLevelOfStudy, String endLevelOfStudy, List<LevelOfStudyEntity> levelOfStudies) {
        LinkedList<String> levelOfStudyCodes = new LinkedList<>();
        Set<String> levelOfStudyCodesToSave = new HashSet<>();
        for (LevelOfStudyEntity levelOfStudyEntity : levelOfStudies) {
            levelOfStudyCodes.add(levelOfStudyEntity.getCode());
        }
        if (levelOfStudyCodes.indexOf(startLevelOfStudy) > levelOfStudyCodes.indexOf(endLevelOfStudy)) {
            throw new ValidationException("levelOfStudyCodes", "Start element must be before end element");
        }
        for (int i = levelOfStudyCodes.indexOf(startLevelOfStudy); i <= levelOfStudyCodes.indexOf(endLevelOfStudy); i++) {
            levelOfStudyCodesToSave.add(levelOfStudyCodes.get(i));
        }
        return levelOfStudyCodesToSave;
    }
}
