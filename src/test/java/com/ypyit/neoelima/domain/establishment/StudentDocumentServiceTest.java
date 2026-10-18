package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.StudentDocumentDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.StudentDocumentKind;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentDocumentService;
import com.ypyit.neoelima.domain.storage.service.PrivateFileStore;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dossier de l'élève.
 *
 * <p>Ce sont des pièces d'identité d'enfants : une autre école ne doit ni les lister ni les lire,
 * et un fichier qui se fait passer pour une image ne doit pas entrer.
 */
@Transactional
class StudentDocumentServiceTest extends AbstractIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n1 0 obj".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @MockitoBean
    private PrivateFileStore privateFileStore;
    @Autowired
    private StudentDocumentService studentDocumentService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;

    private StudentEntity student;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        EstablishmentEntity school = this.school();
        this.authenticateOn(school);
        this.student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Fatoumata").lastName("TRAORE")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2014, 7, 18)).establishment(school).build());
    }

    @Test
    @DisplayName("une pièce envoyée est rangée sous l'école et l'élève, puis listée")
    void storesAndListsADocument() {
        StudentDocumentDto saved = this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.BIRTH_CERTIFICATE, null, this.file("extrait.pdf", PDF));

        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getFileName()).isEqualTo("extrait.pdf");
        verify(this.privateFileStore).put(
                startsWith(this.student.getEstablishment().getId() + "/" + this.student.getId() + "/"),
                eq(PDF), eq("application/pdf"));
        assertThat(this.studentDocumentService.findByStudent(this.student.getId()))
                .extracting(StudentDocumentDto::getKind)
                .containsExactly(StudentDocumentKind.BIRTH_CERTIFICATE);
    }

    @Test
    @DisplayName("le type se lit dans le contenu : un exécutable renommé en .png est refusé")
    void rejectsAFileWhoseContentIsNotAllowed() {
        byte[] executable = {0x4D, 0x5A, (byte) 0x90, 0, 3, 0, 0, 0};

        assertThatThrownBy(() -> this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.PHOTO, null, this.file("photo.png", executable)))
                .isInstanceOf(BadRequestException.class);
        verify(this.privateFileStore, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("l'extension enregistrée suit le contenu, pas le nom d'origine")
    void namesTheFileAfterItsRealType() {
        StudentDocumentDto saved = this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.PHOTO, null, this.file("C:\\Users\\ecole\\photo.pdf", PNG));

        assertThat(saved.getFileName()).isEqualTo("photo.png");
        assertThat(saved.getContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("une photo doit être une image")
    void refusesAPdfAsPhoto() {
        assertThatThrownBy(() -> this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.PHOTO, null, this.file("photo.pdf", PDF)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("une pièce « Autre » doit dire ce qu'elle est")
    void requiresALabelForOther() {
        assertThatThrownBy(() -> this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.OTHER, " ", this.file("x.pdf", PDF)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("une autre école ne peut ni lister ni lire le dossier")
    void doesNotLeakToAnotherSchool() {
        StudentDocumentDto saved = this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.BIRTH_CERTIFICATE, null, this.file("extrait.pdf", PDF));

        this.authenticateOn(this.school());

        assertThatThrownBy(() -> this.studentDocumentService.findByStudent(this.student.getId()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> this.studentDocumentService
                .download(this.student.getId(), UUID.fromString(saved.getId())))
                .isInstanceOf(RuntimeException.class);
        verify(this.privateFileStore, never()).get(any());
    }

    @Test
    @DisplayName("supprimer une pièce retire aussi le fichier")
    void deletesTheStoredFile() {
        StudentDocumentDto saved = this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.BIRTH_CERTIFICATE, null, this.file("extrait.pdf", PDF));

        this.studentDocumentService.delete(this.student.getId(), UUID.fromString(saved.getId()));

        assertThat(this.studentDocumentService.findByStudent(this.student.getId())).isEmpty();
        verify(this.privateFileStore).deleteQuietly(startsWith(this.student.getEstablishment().getId().toString()));
    }

    @Test
    @DisplayName("la lecture rend le contenu stocké et le nom d'origine")
    void downloadsTheContent() {
        StudentDocumentDto saved = this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.BIRTH_CERTIFICATE, null, this.file("extrait.pdf", PDF));
        when(this.privateFileStore.get(any())).thenReturn(PDF);

        StudentDocumentService.StoredFile file = this.studentDocumentService
                .download(this.student.getId(), UUID.fromString(saved.getId()));

        assertThat(file.content()).isEqualTo(PDF);
        assertThat(file.fileName()).isEqualTo("extrait.pdf");
    }

    @Test
    @DisplayName("la dernière photo versée devient la photo de l'élève")
    void lastPhotoBecomesTheStudentPhoto() {
        this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.PHOTO, null, this.file("2025.png", PNG));
        StudentDocumentDto latest = this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.PHOTO, null, this.file("2026.png", PNG));
        this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.BIRTH_CERTIFICATE, null, this.file("extrait.pdf", PDF));

        assertThat(this.photoOfStudent()).isEqualTo(UUID.fromString(latest.getId()));
    }

    @Test
    @DisplayName("supprimer la photo courante rend sa place à la précédente")
    void deletingThePhotoFallsBackToThePreviousOne() {
        StudentDocumentDto previous = this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.PHOTO, null, this.file("bonne.png", PNG));
        StudentDocumentDto failed = this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.PHOTO, null, this.file("floue.png", PNG));

        this.studentDocumentService.delete(this.student.getId(), UUID.fromString(failed.getId()));
        assertThat(this.photoOfStudent()).isEqualTo(UUID.fromString(previous.getId()));

        this.studentDocumentService.delete(this.student.getId(), UUID.fromString(previous.getId()));
        assertThat(this.photoOfStudent()).isNull();
    }

    @Test
    @DisplayName("la photo d'un élève d'une autre école ne se lit pas")
    void photoDoesNotLeakToAnotherSchool() {
        this.studentDocumentService.upload(this.student.getId(),
                StudentDocumentKind.PHOTO, null, this.file("photo.png", PNG));

        this.authenticateOn(this.school());

        assertThatThrownBy(() -> this.studentDocumentService.photo(this.student.getId()))
                .isInstanceOf(RuntimeException.class);
        verify(this.privateFileStore, never()).get(any());
    }

    private UUID photoOfStudent() {
        return this.studentRepository.findById(this.student.getId()).orElseThrow().getPhotoDocumentId();
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
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
