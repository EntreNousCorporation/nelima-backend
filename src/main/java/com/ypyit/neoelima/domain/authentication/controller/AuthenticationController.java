package com.ypyit.neoelima.domain.authentication.controller;


import com.ypyit.neoelima.common.advice.error.AuthError;
import com.ypyit.neoelima.common.advice.error.AuthErrorType;
import com.ypyit.neoelima.domain.authentication.dto.AuthRequest;
import com.ypyit.neoelima.domain.authentication.dto.Token;
import com.ypyit.neoelima.domain.authentication.dto.TokenIntrospection;
import com.ypyit.neoelima.domain.authentication.service.AuthService;
import com.ypyit.neoelima.domain.user.service.SessionService;
import com.ypyit.neoelima.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final SessionService sessionService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthRequest request) {
        try {
            authenticationManager.authenticate(new
                    UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (Exception ex) {
            log.error("CONNECTION_FAILED: user {} failed to connect", request.getUsername());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(AuthError.builder()
                            .error(AuthErrorType.INVALID_GRANT.getValue())
                            .errorDescription(ExceptionUtils.getRootCauseMessage(ex)).build());
        }
        Token token = authService.generateToken(request.getUsername());
        this.userService.updateLastLogin(request.getUsername());
        log.info("CONNECTION_SUCCESS: user {} connected", request.getUsername());

        return ResponseEntity.ok(token);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logoutCurrentSession(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        UUID sessionId = UUID.fromString(authService.extractSessionId(token.substring("Bearer ".length())));
        sessionService.endSession(sessionId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/introspection")
    public ResponseEntity<TokenIntrospection> introspection(@RequestParam String token) {
        return ResponseEntity.ok(this.authService.parseJwt(token));
    }
}
