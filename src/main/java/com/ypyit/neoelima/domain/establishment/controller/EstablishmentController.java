package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.EstablishmentDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentLiteDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentLiteDto;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentLevelOfStudyCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentUpdateForm;
import com.ypyit.neoelima.domain.establishment.service.EstablishmentService;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.form.EstablishmentUserSignupForm;
import com.ypyit.neoelima.domain.user.form.UserSignupForm;
import com.ypyit.neoelima.domain.user.service.UserService;
import com.ypyit.neoelima.domain.utils.ControllerUtils;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/establishments")
@RequiredArgsConstructor
public class EstablishmentController {

    private final EstablishmentService establishmentService;

    private final UserService userService;

    private final StudentService studentService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> createRootUser(@RequestBody @Valid UserSignupForm creationForm) {
        var response = this.userService.createPartnerRootUser(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), EstablishmentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping(value = "/{id}/users", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> createUsers(@RequestBody @Valid EstablishmentUserSignupForm creationForm, @PathVariable UUID id) {
        var response = this.userService.createPartnerUser(id, creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), EstablishmentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<EstablishmentLiteDto>> findAll(@PageableDefault(100) @ParameterObject Pageable page
            , @ModelAttribute @ParameterObject EstablishmentSearchForm searchForm) {
        return ResponseEntity.ok(this.establishmentService.findAll(searchForm, page));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EstablishmentDto> findById(@PathVariable UUID id) {
        if (!this.establishmentService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(this.establishmentService.findById(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EstablishmentDto> update(@RequestBody @Valid EstablishmentUpdateForm updateForm, @PathVariable("id") final UUID id) {
        return ResponseEntity.ok(this.establishmentService.update(id, updateForm));
    }

    @PostMapping(value = "/subsidiaries", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EstablishmentDto> createInternal(@RequestBody @Valid EstablishmentCreationForm creationForm) {
        var response = this.establishmentService.createInternal(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), EstablishmentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping(value = "/{id}/level-of-studies", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EstablishmentLiteDto> createLevelOfStudies(@RequestBody @Valid EstablishmentLevelOfStudyCreationForm creationForm, @PathVariable UUID id) {
        var response = this.establishmentService.createLevelOfStudies(id, creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), EstablishmentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping(value = "/{id}/students", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<StudentLiteDto>> findByEstablishmentId(@PathVariable UUID id, @PageableDefault(100) @ParameterObject Pageable page
            , @ModelAttribute @ParameterObject EstablishmentSearchForm searchForm) {
        return ResponseEntity.ok(this.studentService.findByEstablishmentId(id, page));
    }
}
