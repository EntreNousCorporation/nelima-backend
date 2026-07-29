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
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Page<StudentDto>> search(@ModelAttribute @ParameterObject StudentSearchForm searchForm,
                                                   @PageableDefault @ParameterObject Pageable page) {
        return ResponseEntity.ok(this.studentService.search(searchForm, page));
    }


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentDto> create(@RequestBody @Valid final StudentCreationForm creationForm) {
        var response = this.studentService.create(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), StudentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Importe une liste d'élèves au format CSV",
            description = "Colonnes attendues, séparées par des points-virgules : "
                    + "matricule;nom;prenom;date_naissance;lieu_naissance;niveau. "
                    + "L'import est tout ou rien : à la moindre ligne invalide, rien n'est écrit "
                    + "et le message désigne les lignes fautives.")
    public ResponseEntity<StudentCsvImporter.ImportReport> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(this.studentCsvImporter.importFrom(file));
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StudentDto>> importFromFile(@RequestBody @Valid final StorageCreationForm creationForm) {
        var response = this.studentService.importFromFile(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(UUID.randomUUID(), StudentController.class);
        return ResponseEntity.created(uri).body(response);
    }
}
