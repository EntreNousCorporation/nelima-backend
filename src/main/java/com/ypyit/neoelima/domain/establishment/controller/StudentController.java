package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentFeeDto;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentStudentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.StudentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
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
import com.ypyit.neoelima.domain.establishment.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
    private final StudentMapper studentMapper;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentDto> findByEstablishment(@ModelAttribute @ParameterObject EstablishmentStudentSearchForm searchForm) {
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

    @PostMapping(value = "/claim", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rattache un enfant au compte du parent authentifié",
            description = "La preuve est le triplet établissement + matricule + date de naissance, "
                    + "revérifié côté serveur : un identifiant d'élève seul ne suffit pas. "
                    + "Le rattachement fait du parent un destinataire des reçus et des rappels.")
    public ResponseEntity<StudentDto> claim(@RequestBody @Valid StudentClaimForm claimForm) {
        return ResponseEntity.ok(this.studentMapper.toDto(this.studentClaimService.claim(claimForm)));
    }

    @GetMapping(value = "/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Élèves rattachés au compte du parent authentifié")
    public ResponseEntity<List<StudentDto>> myChildren() {
        return ResponseEntity.ok(this.studentMapper.toDtos(this.studentClaimService.myChildren()));
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Importe une liste d'élèves au format CSV",
            description = "Colonnes attendues, séparées par des points-virgules : "
                    + "matricule;nom;prenom;date_naissance;lieu_naissance;niveau. "
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
