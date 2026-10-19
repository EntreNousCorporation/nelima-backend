package com.ypyit.neoelima.domain.canteen.service;

import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.canteen.dto.CanteenDtos;
import com.ypyit.neoelima.domain.canteen.entity.StudentDietEntity;
import com.ypyit.neoelima.domain.canteen.form.CanteenForms;
import com.ypyit.neoelima.domain.canteen.repository.StudentDietRepository;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Régimes et allergies au dossier de l'élève : la cantine les lit, la fiche les tient. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentDietService {

    private final StudentDietRepository dietRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserProvider currentUserProvider;

    public List<CanteenDtos.Diet> diets(UUID studentId) {
        this.load(studentId);
        return this.dietRepository.findByStudent_Id(studentId).stream()
                .map(d -> CanteenDtos.Diet.builder().kind(d.getKind()).label(d.getLabel()).build()).toList();
    }

    /** Remplace la liste : ce que l'école coche est ce que la cuisine reçoit. */
    @Transactional
    public List<CanteenDtos.Diet> replace(UUID studentId, List<CanteenForms.Diet> diets) {
        StudentEntity student = this.load(studentId);
        this.dietRepository.deleteByStudent_Id(studentId);
        this.dietRepository.flush();
        Objects.requireNonNullElse(diets, List.<CanteenForms.Diet>of()).stream()
                .filter(d -> Objects.nonNull(d.getKind()))
                .map(CanteenForms.Diet::getKind).distinct()
                .forEach(kind -> {
                    String label = diets.stream().filter(d -> d.getKind() == kind).map(CanteenForms.Diet::getLabel)
                            .filter(StringUtils::isNotBlank).findFirst().map(String::trim).orElse(null);
                    this.dietRepository.save(StudentDietEntity.builder().student(student).kind(kind).label(label).build());
                });
        return this.diets(studentId);
    }

    private StudentEntity load(UUID studentId) {
        StudentEntity student = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found"));
        this.currentUserProvider.assertCanAccessStudent(student);
        return student;
    }
}
