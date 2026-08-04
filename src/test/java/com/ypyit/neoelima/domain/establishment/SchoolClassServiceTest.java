package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.SchoolClassDto;
import com.ypyit.neoelima.domain.establishment.dto.StaffDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.establishment.form.SchoolClassForm;
import com.ypyit.neoelima.domain.establishment.form.StaffForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.SchoolClassService;
import com.ypyit.neoelima.domain.establishment.service.StaffService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Classes de l'établissement.
 *
 * <p>Trois erreurs seraient coûteuses et silencieuses : voir les classes d'une autre école, y
 * affecter l'élève d'une autre école, et perdre l'affectation de trente élèves en supprimant leur
 * classe d'un clic.
 */
@Transactional
class SchoolClassServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SchoolClassService schoolClassService;
    @Autowired
    private StaffService staffService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private LevelOfStudyEntity level;
    private LevelOfStudyEntity otherLevel;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        // Le catalogue des niveaux n'est pas semé dans la base de test : on crée les deux dont
        // ces cas ont besoin, plutôt que de dépendre d'un jeu de données implicite.
        this.level = this.level("CM1-" + UUID.randomUUID().toString().substring(0, 6), "CM1");
        this.otherLevel = this.level("TLE-" + UUID.randomUUID().toString().substring(0, 6), "Terminale");
        this.school = this.schoolWithLevel();
        this.authenticateOn(this.school);
    }

    private LevelOfStudyEntity level(String code, String label) {
        return this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code(code).position(1)
                .name(TranslateEntity.builder().fr(label).en(label).build())
                .build());
    }

    @Test
    @DisplayName("une classe créée porte son niveau, sa salle et sa capacité")
    void createsAClass() {
        SchoolClassDto created = this.schoolClassService.create(this.form("CM1 A", 35));

        assertThat(created.getName()).isEqualTo("CM1 A");
        assertThat(created.getCapacity()).isEqualTo(35);
        assertThat(created.getLevelCode()).isEqualTo(this.level.getCode());
        assertThat(created.getStudentCount()).isZero();
    }

    @Test
    @DisplayName("un niveau non déclaré par l'établissement est refusé")
    void rejectsAnUndeclaredLevel() {
        // Sans ce contrôle, une école maternelle pourrait ouvrir une classe de Terminale.
        SchoolClassForm form = this.form("Classe fantôme", 30);
        form.setLevelOfStudyCode(this.otherLevel.getCode());

        assertThatThrownBy(() -> this.schoolClassService.create(form))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("l'affectation range l'élève et alimente l'effectif")
    void assignsStudents() {
        SchoolClassDto created = this.schoolClassService.create(this.form("CE2 B", 30));
        StudentEntity student = this.student(this.school);

        this.schoolClassService.assign(UUID.fromString(created.getId()), List.of(student.getId()));

        assertThat(this.schoolClassService.findById(UUID.fromString(created.getId()))
                .getStudentCount()).isEqualTo(1);
        assertThat(this.studentRepository.findById(student.getId()).orElseThrow()
                .getSchoolClass().getName()).isEqualTo("CE2 B");
    }

    @Test
    @DisplayName("l'élève d'une autre école ne peut pas être affecté")
    void refusesAStudentFromAnotherSchool() {
        SchoolClassDto created = this.schoolClassService.create(this.form("6e A", 45));
        StudentEntity stranger = this.student(this.schoolWithLevel());

        assertThatThrownBy(() -> this.schoolClassService
                .assign(UUID.fromString(created.getId()), List.of(stranger.getId())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("les classes d'une autre école sont invisibles")
    void doesNotLeakOtherSchools() {
        this.schoolClassService.create(this.form("CP1 A", 30));

        EstablishmentEntity other = this.schoolWithLevel();
        this.authenticateOn(other);

        assertThat(this.schoolClassService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("une classe occupée ne se supprime pas")
    void refusesToDeleteAnOccupiedClass() {
        SchoolClassDto created = this.schoolClassService.create(this.form("5e B", 40));
        UUID id = UUID.fromString(created.getId());
        this.schoolClassService.assign(id, List.of(this.student(this.school).getId()));

        // Supprimer en cascade laisserait des élèves sans classe sans que personne ne l'ait
        // demandé — une perte qu'on ne remarque qu'à la rentrée suivante.
        assertThatThrownBy(() -> this.schoolClassService.delete(id))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("la classe d'une autre école ne se lit pas par son identifiant")
    void doesNotExposeAnotherSchoolClassById() {
        SchoolClassDto created = this.schoolClassService.create(this.form("Tle D", 45));
        this.authenticateOn(this.schoolWithLevel());

        assertThatThrownBy(() -> this.schoolClassService.findById(UUID.fromString(created.getId())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("le titulaire désigné prime sur le nom saisi à la main")
    void namesTheDesignatedTeacherOverTheTypedOne() {
        StaffDto teacher = this.staff(this.school, "AHOU", "Bernadette");
        SchoolClassForm form = this.form("CM1 B", 35);
        form.setMainTeacherId(UUID.fromString(teacher.getId()));

        SchoolClassDto created = this.schoolClassService.create(form);

        // Le formulaire portait aussi « KOUAMÉ Adjoua » en nom libre : quand les deux existent,
        // c'est que l'école a désigné son titulaire après l'avoir tapé.
        assertThat(created.getMainTeacherId()).isEqualTo(teacher.getId());
        assertThat(created.getMainTeacherName()).isEqualTo("AHOU Bernadette");
    }

    @Test
    @DisplayName("sans titulaire désigné, le nom hérité continue de répondre")
    void fallsBackOnTheInheritedName() {
        SchoolClassDto created = this.schoolClassService.create(this.form("CM1 C", 35));

        assertThat(created.getMainTeacherId()).isNull();
        assertThat(created.getMainTeacherName()).isEqualTo("KOUAMÉ Adjoua");
    }

    @Test
    @DisplayName("l'enseignant d'une autre école ne peut pas être désigné titulaire")
    void refusesATeacherFromAnotherSchool() {
        EstablishmentEntity other = this.schoolWithLevel();
        this.authenticateOn(other);
        StaffDto stranger = this.staff(other, "TRAORÉ", "Fatou");
        this.authenticateOn(this.school);

        SchoolClassForm form = this.form("6e B", 45);
        form.setMainTeacherId(UUID.fromString(stranger.getId()));

        // Sans ce contrôle, une école lirait le nom de l'enseignant d'une autre sur sa propre
        // grille de classes.
        assertThatThrownBy(() -> this.schoolClassService.create(form))
                .isInstanceOf(BadRequestException.class);
    }

    private StaffDto staff(EstablishmentEntity establishment, String lastName, String firstName) {
        StaffForm form = new StaffForm();
        form.setFirstName(firstName);
        form.setLastName(lastName);
        form.setRole(StaffRole.TEACHER);
        return this.staffService.create(form);
    }

    private SchoolClassForm form(String name, int capacity) {
        SchoolClassForm form = new SchoolClassForm();
        form.setName(name);
        form.setCapacity(capacity);
        form.setRoom("B-04");
        form.setMainTeacherName("KOUAMÉ Adjoua");
        form.setLevelOfStudyCode(this.level.getCode());
        return form;
    }

    private EstablishmentEntity schoolWithLevel() {
        EstablishmentEntity establishment = this.establishmentRepository
                .saveAndFlush(EstablishmentEntity.builder()
                        .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        establishment.getLevelOfStudies().add(this.level);
        return this.establishmentRepository.saveAndFlush(establishment);
    }

    private StudentEntity student(EstablishmentEntity establishment) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName("Koné")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2014, 2, 9)).establishment(establishment).build());
    }

    private void authenticateOn(EstablishmentEntity establishment) {
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(establishment)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
