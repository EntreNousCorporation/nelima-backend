package com.ypyit.neoelima.domain.user.controller;

import com.ypyit.neoelima.domain.user.dto.RoleDto;
import com.ypyit.neoelima.domain.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RoleDto>> findAll() {
        return ResponseEntity.ok(this.roleService.findAll());
    }
}
