package com.ypyit.neoelima.domain.writeoff.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.utils.XofFormat;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.service.AuditService;
import com.ypyit.neoelima.domain.establishment.service.SchoolYearGuard;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.writeoff.dto.WriteOffDto;
import com.ypyit.neoelima.domain.writeoff.entity.DebtWriteOffEntity;
import com.ypyit.neoelima.domain.writeoff.enums.WriteOffStatus;
import com.ypyit.neoelima.domain.writeoff.form.WriteOffForms;
import com.ypyit.neoelima.domain.writeoff.repository.DebtWriteOffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Passage en perte d'une dette irrécouvrable, en deux temps.
 *
 * <p>La comptabilité propose, avec un motif ; la direction valide ou refuse. Celui qui propose ne
 * peut pas valider sa propre demande, même s'il en a le droit : renoncer à une créance est une
 * décision d'argent, elle se prend à deux. Tant qu'elle n'est pas validée, la tranche reste due et
 * continue d'être relancée.
 *
 * <p>Chaque étape est inscrite au journal d'audit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DebtWriteOffService {

    public static final String PROPOSE = "writeoff:propose";
    public static final String APPROVE = "writeoff:approve";

    private final DebtWriteOffRepository writeOffRepository;
    private final InstallmentRepository installmentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final SchoolYearGuard schoolYearGuard;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public List<WriteOffDto> list() {
        UUID scope = this.scope();
        UUID me = this.currentUserProvider.currentUser().getId();
        boolean canApprove = this.currentUserProvider.hasPermission(APPROVE);
        return this.writeOffRepository.findByEstablishment_IdOrderByProposedAtDesc(scope).stream()
                .map(writeOff -> this.toDto(writeOff, me, canApprove))
                .toList();
    }

    @Transactional
    public WriteOffDto propose(WriteOffForms.Proposal form) {
        this.currentUserProvider.assertPermission(PROPOSE);
        UUID scope = this.scope();
        InstallmentEntity installment = this.installmentRepository.findById(form.getInstallmentId())
                .filter(i -> scope.equals(i.getStudentFee().getStudent().getEstablishment().getId()))
                .orElseThrow(() -> new NotFoundException("Tranche introuvable."));
        if (installment.getStatus() != InstallmentStatus.PENDING) {
            throw new BadRequestException("Seule une tranche encore due peut être passée en perte.");
        }
        FeeEntity fee = installment.getStudentFee().getFee();
        this.schoolYearGuard.assertWritable(scope, Objects.isNull(fee) ? null : fee.getAcademicYear());
        if (this.writeOffRepository.existsByInstallment_IdAndStatus(installment.getId(), WriteOffStatus.PROPOSED)) {
            throw new BadRequestException("Une demande de passage en perte attend déjà la direction pour cette tranche.");
        }
        UserEntity me = this.currentUserProvider.currentUser();
        DebtWriteOffEntity writeOff = this.writeOffRepository.saveAndFlush(DebtWriteOffEntity.builder()
                .establishment(this.establishmentRepository.getReferenceById(scope))
                .installment(installment)
                .amount(installment.getAmount())
                .reason(form.getReason().trim())
                .status(WriteOffStatus.PROPOSED)
                .proposedBy(me)
                .proposedAt(Instant.now())
                .build());
        this.audit(scope, AuditAction.DEBT_WRITE_OFF_PROPOSED, writeOff, form.getReason().trim());
        return this.toDto(writeOff, me.getId(), this.currentUserProvider.hasPermission(APPROVE));
    }

    @Transactional
    public WriteOffDto approve(UUID id, WriteOffForms.Decision form) {
        DebtWriteOffEntity writeOff = this.loadForDecision(id);
        InstallmentEntity installment = writeOff.getInstallment();
        if (installment.getStatus() != InstallmentStatus.PENDING) {
            // Réglée entre la proposition et la décision : il n'y a plus rien à abandonner.
            this.close(writeOff, WriteOffStatus.REJECTED, "Tranche réglée ou annulée entre-temps.");
            throw new BadRequestException("Cette tranche n'est plus due : la demande a été close sans perte.");
        }
        installment.setStatus(InstallmentStatus.WRITTEN_OFF);
        this.installmentRepository.save(installment);
        this.close(writeOff, WriteOffStatus.APPROVED, Objects.isNull(form) ? null : form.getNote());
        this.audit(writeOff.getEstablishment().getId(), AuditAction.DEBT_WRITE_OFF_APPROVED, writeOff,
                StringUtils.defaultIfBlank(writeOff.getDecisionNote(), writeOff.getReason()));
        log.info("DEBT_WRITTEN_OFF: installment {} ({})", installment.getId(), XofFormat.format(writeOff.getAmount()));
        return this.toDto(writeOff, this.currentUserProvider.currentUser().getId(), true);
    }

    @Transactional
    public WriteOffDto reject(UUID id, WriteOffForms.Decision form) {
        if (Objects.isNull(form) || StringUtils.isBlank(form.getNote())) {
            throw new BadRequestException("Indiquez pourquoi la demande est refusée : la comptabilité en a besoin pour relancer.");
        }
        DebtWriteOffEntity writeOff = this.loadForDecision(id);
        this.close(writeOff, WriteOffStatus.REJECTED, form.getNote());
        this.audit(writeOff.getEstablishment().getId(), AuditAction.DEBT_WRITE_OFF_REJECTED, writeOff, form.getNote().trim());
        return this.toDto(writeOff, this.currentUserProvider.currentUser().getId(), true);
    }

    private DebtWriteOffEntity loadForDecision(UUID id) {
        this.currentUserProvider.assertPermission(APPROVE);
        UUID scope = this.scope();
        DebtWriteOffEntity writeOff = this.writeOffRepository.findById(id)
                .filter(w -> scope.equals(w.getEstablishment().getId()))
                .orElseThrow(() -> new NotFoundException("Demande introuvable."));
        if (writeOff.getStatus() != WriteOffStatus.PROPOSED) {
            throw new BadRequestException("Cette demande a déjà été tranchée.");
        }
        if (writeOff.getProposedBy().getId().equals(this.currentUserProvider.currentUser().getId())) {
            throw new BadRequestException("Une perte se valide à deux : une autre personne de la direction doit trancher votre demande.");
        }
        return writeOff;
    }

    private void close(DebtWriteOffEntity writeOff, WriteOffStatus status, String note) {
        writeOff.setStatus(status);
        writeOff.setDecidedBy(this.currentUserProvider.currentUser());
        writeOff.setDecidedAt(Instant.now());
        writeOff.setDecisionNote(StringUtils.isBlank(note) ? null : note.trim());
        this.writeOffRepository.saveAndFlush(writeOff);
    }

    private void audit(UUID scope, AuditAction action, DebtWriteOffEntity writeOff, String details) {
        StudentEntity student = writeOff.getInstallment().getStudentFee().getStudent();
        this.auditService.record(scope, action,
                String.format("%s — élève %s", labelOf(writeOff.getInstallment()), Objects.toString(student.getRegistrationNumber(), "—")),
                String.format("%s · %s", XofFormat.format(writeOff.getAmount()), details));
    }

    private WriteOffDto toDto(DebtWriteOffEntity writeOff, UUID me, boolean canApprove) {
        InstallmentEntity installment = writeOff.getInstallment();
        StudentEntity student = installment.getStudentFee().getStudent();
        return WriteOffDto.builder()
                .id(writeOff.getId().toString())
                .installmentId(installment.getId())
                .studentId(student.getId())
                .studentPhotoId(student.getPhotoDocumentId())
                .studentName(String.format("%s %s", student.getLastName(), student.getFirstName()))
                .registrationNumber(student.getRegistrationNumber())
                .installmentLabel(labelOf(installment))
                .dueDate(installment.getDueDate())
                .amount(writeOff.getAmount())
                .reason(writeOff.getReason())
                .status(writeOff.getStatus())
                .proposedBy(nameOf(writeOff.getProposedBy()))
                .proposedAt(writeOff.getProposedAt())
                .decidedBy(Objects.isNull(writeOff.getDecidedBy()) ? null : nameOf(writeOff.getDecidedBy()))
                .decidedAt(writeOff.getDecidedAt())
                .decisionNote(writeOff.getDecisionNote())
                .decidable(canApprove && writeOff.getStatus() == WriteOffStatus.PROPOSED
                        && !writeOff.getProposedBy().getId().equals(me))
                .build();
    }

    static String labelOf(InstallmentEntity installment) {
        FeeEntity fee = installment.getStudentFee().getFee();
        String feeName = Objects.isNull(fee) ? installment.getStudentFee().getName() : fee.getName();
        return StringUtils.isBlank(feeName) ? installment.getLabel()
                : String.format("%s — %s", feeName, Objects.toString(installment.getLabel(), ""));
    }

    private static String nameOf(UserEntity user) {
        return String.format("%s %s", Objects.toString(user.getFirstName(), ""), Objects.toString(user.getLastName(), "")).trim();
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
