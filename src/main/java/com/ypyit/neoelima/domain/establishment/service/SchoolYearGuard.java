package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolYearEntity;
import com.ypyit.neoelima.domain.establishment.repository.SchoolYearRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Une année clôturée ne se modifie plus.
 *
 * <p>Deux portes, la même règle. L'intercepteur ferme toute écriture à qui consulte une année
 * clôturée ; ce garde-ci ferme, depuis n'importe quelle année, l'écriture sur une donnée qui
 * appartient à une année clôturée — un frais de l'an passé ne doit pas se rouvrir par l'API parce
 * que l'utilisateur, lui, regarde l'année en cours.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolYearGuard {

    private final SchoolYearRepository schoolYearRepository;

    public boolean isClosed(UUID establishmentId, UUID academicYearId) {
        if (Objects.isNull(establishmentId) || Objects.isNull(academicYearId)) {
            return false;
        }
        return this.schoolYearRepository.findByEstablishment_IdAndAcademicYear_Id(establishmentId, academicYearId)
                .map(SchoolYearEntity::isClosed).orElse(false);
    }

    /** Refuse l'écriture si cette année est clôturée dans cette école. Sans année, rien à garder. */
    public void assertWritable(UUID establishmentId, AcademicYearEntity year) {
        if (Objects.nonNull(year) && this.isClosed(establishmentId, year.getId())) {
            throw new BadRequestException(closedMessage(year.getLabel()));
        }
    }

    public static String closedMessage(String label) {
        return String.format("L'année %s est clôturée : ses données se consultent, elles ne se modifient plus.", label);
    }
}
