package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.FeeDto;
import com.ypyit.neoelima.domain.establishment.form.FeeCreationForm;
import com.ypyit.neoelima.domain.establishment.form.FeeSearchForm;
import com.ypyit.neoelima.domain.establishment.form.FeeUpdateForm;
import com.ypyit.neoelima.domain.establishment.service.FeeService;
import com.ypyit.neoelima.domain.utils.ControllerUtils;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/fees")
@RequiredArgsConstructor
public class FeeController {

    private final FeeService feeService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('fee:write')")
    public ResponseEntity<FeeDto> create(@RequestBody @Valid FeeCreationForm creationForm) {
        var response = this.feeService.create(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), FeeController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('fee:write')")
    public ResponseEntity<FeeDto> update(@RequestBody @Valid FeeUpdateForm updateForm, @PathVariable("id") final UUID id) {
        return ResponseEntity.ok(this.feeService.update(id, updateForm));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('fee:read')")
    public ResponseEntity<Page<FeeDto>> search(@ModelAttribute @ParameterObject FeeSearchForm searchForm,
                                               @PageableDefault @ParameterObject Pageable page) {
        return ResponseEntity.ok(this.feeService.search(searchForm, page));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('fee:read')")
    public ResponseEntity<FeeDto> findById(@PathVariable UUID id) {
        if (!this.feeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(this.feeService.findById(id));
    }
}
