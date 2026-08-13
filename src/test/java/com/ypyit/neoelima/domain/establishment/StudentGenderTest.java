package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.Gender;
import com.ypyit.neoelima.domain.establishment.form.StudentUpdateForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentCsvImporter;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
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
 * Le sexe de l'élève, demandé pour la répartition filles/garçons.
 *
 * <p>Il est <strong>facultatif</strong> : des élèves étaient déjà inscrits quand la colonne est
 * apparue, et une donnée d'état civil devinée vaut moins que son absence. Trois chemins le
 * renseignent — l'import, la fiche, et rien du tout.
 */
@Transactional
class StudentGenderTest extends AbstractIntegrationTest {

    @Autowired
    private StudentCsvImporter importer;
    @Autowired
    private StudentService studentService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private String levelCode;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        levelCode = "CP1-" + UUID.randomUUID().toString().substring(0, 6);
        LevelOfStudyEntity level = this.levelOfStudyRepository.saveAndFlush(
                LevelOfStudyEntity.builder().code(levelCode).position(1).build());

        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École sexe " + UUID.randomUUID()).active(true).build());
        this.school.getLevelOfStudies().add(level);
        this.school = this.establishmentRepository.saveAndFlush(this.school);

        String email = "secretaire-" + UUID.randomUUID() + "@nelima.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(this.school)
                .contacts(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build()))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    @Test
    @DisplayName("l'import accepte l'initiale comme le mot entier, et tolère la cellule vide")
    void importsGenderInEveryReasonableSpelling() {
        // Aucun tableur ne s'accorde sur la graphie ; refuser « Fille » ferait retomber tout
        // l'import, alors que l'intention est limpide.
        var report = this.importer.importFrom(csv(
                "G-001;Koffi;Aaron;2012-04-03;Abidjan;" + levelCode + ";M;\n"
                        + "G-002;Diallo;Fatou;2011-09-21;Bouaké;" + levelCode + ";Fille;\n"
                        + "G-003;Kone;Sekou;2013-01-05;Daloa;" + levelCode + ";;\n"));

        assertThat(report.getImported()).isEqualTo(3);
        assertThat(genderOf("G-001")).isEqualTo(Gender.MALE);
        assertThat(genderOf("G-002")).isEqualTo(Gender.FEMALE);
        assertThat(genderOf("G-003"))
                .as("une cellule vide laisse l'information à renseigner, elle n'invente rien")
                .isNull();
    }

    @Test
    @DisplayName("une valeur incompréhensible est refusée avec son numéro de ligne")
    void rejectsUnreadableGender() {
        assertThatThrownBy(() -> this.importer.importFrom(csv(
                "G-001;Koffi;Aaron;2012-04-03;Abidjan;" + levelCode + ";autre;\n")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ligne 2")
                .hasMessageContaining("autre");
    }

    @Test
    @DisplayName("la fiche d'un élève déjà inscrit peut être complétée")
    void completesAnAlreadyEnrolledStudent() {
        // Le cas réel : vingt-et-un élèves inscrits avant que la colonne n'existe. Sans route de
        // modification — elle n'était exposée nulle part —, il aurait fallu les recréer.
        this.importer.importFrom(csv(
                "G-010;Yao;Mariam;2012-04-03;Abidjan;" + levelCode + ";;\n"));
        StudentEntity student = student("G-010");
        assertThat(student.getGender()).isNull();

        this.studentService.update(student.getId(),
                StudentUpdateForm.builder().gender(Gender.FEMALE).build());

        assertThat(genderOf("G-010")).isEqualTo(Gender.FEMALE);
        assertThat(student("G-010").getFirstName())
                .as("un champ absent du formulaire ne doit pas effacer ce qui est déjà écrit")
                .isEqualTo("Mariam");
    }

    private Gender genderOf(String registrationNumber) {
        return student(registrationNumber).getGender();
    }

    private StudentEntity student(String registrationNumber) {
        return this.studentRepository.findAllByEstablishment_Id(this.school.getId(), Pageable.unpaged())
                .getContent().stream()
                .filter(candidate -> registrationNumber.equals(candidate.getRegistrationNumber()))
                .findFirst().orElseThrow();
    }

    private MockMultipartFile csv(String rows) {
        String header = String.join(";", StudentCsvImporter.TEMPLATE_HEADERS) + "\n";
        return new MockMultipartFile("file", "eleves.csv", "text/csv",
                (header + rows).getBytes(StandardCharsets.UTF_8));
    }
}
