package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.AcademicYearDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicPeriodEntity;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.form.AcademicYearForm;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Année scolaire de l'établissement et ses périodes.
 *
 * <p>Une seule année est active à la fois, et c'est ici que la règle tient : la base ne l'exprime
 * pas — un index partiel unique interdirait de basculer d'une année à l'autre dans la même
 * transaction sans ordonner les écritures. Le service désactive les autres avant d'activer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CurrentUserProvider currentUserProvider;

    /** Les années de l'école, la plus récente en tête. */
    public List<AcademicYearDto> findAll() {
        return this.academicYearRepository
                .findByEstablishment_IdOrderByStartDateDesc(this.scope()).stream()
                .map(AcademicYearService::toDto)
                .toList();
    }

    /** L'année en cours, si l'école en a déclaré une. */
    public Optional<AcademicYearEntity> activeYear(UUID establishmentId) {
        return this.academicYearRepository.findByEstablishment_IdAndActiveTrue(establishmentId);
    }

    @Transactional
    public AcademicYearDto create(AcademicYearForm form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.findById(scope)
                .orElseThrow(() -> new NotFoundException("Establishment not found"));

        AcademicYearEntity entity = AcademicYearEntity.builder()
                .establishment(establishment)
                .label(form.getLabel().trim())
                .startDate(form.getStartDate())
                .endDate(form.getEndDate())
                .build();
        this.apply(entity, form);

        AcademicYearEntity saved = this.academicYearRepository.saveAndFlush(entity);
        log.info("ACADEMIC_YEAR_CREATED: {} in establishment {}", saved.getId(), scope);
        return toDto(saved);
    }

    @Transactional
    public AcademicYearDto update(UUID id, AcademicYearForm form) {
        AcademicYearEntity entity = this.load(id);
        entity.setLabel(form.getLabel().trim());
        entity.setStartDate(form.getStartDate());
        entity.setEndDate(form.getEndDate());
        this.apply(entity, form);
        return toDto(this.academicYearRepository.saveAndFlush(entity));
    }

    /**
     * Supprime une année.
     *
     * <p>L'année en cours ne se supprime pas. Elle borne le calendrier et sert de référence aux
     * écrans : la retirer laisserait l'école sans repère jusqu'à ce qu'elle en active une autre,
     * sans que rien ne le lui dise.
     */
    @Transactional
    public void delete(UUID id) {
        AcademicYearEntity entity = this.load(id);
        if (entity.isActive()) {
            throw new BadRequestException(
                    "L'année en cours ne peut pas être supprimée : activez-en une autre d'abord.");
        }
        this.academicYearRepository.delete(entity);
        log.info("ACADEMIC_YEAR_DELETED: {}", id);
    }

    private void apply(AcademicYearEntity entity, AcademicYearForm form) {
        assertRange(entity.getStartDate(), entity.getEndDate(),
                "L'année scolaire doit se terminer après avoir commencé.");

        entity.getPeriods().clear();
        int position = 1;
        for (AcademicYearForm.AcademicPeriodForm period : form.getPeriods()) {
            assertRange(period.getStartDate(), period.getEndDate(), String.format(
                    "La période « %s » doit se terminer après avoir commencé.", period.getLabel()));
            // Une période hors de son année placerait au calendrier un trimestre que l'année ne
            // couvre pas ; la faute vient d'une saisie, pas d'un cas limite à absorber.
            if (period.getStartDate().isBefore(entity.getStartDate())
                    || period.getEndDate().isAfter(entity.getEndDate())) {
                throw new BadRequestException(String.format(
                        "La période « %s » sort des bornes de l'année scolaire.", period.getLabel()));
            }
            entity.getPeriods().add(AcademicPeriodEntity.builder()
                    .academicYear(entity)
                    .label(period.getLabel().trim())
                    .startDate(period.getStartDate())
                    .endDate(period.getEndDate())
                    .position(position++)
                    .build());
        }

        entity.setActive(form.isActive());
        if (form.isActive()) {
            this.deactivateOthers(entity);
        }
    }

    /**
     * Désactive les autres années de l'école.
     *
     * <p>Deux années actives feraient répondre deux jeux de bornes à la même question ; l'écran en
     * afficherait une et le calendrier l'autre, sans que rien ne signale la contradiction.
     */
    private void deactivateOthers(AcademicYearEntity entity) {
        UUID establishmentId = entity.getEstablishment().getId();
        UUID keep = Objects.requireNonNullElse(entity.getId(), new UUID(0L, 0L));
        List<AcademicYearEntity> others = this.academicYearRepository
                .findByEstablishment_IdAndActiveTrueAndIdNot(establishmentId, keep);
        for (AcademicYearEntity other : others) {
            other.setActive(false);
        }
        this.academicYearRepository.saveAll(others);
    }

    private static void assertRange(LocalDate from, LocalDate to, String message) {
        if (Objects.isNull(from) || Objects.isNull(to) || !to.isAfter(from)) {
            throw new BadRequestException(message);
        }
    }

    static AcademicYearDto toDto(AcademicYearEntity entity) {
        return AcademicYearDto.builder()
                .id(entity.getId().toString())
                .label(entity.getLabel())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .active(entity.isActive())
                .periods(entity.getPeriods().stream()
                        .map(period -> AcademicYearDto.AcademicPeriodDto.builder()
                                .id(period.getId().toString())
                                .label(period.getLabel())
                                .startDate(period.getStartDate())
                                .endDate(period.getEndDate())
                                .position(period.getPosition())
                                .build())
                        .toList())
                .build();
    }

    private AcademicYearEntity load(UUID id) {
        AcademicYearEntity entity = this.academicYearRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Academic year with provided id %s not found", id)));
        UUID scope = this.scope();
        if (!entity.getEstablishment().getId().equals(scope)) {
            throw new NotFoundException(
                    String.format("Academic year with provided id %s not found", id));
        }
        return entity;
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
