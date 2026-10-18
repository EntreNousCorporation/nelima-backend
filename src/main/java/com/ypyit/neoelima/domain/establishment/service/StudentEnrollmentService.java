package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Tient l'inscription de l'année en cours d'accord avec l'élève.
 *
 * <p>Appelé partout où l'élève naît ou change de classe ou de niveau : inscription, import,
 * affectation, modification. Une école sans année déclarée n'a pas d'inscription à tenir — l'élève
 * existe quand même, il sera inscrit quand l'année le sera.
 */
@Service
@RequiredArgsConstructor
public class StudentEnrollmentService {

    private final AcademicYearContext academicYearContext;
    private final StudentEnrollmentRepository studentEnrollmentRepository;

    @Transactional
    public void syncActiveYear(StudentEntity student) {
        if (Objects.isNull(student.getEstablishment())) {
            return;
        }
        // L'année de travail de l'utilisateur : celle qu'il consulte. Préparer la rentrée suivante
        // depuis 2027-2028 y inscrit l'élève, sans toucher à 2026-2027.
        Optional<AcademicYearEntity> active = this.academicYearContext.consulted();
        if (active.isEmpty()) {
            return;
        }
        this.academicYearContext.schoolYear(student.getEstablishment().getId(), active.get());
        StudentEnrollmentEntity enrollment = this.studentEnrollmentRepository
                .findByStudent_IdAndAcademicYear_Id(student.getId(), active.get().getId())
                .orElseGet(() -> StudentEnrollmentEntity.builder()
                        .student(student).academicYear(active.get()).build());
        enrollment.setSchoolClass(student.getSchoolClass());
        enrollment.setLevelOfStudy(student.getLevelOfStudy());
        this.studentEnrollmentRepository.save(enrollment);
    }
}
