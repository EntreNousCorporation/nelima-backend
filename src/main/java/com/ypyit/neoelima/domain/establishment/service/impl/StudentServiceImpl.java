package com.ypyit.neoelima.domain.establishment.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.DuplicateResourceException;
import com.ypyit.neoelima.common.exception.NonUniqueUserFoundException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentStudentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.StudentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.StudentUpdateForm;
import com.ypyit.neoelima.domain.establishment.mapper.StudentMapper;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.storage.dto.StorageDto;
import com.ypyit.neoelima.domain.storage.form.StorageCreationForm;
import com.ypyit.neoelima.domain.storage.mapper.StorageMapper;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.RoleType;
import com.ypyit.neoelima.domain.user.form.MobileUserSignupForm;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
import com.ypyit.neoelima.domain.utils.FunctionalUtils;
import com.ypyit.neoelima.domain.utils.StorageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ypyit.neoelima.domain.utils.StorageUtils.PLACE_OF_BIRTH;
import static com.ypyit.neoelima.domain.utils.StorageUtils.STUDENTS_SHEET_NAME;
import static com.ypyit.neoelima.domain.utils.StorageUtils.STUDENT_BIRTHDAY;
import static com.ypyit.neoelima.domain.utils.StorageUtils.STUDENT_FIRST_NAME;
import static com.ypyit.neoelima.domain.utils.StorageUtils.STUDENT_LAST_NAME;
import static com.ypyit.neoelima.domain.utils.StorageUtils.STUDENT_LEVEL_OF_STUDY;
import static com.ypyit.neoelima.domain.utils.StorageUtils.STUDENT_REGISTRATION_NUMBER;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;
    private final LevelOfStudyRepository levelOfStudyRepository;
    private final EstablishmentRepository establishmentRepository;
    private final StorageMapper storageMapper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final FeeRepository feeRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public StudentDto findByEstablishment(EstablishmentStudentSearchForm searchForm) throws BusinessException {
        try {

            BooleanBuilder builder = new BooleanBuilder();

            QStudentEntity student = QStudentEntity.studentEntity;

            if (Objects.nonNull(searchForm.getEstablishmentId())) {
                builder.and(student.establishment.id.eq(searchForm.getEstablishmentId()));
            }
            if (StringUtils.isNotBlank(searchForm.getRegistrationNumber())) {
                builder.and(student.registrationNumber.eq(searchForm.getRegistrationNumber()));
            }
            if (Objects.nonNull(searchForm.getBirthDay())) {
                builder.and(student.birthDay.eq(searchForm.getBirthDay()));
            }

            List<StudentEntity> students = new ArrayList<>();
            Iterable<StudentEntity> all = this.studentRepository.findAll(builder);
            all.forEach(students::add);
            if (CollectionUtils.isEmpty(students)) {
                throw new NotFoundException("User not found");
            }
            if (students.size() > 1) {
                throw new NonUniqueUserFoundException("Multiple users found");
            }

            return this.studentMapper.toDto(students.get(0));
        } catch (NotFoundException | NonUniqueUserFoundException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public Page<StudentDto> search(StudentSearchForm searchForm, Pageable pageable) throws BusinessException {
        try {

            BooleanBuilder builder = new BooleanBuilder();

            QStudentEntity student = QStudentEntity.studentEntity;

            // La portée vient de l'utilisateur authentifié, jamais du formulaire : sinon un
            // utilisateur d'établissement listerait les élèves d'une autre école en changeant
            // simplement le paramètre.
            UUID establishmentScope = this.currentUserProvider
                    .resolveEstablishmentScope(searchForm.getEstablishmentId());
            if (Objects.nonNull(establishmentScope)) {
                builder.and(student.establishment.id.eq(establishmentScope));
            }
            if (StringUtils.isNotBlank(searchForm.getKeyword())) {
                String keyword = searchForm.getKeyword().trim();
                builder.and(student.registrationNumber.containsIgnoreCase(keyword)
                        .or(student.firstName.containsIgnoreCase(keyword))
                        .or(student.lastName.containsIgnoreCase(keyword)));
            }
            List<Predicate> levelOfStudies = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(searchForm.getLevelOfStudies())) {
                searchForm.getLevelOfStudies().forEach(levelOfStudy -> levelOfStudies
                        .add(student.levelOfStudy.code.eq(levelOfStudy)));
                builder.andAnyOf(levelOfStudies.toArray(new Predicate[0]));
            }
            Page<StudentEntity> result = this.studentRepository.findAll(builder, pageable);
            List<StudentDto> response = result.get()
                    .map(this.studentMapper::toDto).collect(Collectors.toList());
            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (NotFoundException | NonUniqueUserFoundException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public StudentDto create(StudentCreationForm creationForm) throws BusinessException {
        try {
            UUID establishmentId = this.requireWritableEstablishment(creationForm.getEstablishmentId());
            if (this.studentRepository.existsByEstablishment_IdAndRegistrationNumber(establishmentId,
                    creationForm.getRegistrationNumber())) {
                throw new DuplicateResourceException(String.format("Student with provided registration number %s already exists",
                        creationForm.getRegistrationNumber()));
            }
            StudentEntity student = this.studentMapper.toEntity(creationForm);
            StudentEntity savedStudent = this.studentRepository.save(student);
            this.checkEstablishment(establishmentId, savedStudent, creationForm.getLevelOfStudyCode());
            this.checkLevelOfStudy(creationForm.getLevelOfStudyCode(), savedStudent);
            this.manageParent(creationForm.getParentId(), creationForm.getParent(), savedStudent);
            return this.studentMapper.toDto(this.studentRepository.save(savedStudent));
        } catch (NotFoundException | DuplicateResourceException | BadRequestException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public StudentDto update(UUID id, StudentUpdateForm updateForm) throws BusinessException {
        try {
            StudentEntity student = this.studentRepository.findById(id)
                    .orElseThrow(() ->
                            new NotFoundException(String.format("Student with provided id %s not found", id)));
            this.currentUserProvider.assertCanAccessStudent(student);
            if (StringUtils.isNotBlank(updateForm.getRegistrationNumber())) {
                Optional<StudentEntity> existingNumber = this.studentRepository
                        .findByEstablishment_IdAndRegistrationNumber(student.getEstablishment().getId(),
                                updateForm.getRegistrationNumber());
                if (existingNumber.isPresent() && !id.equals(existingNumber.get().getId())) {
                    throw new DuplicateResourceException(String.format("Student with provided registration number %s already exists",
                            updateForm.getRegistrationNumber()));
                }
            }
            this.studentMapper.toUpdate(updateForm, student);
            this.checkLevelOfStudy(updateForm.getLevelOfStudyCode(), student);
            return this.studentMapper.toDto(this.studentRepository.save(student));
        } catch (NotFoundException | DuplicateResourceException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public List<StudentDto> importFromFile(StorageCreationForm creationForm) throws BusinessException {
        try {
            UUID establishmentId = this.requireWritableEstablishment(creationForm.getEstablishmentId());
            EstablishmentEntity establishment = this.establishmentRepository.findById(establishmentId)
                    .orElseThrow(() -> new NotFoundException(String.format("Establishment with id %s not found", establishmentId)));
            List<StudentEntity> studentEntities = this.readEstablishmentStudentsFile(creationForm, establishment);
            if (CollectionUtils.isNotEmpty(studentEntities)) {
                this.studentRepository.saveAllAndFlush(studentEntities);
            }
            return this.studentMapper.toDtos(studentEntities);
        } catch (NotFoundException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public Page<StudentLiteDto> findByEstablishmentId(UUID establishmentId, Pageable pageable) throws BusinessException {
        try {
            UUID scope = this.requireWritableEstablishment(establishmentId);
            Page<StudentEntity> result = this.studentRepository.findAllByEstablishment_Id(scope, pageable);
            List<StudentLiteDto> response = result.get()
                    .map(this.studentMapper::toLiteDto).collect(Collectors.toList());
            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (NotFoundException | NonUniqueUserFoundException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    /**
     * Portée établissement pour une opération d'écriture ou une lecture ciblée. Un utilisateur
     * d'établissement est épinglé au sien ; un admin YPYit doit désigner explicitement sa cible.
     */
    private UUID requireWritableEstablishment(UUID requestedEstablishmentId) {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(requestedEstablishmentId);
        if (Objects.isNull(scope)) {
            throw new AccessDeniedException("An explicit establishment is required for this operation");
        }
        return scope;
    }

    private void checkEstablishment(UUID establishmentId, StudentEntity student, String levelOfStudyCode) {
        if (Objects.nonNull(establishmentId)) {
            EstablishmentEntity establishment = this.establishmentRepository.findById(establishmentId)
                    .orElseThrow(() -> new NotFoundException(String.format("Establishment with id %s not found", establishmentId)));
            boolean levelOfStudyNotExists = FunctionalUtils.safelyGetStream(establishment.getLevelOfStudies())
                    .noneMatch(levelOfStudyEntity -> levelOfStudyCode.equals(levelOfStudyEntity.getCode()));
            if (levelOfStudyNotExists) {
                throw new BadRequestException("Level of study code " + levelOfStudyCode + " does not exist within this establishment");
            }
            List<FeeEntity> allByEstablishmentId = this.feeRepository.findAllByEstablishment_Id(establishmentId);
            if (CollectionUtils.isNotEmpty(allByEstablishmentId)) {
                for (FeeEntity fee : allByEstablishmentId) {
                    boolean studentLevelOfStudyExists = FunctionalUtils
                            .safelyGetStream(fee.getLevelOfStudies()).anyMatch(v -> levelOfStudyCode.equals(v.getCode()));
                    if (studentLevelOfStudyExists) {
                        this.studentFeeRepository.save(StudentFeeEntity.builder()
                                .student(student)
                                .fee(fee)
                                .build());
                    }
                }
            }
            student.setEstablishment(establishment);
        }
    }

    private void checkLevelOfStudy(String levelOfStudyCode, StudentEntity student) {
        if (StringUtils.isNotBlank(levelOfStudyCode)) {
            LevelOfStudyEntity levelOfStudy = this.levelOfStudyRepository.findByCode(levelOfStudyCode)
                    .orElseThrow(() -> new NotFoundException(String.format("Level of study with code %s not found", levelOfStudyCode)));
            student.setLevelOfStudy(levelOfStudy);
        }
    }


    private List<StudentEntity> readEstablishmentStudentsFile(StorageCreationForm creationForm, EstablishmentEntity establishment) {
        List<StudentEntity> entities = new ArrayList<>();
        try {
            StorageDto dto = this.storageMapper.toDto(creationForm);
            String fileAbsoluteNameOnSystem = StorageUtils
                    .generateAbsoluteFileName(dto.getFileName(), dto.getFileExtension());
            Optional<String> fileIsCreatedOnSystem = StorageUtils
                    .createFileOnSystem(dto, fileAbsoluteNameOnSystem);
            if (fileIsCreatedOnSystem.isEmpty()) {
                log.warn("Cannot create file on system {}", fileAbsoluteNameOnSystem);
                return List.of();
            }

            FileInputStream file = new FileInputStream(fileAbsoluteNameOnSystem);
            XSSFWorkbook workbook = new XSSFWorkbook(file);
            XSSFSheet sheet = workbook.getSheet(STUDENTS_SHEET_NAME);
            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) {

                Row row = rowIterator.next();

                Cell firstName = row.getCell(STUDENT_FIRST_NAME);
                Cell lastName = row.getCell(STUDENT_LAST_NAME);
                Cell registrationNumber = row.getCell(STUDENT_REGISTRATION_NUMBER);
                Cell birthDay = row.getCell(STUDENT_BIRTHDAY);
                Cell levelOfStudy = row.getCell(STUDENT_LEVEL_OF_STUDY);
                Cell placeOfBirth = row.getCell(PLACE_OF_BIRTH);

                if (this.studentRepository.existsByEstablishment_IdAndRegistrationNumber(establishment.getId(),
                        registrationNumber.getStringCellValue())) {
                    throw new DuplicateResourceException(String.format("Student with provided registration number %s already exists",
                            registrationNumber.getStringCellValue()));
                }
                LevelOfStudyEntity levelOfStudyInDb = this.levelOfStudyRepository.findByCode(levelOfStudy.getStringCellValue())
                        .orElseThrow(() -> new NotFoundException(String.format("Level of study with code %s not found", levelOfStudy.getStringCellValue())));
                entities.add(StudentEntity.builder()
                        .establishment(establishment)
                        .firstName(firstName.getStringCellValue())
                        .lastName(lastName.getStringCellValue())
                        .levelOfStudy(levelOfStudyInDb)
                        .registrationNumber(registrationNumber.getStringCellValue())
                        .birthDay(LocalDate.from(birthDay.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()))
                        .placeOfBirth(placeOfBirth.getStringCellValue())
                        .build());
            }
            file.close();
        } catch (Exception e) {
            log.error("Cannot read file on system", e);
        }
        return entities;
    }

    private void manageParent(UUID parentId, MobileUserSignupForm parentCreationForm, StudentEntity student) {
        if (Objects.nonNull(parentId) && Objects.nonNull(parentCreationForm)) {
            throw new BadRequestException("Only one parent can be specified at the moment");
        }
        if (Objects.nonNull(parentId)) {
            Optional<UserEntity> parent = this.userRepository.findById(parentId);
            if (parent.isEmpty()) {
                throw new NotFoundException(String.format("Cannot find user with provided id %s", parentId));
            }
            if (!RoleType.STUDENT_PARENT.name().equals(parent.get().getRole().getCode())) {
                throw new BadRequestException("Only mobile user is allowed");
            }
            student.getParentUsers().add(parent.get());
        }
        if (Objects.nonNull(parentCreationForm)) {
            parentCreationForm.setSendEmail(false);
            UserDto mobileUser = this.userService.createMobileUser(parentCreationForm);
            Optional<UserEntity> parent = this.userRepository.findById(mobileUser.getId());
            if (parent.isEmpty()) {
                throw new NotFoundException(String.format("Cannot find user with provided id %s", parentId));
            }
            student.getParentUsers().add(parent.get());
            ContactEntity contact = parent.get().getContacts().stream()
                    .filter(ContactEntity::isPrimary).findFirst().get();
            this.userService.sendStudentParentInvitation(contact);
        }
    }
}
