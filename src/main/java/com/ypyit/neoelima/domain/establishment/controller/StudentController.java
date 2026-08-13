package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentFeeDto;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentStudentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.StudentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.StudentUpdateForm;
import com.ypyit.neoelima.domain.establishment.service.StudentFeeService;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.storage.form.StorageCreationForm;
import com.ypyit.neoelima.domain.utils.ControllerUtils;
import jakarta.validation.Valid;
import com.ypyit.neoelima.domain.establishment.service.StudentCsvImporter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.multipart.MultipartFile;
import com.ypyit.neoelima.domain.establishment.form.StudentClaimForm;
import com.ypyit.neoelima.domain.establishment.service.StudentClaimService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final StudentFeeService studentFeeService;
    private final StudentCsvImporter studentCsvImporter;
    private final StudentClaimService studentClaimService;

    // `@Valid` sur le `@ModelAttribute` : sans lui, les `@NotBlank`/`@NotNull` du formulaire ne
    // s'exécutaient pas. Un `GET /students` nu chargeait alors la table élève entière avant de rendre
    // un 500, là où un 400 dit ce qui manque — matricule, date de naissance ou établissement.
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentDto> findByEstablishment(@Valid @ModelAttribute @ParameterObject EstablishmentStudentSearchForm searchForm) {
        return ResponseEntity.ok(this.studentService.findByEstablishment(searchForm));
    }

    @GetMapping(value = "/{id}/fees", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<StudentFeeDto>> findByStudentId(@PathVariable("id") UUID id,
                                                               @RequestParam("academical") Boolean academical,
                                                               @PageableDefault @ParameterObject Pageable page) {
        return ResponseEntity.ok(this.studentFeeService.findByStudentId(id, academical, page));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('student:read')")
    public ResponseEntity<Page<StudentDto>> search(@ModelAttribute @ParameterObject StudentSearchForm searchForm,
                                                   @PageableDefault @ParameterObject Pageable page) {
        return ResponseEntity.ok(this.studentService.search(searchForm, page));
    }


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('student:write')")
    public ResponseEntity<StudentDto> create(@RequestBody @Valid final StudentCreationForm creationForm) {
        var response = this.studentService.create(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), StudentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    /**
     * Modifie la fiche d'un élève.
     *
     * <p>Le service existait et n'était <strong>exposé nulle part</strong> : une fiche d'élève ne
     * se corrigeait donc pas une fois créée — ni un prénom mal orthographié, ni un matricule, ni
     * le sexe que l'école n'avait pas renseigné à l'inscription. Le périmètre est vérifié par le
     * service ({@code assertCanAccessStudent}), qui refuse l'élève d'une autre école.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifie la fiche d'un élève",
            description = "Seuls les champs fournis sont écrits. Le matricule reste unique dans "
                    + "l'établissement.")
    @PreAuthorize("hasAuthority('student:write')")
    public ResponseEntity<StudentDto> update(@PathVariable UUID id,
                                             @RequestBody @Valid StudentUpdateForm updateForm) {
        return ResponseEntity.ok(this.studentService.update(id, updateForm));
    }

    @PostMapping(value = "/claim", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rattache un enfant au compte du parent authentifié",
            description = "La preuve est le triplet établissement + matricule + date de naissance, "
                    + "revérifié côté serveur : un identifiant d'élève seul ne suffit pas. "
                    + "Le rattachement fait du parent un destinataire des reçus et des rappels.")
    public ResponseEntity<StudentDto> claim(@RequestBody @Valid StudentClaimForm claimForm) {
        return ResponseEntity.ok(this.studentClaimService.claimView(claimForm));
    }

    @GetMapping(value = "/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Élèves rattachés au compte du parent authentifié")
    public ResponseEntity<List<StudentDto>> myChildren() {
        return ResponseEntity.ok(this.studentClaimService.myChildrenView());
    }

    /**
     * Le modèle de fichier à remplir.
     *
     * <p>Servi par le serveur et non écrit dans le portail : l'en-tête vient des mêmes constantes
     * que le contrôle à l'import, si bien qu'un modèle téléchargé chez nous ne peut pas être
     * refusé par nous.
     *
     * <p>La marque d'ordre d'octets ouvre le fichier : sans elle, Excel lit l'UTF-8 comme du
     * latin-1 et « prénom » arrive en « prÃ©nom ».
     */
    @GetMapping(value = "/import-csv/template", produces = "text/csv; charset=UTF-8")
    @Operation(summary = "Modèle de fichier d'import",
            description = "En-tête et une ligne d'exemple. La colonne « classe » est facultative.")
    @PreAuthorize("hasAuthority('student:write')")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] body = ("﻿" + StudentCsvImporter.template()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"nelima-modele-eleves.csv\"")
                .body(body);
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Importe une liste d'élèves au format CSV",
            description = "Colonnes attendues, séparées par des points-virgules : "
                    + "matricule;nom;prenom;date_naissance;lieu_naissance;niveau;classe — la "
                    + "dernière est facultative, et un fichier à six colonnes reste accepté. "
                    + "L'import est tout ou rien : à la moindre ligne invalide, rien n'est écrit "
                    + "et le message désigne les lignes fautives.")
    @PreAuthorize("hasAuthority('student:write')")
    public ResponseEntity<StudentCsvImporter.ImportReport> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(this.studentCsvImporter.importFrom(file));
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('student:write')")
    public ResponseEntity<List<StudentDto>> importFromFile(@RequestBody @Valid final StorageCreationForm creationForm) {
        var response = this.studentService.importFromFile(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(UUID.randomUUID(), StudentController.class);
        return ResponseEntity.created(uri).body(response);
    }
}
