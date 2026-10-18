package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.StudentDocumentDto;
import com.ypyit.neoelima.domain.establishment.entity.StudentDocumentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.StudentDocumentKind;
import com.ypyit.neoelima.domain.establishment.repository.StudentDocumentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.storage.service.PrivateFileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Dossier de l'élève : les pièces justificatives que l'école conserve.
 *
 * <p>Ce sont des données personnelles de mineurs. D'où trois partis pris : le fichier ne vit que
 * dans un stockage privé et ne sort que par ici, après contrôle de l'école ; le type est établi
 * d'après le contenu et non d'après ce que le navigateur annonce ; seuls le PDF et les images
 * courantes sont acceptés, rien qui puisse s'exécuter à l'ouverture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentDocumentService {

    /** Une photo de téléphone tient en 3 à 4 Mo ; au-delà, c'est rarement une pièce justificative. */
    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private final StudentDocumentRepository studentDocumentRepository;
    private final StudentRepository studentRepository;
    private final PrivateFileStore privateFileStore;
    private final CurrentUserProvider currentUserProvider;

    public List<StudentDocumentDto> findByStudent(UUID studentId) {
        this.loadStudent(studentId);
        return this.studentDocumentRepository.findByStudent_IdOrderByCreatedAtDesc(studentId)
                .stream().map(StudentDocumentService::toDto).toList();
    }

    @Transactional
    public StudentDocumentDto upload(UUID studentId, StudentDocumentKind kind, String label, MultipartFile file) {
        StudentEntity student = this.loadStudent(studentId);
        if (Objects.isNull(kind)) {
            throw new BadRequestException("Précisez la nature de la pièce.");
        }
        if (kind == StudentDocumentKind.OTHER && StringUtils.isBlank(label)) {
            throw new BadRequestException("Précisez de quelle pièce il s'agit.");
        }
        if (Objects.isNull(file) || file.isEmpty()) {
            throw new BadRequestException("Le fichier est vide.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("Le fichier dépasse 5 Mo.");
        }
        byte[] content = readBytes(file);
        DetectedType type = DetectedType.of(content);
        if (Objects.isNull(type)) {
            throw new BadRequestException("Seuls les PDF et les images JPEG, PNG ou WebP sont acceptés.");
        }
        if (kind == StudentDocumentKind.PHOTO && !type.image) {
            throw new BadRequestException("La photo doit être une image JPEG, PNG ou WebP.");
        }

        // L'école en tête du chemin : un bucket partagé reste ainsi rangé, et purgeable, par école.
        String objectKey = String.format("%s/%s/%s.%s",
                student.getEstablishment().getId(), student.getId(), UUID.randomUUID(), type.extension);
        this.privateFileStore.put(objectKey, content, type.contentType);
        try {
            StudentDocumentEntity saved = this.studentDocumentRepository.saveAndFlush(
                    StudentDocumentEntity.builder()
                            .student(student)
                            .kind(kind)
                            .label(StringUtils.isBlank(label) ? null : StringUtils.left(label.trim(), 128))
                            .fileName(fileNameOf(file, type))
                            .contentType(type.contentType)
                            .sizeBytes(content.length)
                            .objectKey(objectKey)
                            .build());
            return toDto(saved);
        } catch (RuntimeException e) {
            // Sans ligne en base, l'objet ne serait plus jamais ni listé ni supprimé.
            this.privateFileStore.deleteQuietly(objectKey);
            throw e;
        }
    }

    public StoredFile download(UUID studentId, UUID documentId) {
        StudentDocumentEntity document = this.loadDocument(studentId, documentId);
        return new StoredFile(document.getFileName(), document.getContentType(),
                this.privateFileStore.get(document.getObjectKey()));
    }

    @Transactional
    public void delete(UUID studentId, UUID documentId) {
        StudentDocumentEntity document = this.loadDocument(studentId, documentId);
        this.studentDocumentRepository.delete(document);
        this.studentDocumentRepository.flush();
        this.privateFileStore.deleteQuietly(document.getObjectKey());
    }

    private StudentEntity loadStudent(UUID studentId) {
        StudentEntity student = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Student with provided id %s not found", studentId)));
        this.currentUserProvider.assertCanAccessStudent(student);
        return student;
    }

    private StudentDocumentEntity loadDocument(UUID studentId, UUID documentId) {
        this.loadStudent(studentId);
        return this.studentDocumentRepository.findById(documentId)
                .filter(found -> found.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new NotFoundException(
                        String.format("Document with provided id %s not found", documentId)));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Le fichier n'a pas pu être lu.");
        }
    }

    /** Nom d'origine, débarrassé de tout chemin, et dont l'extension dit le vrai type. */
    private static String fileNameOf(MultipartFile file, DetectedType type) {
        String original = StringUtils.defaultIfBlank(file.getOriginalFilename(), "piece");
        String base = original.replaceAll("^.*[\\\\/]", "").replaceAll("\\.[^.]*$", "")
                .replaceAll("[\\p{Cntrl}\"]", "");
        return StringUtils.left(StringUtils.defaultIfBlank(base, "piece"), 200) + "." + type.extension;
    }

    private static StudentDocumentDto toDto(StudentDocumentEntity entity) {
        return StudentDocumentDto.builder()
                .id(entity.getId().toString())
                .kind(entity.getKind())
                .label(entity.getLabel())
                .fileName(entity.getFileName())
                .contentType(entity.getContentType())
                .sizeBytes(entity.getSizeBytes())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }

    public record StoredFile(String fileName, String contentType, byte[] content) {
    }

    /** Type reconnu à la signature du fichier, la seule information qu'un client ne choisit pas. */
    private enum DetectedType {
        PDF("application/pdf", "pdf", false),
        JPEG("image/jpeg", "jpg", true),
        PNG("image/png", "png", true),
        WEBP("image/webp", "webp", true);

        private final String contentType;
        private final String extension;
        private final boolean image;

        DetectedType(String contentType, String extension, boolean image) {
            this.contentType = contentType;
            this.extension = extension;
            this.image = image;
        }

        static DetectedType of(byte[] c) {
            if (startsWith(c, 0, 0x25, 0x50, 0x44, 0x46, 0x2D)) return PDF;          // %PDF-
            if (startsWith(c, 0, 0xFF, 0xD8, 0xFF)) return JPEG;
            if (startsWith(c, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return PNG;
            if (startsWith(c, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(c, 8, 0x57, 0x45, 0x42, 0x50)) return WEBP;
            return null;
        }

        private static boolean startsWith(byte[] content, int offset, int... signature) {
            if (content.length < offset + signature.length) return false;
            for (int i = 0; i < signature.length; i++) {
                if ((content[offset + i] & 0xFF) != signature[i]) return false;
            }
            return true;
        }
    }
}
