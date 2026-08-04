package com.ypyit.neoelima.domain.establishment.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.DuplicateResourceException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.exception.ValidationException;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.QEstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentLevelOfStudyCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentRootCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentUpdateForm;
import com.ypyit.neoelima.domain.establishment.form.LevelOfStudySelectForm;
import com.ypyit.neoelima.domain.establishment.mapper.EstablishmentMapper;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.EstablishmentService;
import com.ypyit.neoelima.domain.establishment.service.LevelOfStudyService;
import com.ypyit.neoelima.domain.storage.dto.StorageDto;
import com.ypyit.neoelima.domain.storage.service.StorageService;
import com.ypyit.neoelima.domain.transverse.entity.FileMediaEntity;
import com.ypyit.neoelima.domain.transverse.form.FileMediaCreateForm;
import com.ypyit.neoelima.domain.transverse.form.FileMediaUpdateForm;
import com.ypyit.neoelima.domain.transverse.mapper.FileMediaMapper;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import com.ypyit.neoelima.domain.user.form.EstablishmentPrincipalCreationForm;
import com.ypyit.neoelima.domain.user.mapper.UserMapper;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.ContactService;
import com.ypyit.neoelima.domain.utils.FunctionalUtils;
import com.ypyit.neoelima.domain.utils.StorageUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class EstablishmentServiceImpl implements EstablishmentService {

    private final EstablishmentRepository establishmentRepository;

    private final EstablishmentMapper establishmentMapper;

    private final ContactService contactService;

    private final StorageService storageService;

    private final FileMediaMapper fileMediaMapper;

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final UserMapper userMapper;

    private final LevelOfStudyService levelOfStudyService;

    @Override
    public Page<EstablishmentLiteDto> findAll(EstablishmentSearchForm searchForm, Pageable pageable) throws BusinessException {
        try {

            BooleanBuilder builder = new BooleanBuilder();
            QEstablishmentEntity establishment = QEstablishmentEntity.establishmentEntity;

            if (Objects.isNull(searchForm.getIsPrimary())) {
                builder.and(establishment.isPrimary.eq(true));
            }
            if (Objects.nonNull(searchForm.getIsPrimary())) {
                builder.and(establishment.isPrimary.eq(searchForm.getIsPrimary()));
            }

            if (Objects.nonNull(searchForm.getParentId())) {
                builder.and(establishment.parent.id.eq(searchForm.getParentId()));
            }
            Page<EstablishmentEntity> result = this.establishmentRepository.findAll(builder, pageable);

            List<EstablishmentLiteDto> response = result.get()
                    .map(entity -> {
                        EstablishmentLiteDto dto = this.establishmentMapper.toLiteDto(entity);
                        dto.setSubsidiaries(this.establishmentMapper.toLiteDtos(this.establishmentRepository.findByParent_Id(entity.getId())));
                        return dto;
                    }).collect(Collectors.toList());

            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public EstablishmentDto findById(UUID id) throws BusinessException {
        try {
            EstablishmentEntity company = this.establishmentRepository
                    .findById(id).orElseThrow(() -> new NotFoundException(String.format("Cannot find company place with provided identifier %s", id)));
            return this.establishmentMapper.toDto(company);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public boolean existsById(UUID id) throws BusinessException {
        try {
            return this.establishmentRepository.existsById(id);
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public EstablishmentDto update(UUID id, EstablishmentUpdateForm updateForm) throws BusinessException {
        try {
            EstablishmentEntity establishment = this.establishmentRepository.findById(id)
                    .orElseThrow(() -> new
                            NotFoundException(String
                            .format("Company with id %s not found", id)));
            if (!CollectionUtils.isEmpty(updateForm.getContacts())) {
                this.validateContacts(updateForm.getContacts());
                this.manageContacts(updateForm, establishment);
            }
            if (StringUtils.isNotBlank(updateForm.getName())) {
                Optional<EstablishmentEntity> byNameIgnoreCase = this.establishmentRepository.findByNameIgnoreCase(updateForm.getName());
                if (byNameIgnoreCase.isPresent() && !byNameIgnoreCase.get().getId().equals(establishment.getId())) {
                    throw new DuplicateResourceException(
                            String.format("Establishment with name %s already exists", updateForm.getName()));
                }
            }
            this.establishmentMapper.toUpdate(updateForm, establishment);
            this.updateCoverImage(updateForm.getCoverImage(), establishment);
            return this.establishmentMapper.toDto(this.establishmentRepository.save(establishment));
        } catch (NotFoundException | ValidationException | DuplicateResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public EstablishmentEntity create(EstablishmentRootCreationForm creationForm) throws BusinessException {
        try {
            this.validateEstablishment(creationForm.getName(), creationForm.getContacts());
            FunctionalUtils.checkDuplicatedOnCreation(creationForm.getContacts());
            EstablishmentEntity establishment = this.establishmentMapper.toEntity(creationForm);
            establishment.setBucketName(StorageUtils.generateFileName());
            // Un établissement créé par YPYit est opérationnel dès sa création : le drapeau
            // servira à en suspendre un, pas à le laisser naître inerte. Faute de l'écrire, il
            // valait faux partout, et la console du parc annonçait des écoles désactivées qui
            // encaissaient normalement.
            establishment.setActive(true);
            this.createCoverImage(creationForm.getCoverImage(), establishment);
            return this.establishmentRepository.save(establishment);
        } catch (BadRequestException | DuplicateResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public EstablishmentDto createInternal(EstablishmentCreationForm creationForm) throws BusinessException {
        try {
            EstablishmentEntity parentEstablishment = this.establishmentRepository.findById(creationForm.getParentId())
                    .orElseThrow(() -> new
                            NotFoundException(String
                            .format("Company with id %s not found", creationForm.getParentId())));
            this.validateEstablishment(creationForm.getName(), creationForm.getContacts());
            EstablishmentEntity establishment = this.establishmentMapper.toEntity(creationForm);
            establishment.setBucketName(StorageUtils.generateFileName());
            establishment.setActive(true);
            this.createCoverImage(creationForm.getCoverImage(), establishment);
            establishment.setParent(parentEstablishment);
            EstablishmentEntity savedEstablishment = this.establishmentRepository.save(establishment);
            EstablishmentUserEntity establishmentPrincipal = this.createEstablishmentPrincipal(creationForm.getPrincipal());
            if (Objects.isNull(establishmentPrincipal)) {
                establishmentPrincipal = (EstablishmentUserEntity) parentEstablishment.getPrincipal();
            }
            savedEstablishment.setPrincipal(establishmentPrincipal);
            establishmentPrincipal.setEstablishment(savedEstablishment);
            this.establishmentRepository.saveAndFlush(savedEstablishment);
            this.userRepository.saveAndFlush(establishmentPrincipal);
            return this.establishmentMapper.toDto(savedEstablishment);
        } catch (ValidationException | DuplicateResourceException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public EstablishmentLiteDto createLevelOfStudies(UUID id, EstablishmentLevelOfStudyCreationForm creationForm) throws BusinessException {
        try {
            EstablishmentEntity establishment = this.establishmentRepository.findById(id)
                    .orElseThrow(() -> new
                            NotFoundException(String
                            .format("Company with id %s not found", id)));
            Pair<Set<LevelOfStudyEntity>, Set<String>> selectedValues = this.levelOfStudyService.selectValues(LevelOfStudySelectForm.builder()
                    .endLevelOfStudy(creationForm.getEndLevelOfStudy())
                    .levelOfStudiesCodes(creationForm.getLevelOfStudiesCodes())
                    .startLevelOfStudy(creationForm.getStartLevelOfStudy())
                    .build()
            );
            establishment.getLevelOfStudies().addAll(selectedValues.getFirst());
            return this.establishmentMapper.toLiteDto(this.establishmentRepository.save(establishment));
        } catch (BadRequestException | DuplicateResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    private void manageContacts(EstablishmentUpdateForm updateForm, EstablishmentEntity establishment) {
        if (CollectionUtils.isEmpty(updateForm.getContacts())) {
            return;
        }
        FunctionalUtils.checkDuplicatedOnUpdate(updateForm.getContacts());
        Set<ContactEntity> contacts = this.contactService.createOrUpdate(updateForm.getContacts());
        establishment.getContacts().clear();
        establishment.getContacts().addAll(contacts);
    }

    private void validateEstablishment(String establishmentName, Set<ContactCreationForm> contacts) {
        if (this.establishmentRepository.existsByNameIgnoreCase(establishmentName)) {
            throw new DuplicateResourceException(
                    String.format("Establishment with name %s already exists", establishmentName));
        }
        long count = contacts.stream().filter(ContactCreationForm::getIsPrimary).count();
        if (count == 0) {
            throw new BadRequestException("Cannot create establishment without primary contacts");
        }
        if (count > 1) {
            throw new BadRequestException("Cannot create establishment with more primary contacts");
        }
    }

    private void createCoverImage(FileMediaCreateForm logoCreationForm, EstablishmentEntity establishment) {
        if (Objects.nonNull(logoCreationForm)) {
            Optional<String> savedFile = this.storageService.uploadDynamicFile(StorageDto.builder()
                    .base64(logoCreationForm.getBase64())
                    .bucketName(establishment.getBucketName())
                    .fileExtension(logoCreationForm.getExtension()).build());
            savedFile.ifPresent(link -> {
                if (Objects.isNull(establishment.getCoverImage())) {
                    FileMediaEntity fileMedia = this.fileMediaMapper.toEntity(logoCreationForm);
                    establishment.setCoverImage(fileMedia);
                }
                establishment.getCoverImage().setLink(link);
            });
        }
    }

    private void updateCoverImage(FileMediaUpdateForm logoUpdateForm, EstablishmentEntity establishment) {
        if (Objects.nonNull(logoUpdateForm) && StringUtils.isNotBlank(logoUpdateForm.getBase64())) {
            String oldFileName = FunctionalUtils.getOrNull(() -> establishment.getCoverImage().getLink());

            if (StringUtils.isBlank(establishment.getBucketName())) {
                establishment.setBucketName(StorageUtils.generateFileName());
            }
            Optional<String> savedFile = storageService.uploadDynamicFile(StorageDto.builder()
                    .base64(logoUpdateForm.getBase64())
                    .bucketName(establishment.getBucketName())
                    .fileExtension(logoUpdateForm.getExtension()).build());

            savedFile.ifPresent(link -> {
                if (Objects.isNull(establishment.getCoverImage())) {
                    FileMediaEntity coverImage = this.fileMediaMapper.toEntity(logoUpdateForm);
                    establishment.setCoverImage(coverImage);
                }
                establishment.getCoverImage().setLink(link);
                if (Objects.nonNull(oldFileName)) {
                    this.storageService.deleteQuietly(establishment.getBucketName(), FilenameUtils.getName(oldFileName));
                }
            });
        }
    }

    private void validateContacts(Set<ContactUpdateForm> contacts) {
        long count = contacts.stream().filter(ContactUpdateForm::getIsPrimary).count();
        if (count == 0) {
            throw new BadRequestException("Cannot create establishment without primary contacts");
        }
        if (count > 1) {
            throw new BadRequestException("Cannot create establishment with more primary contacts");
        }
        FunctionalUtils.checkDuplicatedOnUpdate(contacts);
    }

    private void validateUpdateContacts(Set<ContactCreationForm> contacts) {
        long count = contacts.stream().filter(ContactCreationForm::getIsPrimary).count();
        if (count == 0) {
            throw new BadRequestException("Cannot create establishment without primary contacts");
        }
        if (count > 1) {
            throw new BadRequestException("Cannot create establishment with more primary contacts");
        }
        FunctionalUtils.checkDuplicatedOnCreation(contacts);
    }

    private EstablishmentUserEntity createEstablishmentPrincipal(EstablishmentPrincipalCreationForm principal) {
        if (Objects.nonNull(principal)) {
            this.validateUpdateContacts(principal.getContacts());
            RoleEntity role = this.roleRepository.findById(principal.getRoleId())
                    .orElseThrow(() -> new NotFoundException(String.format("Role with id %s not found", principal.getRoleId())));
            EstablishmentUserEntity user = this.userMapper.toPrincipalEntity(principal);
            user.setRole(role);
            return this.userRepository.save(user);
        }
        return null;
    }

}
