package com.ypyit.neoelima.domain.establishment.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.InstallmentDto;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.form.InstallmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.InstallmentSearchForm;
import com.ypyit.neoelima.domain.establishment.mapper.InstallmentMapper;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.InstallmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class InstallmentServiceImpl implements InstallmentService {

    private final InstallmentRepository installmentRepository;

    private final InstallmentMapper installmentMapper;

    private final StudentFeeRepository studentFeeRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public InstallmentDto create(InstallmentCreationForm creationForm) throws BusinessException {
        try {
            StudentFeeEntity studentFee = this.studentFeeRepository
                    .findById(creationForm.getStudentFeeId())
                    .orElseThrow(() -> new NotFoundException(String
                            .format("Student fee with id %s not found", creationForm.getStudentFeeId())));
            InstallmentEntity installment = this.installmentMapper.toEntity(creationForm);
            installment.setStudentFee(studentFee);
            return this.installmentMapper.toDto(this.installmentRepository.save(installment));
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public Page<InstallmentDto> findAll(InstallmentSearchForm searchForm, Pageable pageable) {
        try {

            BooleanBuilder builder = new BooleanBuilder();
            QInstallmentEntity installment = QInstallmentEntity.installmentEntity;

            // Deux modes de lecture, et non un seul : une école consulte les tranches de son
            // périmètre, un parent celles de ses enfants. Dériver une portée établissement dans
            // tous les cas refusait au parent l'accès à ses propres échéances — c'est-à-dire à
            // tout l'écran de paiement de l'application mobile.
            if (Objects.nonNull(searchForm.getStudentId())) {
                StudentEntity student = this.studentRepository.findById(searchForm.getStudentId())
                        .orElseThrow(() -> new NotFoundException(String.format(
                                "Student with provided id %s not found", searchForm.getStudentId())));
                // Contrôle plus strict que le filtre établissement : couvre l'admin YPYit, l'école
                // de l'élève et le tuteur rattaché, et rien d'autre.
                this.currentUserProvider.assertCanAccessStudent(student);
                builder.and(installment.studentFee.student.id.eq(student.getId()));
            } else if (this.currentUserProvider.hasEstablishmentScope()) {
                // Sans élève ciblé, la portée vient de l'utilisateur authentifié, jamais du
                // formulaire : sinon une école lirait les tranches d'une autre en changeant le
                // paramètre.
                UUID establishmentScope = this.currentUserProvider
                        .resolveEstablishmentScope(searchForm.getEstablishmentId());
                if (Objects.nonNull(establishmentScope)) {
                    builder.and(installment.studentFee.student.establishment.id.eq(establishmentScope));
                }
            } else {
                // Un parent sans élève désigné consulte l'échéancier de tous ses enfants — c'est ce
                // que demande son écran « Échéances ». Lui refuser la lecture l'obligerait à un
                // appel par enfant ; lui ouvrir la requête sans filtre lui donnerait la plateforme
                // entière. Le périmètre est donc la liste de ses enfants, et rien d'autre.
                List<UUID> childrenIds = this.studentRepository
                        .findByParentUsers_Id(this.currentUserProvider.currentUser().getId())
                        .stream().map(StudentEntity::getId).toList();

                if (childrenIds.isEmpty()) {
                    // Aucun enfant rattaché : une contrainte impossible plutôt qu'aucune contrainte.
                    // Omettre le filtre retournerait ici toutes les tranches de la plateforme.
                    return new PageImpl<>(List.of(), pageable, 0);
                }
                builder.and(installment.studentFee.student.id.in(childrenIds));
            }
            if (Objects.nonNull(searchForm.getStatus())) {
                builder.and(installment.status.eq(searchForm.getStatus()));
            }
            if (Objects.nonNull(searchForm.getDueBefore())) {
                builder.and(installment.dueDate.before(searchForm.getDueBefore()));
            }
            Page<InstallmentEntity> result = this.installmentRepository.findAll(builder, pageable);

            List<InstallmentDto> response = result.get()
                    .map(this.installmentMapper::toDto).collect(Collectors.toList());

            return new PageImpl<>(response, pageable, result.getTotalElements());
        } catch (NotFoundException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}
