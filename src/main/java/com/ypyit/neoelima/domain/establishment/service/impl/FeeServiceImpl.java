package com.ypyit.neoelima.domain.establishment.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.DuplicateResourceException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.exception.ValidationException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.FeeDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.QFeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.form.FeeCreationForm;
import com.ypyit.neoelima.domain.establishment.form.FeeSearchForm;
import com.ypyit.neoelima.domain.establishment.form.FeeUpdateForm;
import com.ypyit.neoelima.domain.establishment.form.LevelOfStudySelectForm;
import com.ypyit.neoelima.domain.establishment.mapper.FeeMapper;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.FeeService;
import com.ypyit.neoelima.domain.establishment.service.LevelOfStudyService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class FeeServiceImpl implements FeeService {

    private final FeeRepository feeRepository;
    private final FeeMapper feeMapper;
    private final EstablishmentRepository establishmentRepository;
    private final StudentRepository studentRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final LevelOfStudyService levelOfStudyService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public FeeDto create(FeeCreationForm creationForm) throws BusinessException {
        try {
            // Portée dérivée de l'utilisateur authentifié : sans cela, un utilisateur
            // d'établissement créerait des frais dans une autre école.
            UUID establishmentId = this.currentUserProvider
                    .resolveEstablishmentScope(creationForm.getEstablishmentId());
            if (Objects.isNull(establishmentId)) {
                throw new AccessDeniedException("An explicit establishment is required to create a fee");
            }
            if (this.feeRepository.existsByEstablishment_IdAndNameIgnoreCase(establishmentId, creationForm.getName())) {
                throw new DuplicateResourceException(String.format("Un frais portant ce nom existe déjà.", creationForm.getName()));
            }
            if (CollectionUtils.isEmpty(creationForm.getLevelOfStudiesCodes()) &&
                    (StringUtils.isBlank(creationForm.getStartLevelOfStudy()) && StringUtils.isBlank(creationForm.getEndLevelOfStudy()))) {
                throw new ValidationException("levelOfStudy", "Level of studies must have at least one level of study");
            }
            EstablishmentEntity establishment = this.establishmentRepository.findById(establishmentId)
                    .orElseThrow(() -> new NotFoundException(String.format("Establishment with id %s not found", establishmentId)));
            FeeEntity fee = this.feeMapper.toEntity(creationForm);
            fee.setEstablishment(establishment);
            Pair<Set<LevelOfStudyEntity>, Set<String>> selectedValues = this.levelOfStudyService.selectValues(LevelOfStudySelectForm.builder()
                    .endLevelOfStudy(creationForm.getEndLevelOfStudy())
                    .levelOfStudiesCodes(creationForm.getLevelOfStudiesCodes())
                    .startLevelOfStudy(creationForm.getStartLevelOfStudy())
                    .build()
            );
            // Copie mutable : selectValues peut renvoyer un ensemble immuable, et l'addAll qui
            // suit échouait alors en UnsupportedOperationException — donc sur tout appel
            // fournissant des codes de niveaux, c'est-à-dire le cas normal depuis l'interface.
            Set<String> finalLevelOfStudyCodes = new HashSet<>(selectedValues.getSecond());
            fee.getLevelOfStudies().addAll(selectedValues.getFirst());
            if (CollectionUtils.isNotEmpty(creationForm.getLevelOfStudiesCodes())) {
                finalLevelOfStudyCodes.addAll(creationForm.getLevelOfStudiesCodes());
            }
            FeeEntity savedFee = this.feeRepository.saveAndFlush(fee);
            this.createStudentFees(savedFee, establishmentId, finalLevelOfStudyCodes);
            return this.feeMapper.toDto(savedFee);
        } catch (NotFoundException | ValidationException | DuplicateResourceException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public FeeDto update(UUID id, FeeUpdateForm updateForm) throws BusinessException {
        try {
            FeeEntity fee = this.feeRepository.findById(id)
                    .orElseThrow(() ->
                            new NotFoundException(String.format("Fee with provided id %s not found", id)));
            if (StringUtils.isNotBlank(updateForm.getName())) {
                Optional<FeeEntity> existingName = this.feeRepository
                        .findByEstablishment_IdAndNameIgnoreCase(fee.getEstablishment().getId(), updateForm.getName());
                if (existingName.isPresent() && !id.equals(existingName.get().getId())) {
                    throw new DuplicateResourceException(String.format("Un frais portant ce nom existe déjà.",
                            updateForm.getName()));
                }
            }
            this.feeMapper.toUpdate(updateForm, fee);
            return this.feeMapper.toDto(this.feeRepository.save(fee));
        } catch (NotFoundException | DuplicateResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public Page<FeeDto> search(FeeSearchForm searchForm, Pageable pageable) {
        try {

            BooleanBuilder builder = new BooleanBuilder();
            QFeeEntity fee = QFeeEntity.feeEntity;

            if (Objects.nonNull(searchForm.getOptional())) {
                builder.and(fee.optional.eq(searchForm.getOptional()));
            }
            if (Objects.nonNull(searchForm.getAcademical())) {
                builder.and(fee.academical.eq(searchForm.getAcademical()));
            }
            UUID establishmentScope = this.currentUserProvider
                    .resolveEstablishmentScope(searchForm.getEstablishmentId());
            if (Objects.nonNull(establishmentScope)) {
                builder.and(fee.establishment.id.eq(establishmentScope));
            }
            List<Predicate> levelOfStudies = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(searchForm.getLevelOfStudies())) {
                searchForm.getLevelOfStudies().forEach(levelOfStudy -> levelOfStudies
                        .add(fee.levelOfStudies.any().code.eq(levelOfStudy)));
                builder.andAnyOf(levelOfStudies.toArray(new Predicate[0]));
            }
            Page<FeeEntity> result = this.feeRepository.findAll(builder, pageable);

            List<FeeDto> response = result.get()
                    .map(this.feeMapper::toDto).collect(Collectors.toList());
            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public FeeDto findById(UUID id) throws BusinessException {
        try {
            FeeEntity fee = this.feeRepository.findById(id)
                    .orElseThrow(() -> new
                            NotFoundException(String
                            .format("Fee with id %s not found", id)));
            return this.feeMapper.toDto(fee);
        } catch (BadRequestException | DuplicateResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public boolean existsById(UUID id) throws BusinessException {
        try {
            return this.feeRepository.existsById(id);
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    private void createStudentFees(FeeEntity fee, UUID establishmentId, Set<String> levelOfStudyCodes) {
        List<StudentEntity> studyCodeIn = this.studentRepository.findByEstablishment_IdAndLevelOfStudy_CodeIn(establishmentId, levelOfStudyCodes);
        if (CollectionUtils.isNotEmpty(studyCodeIn)) {
            for (StudentEntity entity : studyCodeIn) {
                this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                        .student(entity)
                        .fee(fee)
                        .build());
            }
        }
    }
}
