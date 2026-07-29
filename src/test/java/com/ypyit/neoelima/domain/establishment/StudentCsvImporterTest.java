package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentCsvImporter;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'import est tout ou rien. Une reprise partielle laisserait l'école incapable de savoir quelles
 * lignes rejouer, et un second passage buterait sur les matricules déjà créés.
 */
@Transactional
class StudentCsvImporterTest extends AbstractIntegrationTest {

    private static final String HEADER = "matricule;nom;prenom;date_naissance;lieu_naissance;niveau\n";

    @Autowired
    private StudentCsvImporter importer;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private String levelCode;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        levelCode = "CE2-" + UUID.randomUUID().toString().substring(0, 6);
        LevelOfStudyEntity level = levelOfStudyRepository.saveAndFlush(
                LevelOfStudyEntity.builder().code(levelCode).position(1).build());

        school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École import " + UUID.randomUUID()).active(true).build());
        school.getLevelOfStudies().add(level);
        school = establishmentRepository.saveAndFlush(school);

        String email = "secretaire-" + UUID.randomUUID() + "@nelima.ci";
        userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build()))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    @Test
    @DisplayName("un fichier valide crée tous les élèves")
    void importsEveryRow() {
        var report = importer.importFrom(csv(
                "2022001;Koffi;Aaron;2012-04-03;Abidjan;" + levelCode + "\n"
                        + "2022002;Diallo;Fatou;2011-09-21;Bouaké;" + levelCode + "\n"));

        assertThat(report.getImported()).isEqualTo(2);
        assertThat(studentRepository.findAllByEstablishment_Id(school.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("une seule ligne invalide annule tout l'import")
    void rejectsWholeFileOnSingleInvalidRow() {
        assertThatThrownBy(() -> importer.importFrom(csv(
                "2022001;Koffi;Aaron;2012-04-03;Abidjan;" + levelCode + "\n"
                        + "2022002;Diallo;Fatou;21/09/2011;Bouaké;" + levelCode + "\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ligne 3");

        assertThat(studentRepository.findAllByEstablishment_Id(school.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .as("la ligne valide ne doit pas non plus être conservée")
                .isZero();
    }

    @Test
    @DisplayName("un niveau non enseigné est refusé, avec le numéro de ligne")
    void rejectsUnknownLevel() {
        assertThatThrownBy(() -> importer.importFrom(csv(
                "2022001;Koffi;Aaron;2012-04-03;Abidjan;TERMINALE\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ligne 2")
                .hasMessageContaining("TERMINALE");
    }

    @Test
    @DisplayName("un matricule répété dans le fichier est détecté avant l'écriture")
    void rejectsDuplicateWithinFile() {
        assertThatThrownBy(() -> importer.importFrom(csv(
                "2022001;Koffi;Aaron;2012-04-03;Abidjan;" + levelCode + "\n"
                        + "2022001;Diallo;Fatou;2011-09-21;Bouaké;" + levelCode + "\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deux fois");
    }

    @Test
    @DisplayName("un en-tête incorrect est signalé explicitement")
    void rejectsWrongHeader() {
        assertThatThrownBy(() -> importer.importFrom(new MockMultipartFile(
                "file", "eleves.csv", "text/csv",
                "nom;prenom\nKoffi;Aaron\n".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("En-tête attendu");
    }

    private MockMultipartFile csv(String rows) {
        return new MockMultipartFile("file", "eleves.csv", "text/csv",
                (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }
}
