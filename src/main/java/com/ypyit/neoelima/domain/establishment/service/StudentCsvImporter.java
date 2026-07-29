package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Import d'une liste d'élèves au format CSV.
 *
 * <p>Une école n'inscrit pas quatre cents élèves un par un à la rentrée. Le format retenu est le
 * CSV et non le tableur binaire : c'est ce que produit n'importe quel outil, y compris un export
 * depuis un logiciel existant, et il reste lisible en cas de litige sur une ligne.
 *
 * <p>L'import est <strong>tout ou rien</strong>. Une reprise partielle laisserait l'école dans un
 * état qu'elle ne peut pas raisonner : elle ne saurait pas quelles lignes rejouer, et un second
 * passage buterait sur les matricules déjà créés. En cas d'erreur, rien n'est écrit et le rapport
 * désigne les lignes fautives par leur numéro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCsvImporter {

    private static final String SEPARATOR = ";";
    private static final int MAX_ROWS = 2000;
    private static final List<String> HEADERS =
            List.of("matricule", "nom", "prenom", "date_naissance", "lieu_naissance", "niveau");

    private final StudentRepository studentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CurrentUserProvider currentUserProvider;

    @Getter
    public static class ImportReport {
        private final int imported;
        private final List<String> errors;

        public ImportReport(int imported, List<String> errors) {
            this.imported = imported;
            this.errors = errors;
        }
    }

    @Transactional
    public ImportReport importFrom(MultipartFile file) {
        if (Objects.isNull(file) || file.isEmpty()) {
            throw new BadRequestException("Le fichier est vide");
        }
        UUID establishmentId = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(establishmentId)) {
            throw new AccessDeniedException("An explicit establishment is required to import students");
        }
        EstablishmentEntity establishment = this.establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new BadRequestException("Établissement introuvable"));

        Map<String, LevelOfStudyEntity> levelsByCode = new LinkedHashMap<>();
        establishment.getLevelOfStudies().forEach(level -> levelsByCode.put(level.getCode(), level));
        if (levelsByCode.isEmpty()) {
            throw new BadRequestException(
                    "Aucun niveau n'est déclaré pour l'établissement : renseignez-les avant d'importer");
        }

        List<String> errors = new ArrayList<>();
        List<StudentEntity> students = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (Objects.isNull(headerLine)) {
                throw new BadRequestException("Le fichier ne contient aucune ligne");
            }
            this.assertHeader(headerLine);

            String line;
            int lineNumber = 1;
            while (Objects.nonNull(line = reader.readLine())) {
                lineNumber++;
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                if (students.size() >= MAX_ROWS) {
                    throw new BadRequestException(String.format(
                            "Le fichier dépasse %d élèves : découpez-le en plusieurs imports", MAX_ROWS));
                }
                this.parseRow(line, lineNumber, establishment, levelsByCode, students, errors);
            }
        } catch (IOException e) {
            throw new BadRequestException("Le fichier n'a pas pu être lu", e);
        }

        if (!errors.isEmpty()) {
            // L'exception fait retomber la transaction : aucune ligne n'est conservée.
            throw new BadRequestException(String.join(" | ", errors));
        }
        if (students.isEmpty()) {
            throw new BadRequestException("Le fichier ne contient aucun élève");
        }

        this.studentRepository.saveAllAndFlush(students);
        log.info("STUDENT_CSV_IMPORT: {} élèves importés dans l'établissement {}",
                students.size(), establishmentId);
        return new ImportReport(students.size(), List.of());
    }

    private void assertHeader(String headerLine) {
        List<String> actual = new ArrayList<>();
        for (String column : headerLine.split(SEPARATOR, -1)) {
            actual.add(column.trim().toLowerCase().replace("﻿", ""));
        }
        if (!actual.equals(HEADERS)) {
            throw new BadRequestException(String.format(
                    "En-tête attendu : %s", String.join(SEPARATOR, HEADERS)));
        }
    }

    private void parseRow(String line, int lineNumber, EstablishmentEntity establishment,
                          Map<String, LevelOfStudyEntity> levelsByCode,
                          List<StudentEntity> students, List<String> errors) {
        String[] cells = line.split(SEPARATOR, -1);
        if (cells.length < HEADERS.size()) {
            errors.add(String.format("ligne %d : %d colonnes au lieu de %d",
                    lineNumber, cells.length, HEADERS.size()));
            return;
        }

        String registrationNumber = cells[0].trim();
        String lastName = cells[1].trim();
        String firstName = cells[2].trim();
        String birthDayRaw = cells[3].trim();
        String placeOfBirth = cells[4].trim();
        String levelCode = cells[5].trim();

        if (StringUtils.isAnyBlank(registrationNumber, lastName, firstName, birthDayRaw, levelCode)) {
            errors.add(String.format("ligne %d : matricule, nom, prénom, date de naissance et niveau sont obligatoires",
                    lineNumber));
            return;
        }

        LocalDate birthDay;
        try {
            birthDay = LocalDate.parse(birthDayRaw);
        } catch (DateTimeParseException e) {
            errors.add(String.format("ligne %d : date de naissance « %s » attendue au format AAAA-MM-JJ",
                    lineNumber, birthDayRaw));
            return;
        }

        LevelOfStudyEntity level = levelsByCode.get(levelCode);
        if (Objects.isNull(level)) {
            errors.add(String.format("ligne %d : niveau « %s » non enseigné par l'établissement",
                    lineNumber, levelCode));
            return;
        }

        // Doublon dans la base comme à l'intérieur du fichier : les deux produiraient une
        // violation de contrainte au flush, avec un message illisible pour l'utilisateur.
        if (this.studentRepository.existsByEstablishment_IdAndRegistrationNumber(
                establishment.getId(), registrationNumber)) {
            errors.add(String.format("ligne %d : le matricule %s existe déjà", lineNumber, registrationNumber));
            return;
        }
        boolean duplicatedInFile = students.stream()
                .anyMatch(s -> registrationNumber.equals(s.getRegistrationNumber()));
        if (duplicatedInFile) {
            errors.add(String.format("ligne %d : le matricule %s apparaît deux fois dans le fichier",
                    lineNumber, registrationNumber));
            return;
        }

        students.add(StudentEntity.builder()
                .registrationNumber(registrationNumber)
                .lastName(lastName)
                .firstName(firstName)
                .birthDay(birthDay)
                .placeOfBirth(placeOfBirth)
                .levelOfStudy(level)
                .establishment(establishment)
                .build());
    }
}
