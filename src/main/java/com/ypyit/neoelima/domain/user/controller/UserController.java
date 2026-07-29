package com.ypyit.neoelima.domain.user.controller;

import com.ypyit.neoelima.domain.authentication.dto.TokenIntrospection;
import com.ypyit.neoelima.domain.establishment.dto.StudentLiteDto;
import com.ypyit.neoelima.domain.transverse.dto.CheckResourceDto;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.form.ChangePasswordForm;
import com.ypyit.neoelima.domain.user.form.InitResetPasswordForm;
import com.ypyit.neoelima.domain.user.form.MobileUserSignupForm;
import com.ypyit.neoelima.domain.user.form.ResetMobilePasswordForm;
import com.ypyit.neoelima.domain.user.form.ResetPasswordForm;
import com.ypyit.neoelima.domain.user.form.UserUpdateForm;
import com.ypyit.neoelima.domain.user.service.UserService;
import com.ypyit.neoelima.domain.utils.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Slf4j
@CrossOrigin("*")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/users")
public class UserController {

    private final UserService userService;

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> update(@RequestBody @Valid UserUpdateForm updateForm, @PathVariable("id") final UUID id) {
        log.debug("updating user with id {}", id);
        return ResponseEntity.ok(this.userService.update(id, updateForm));
    }

    @PutMapping(value = "/change-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> changePassword(@RequestBody @Valid ChangePasswordForm changePasswordForm) {
        log.info("calling path /change-password");
        var response = this.userService.changePassword(changePasswordForm);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/init-reset-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> initResetPassword(@RequestBody @Valid InitResetPasswordForm initResetPasswordForm) {
        log.info("calling path /init-reset-password");
        var response = this.userService.initResetPassword(initResetPasswordForm);
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> resetPassword(@RequestBody @Valid ResetPasswordForm resetPasswordForm) {
        log.info("calling path /reset-password");
        var response = this.userService.resetPassword(resetPasswordForm);
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/mobile/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> resetMobilePassword(@RequestBody @Valid ResetMobilePasswordForm resetPasswordForm) {
        log.info("calling path /mobile/reset-password");
        var response = this.userService.resetMobilePassword(resetPasswordForm);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/mobile/init-reset-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> initMobileResetPassword(@RequestBody @Valid InitResetPasswordForm initResetPasswordForm) {
        log.info("calling path /mobile/init-reset-password");
        var response = this.userService.initMobileResetPassword(initResetPasswordForm);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> getMe() {
        return ResponseEntity.ok(this.userService.getMe());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> getMe(@PathVariable("id") final UUID id) {
        return ResponseEntity.ok(this.userService.getMe(id));
    }

    @GetMapping("/introspection")
    public ResponseEntity<TokenIntrospection> introspection(@RequestParam String token) {
        log.info("calling path /introspection");
        return ResponseEntity.ok(this.userService.parseJwt(token));
    }

    @PostMapping(value = "/mobile", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> createMobileUser(@RequestBody @Valid MobileUserSignupForm mobileUserSignupForm) {
        log.info("creating new mobile user");
        mobileUserSignupForm.setSendEmail(true);
        var response = this.userService.createMobileUser(mobileUserSignupForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), UserController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping(value = "/check-signup-otp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CheckResourceDto> getMe(@RequestParam String otp, @RequestParam String username) {
        log.info("calling path /check-signup-otp");
        return ResponseEntity.ok(this.userService.otpExists(username, otp));
    }

    @PostMapping(value = "/resend-signup-otp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> resendSignupOtp(@RequestParam String username) {
        this.userService.resendMobileOtp(username);
        return ResponseEntity.accepted().build();
    }

    @GetMapping(value = "/{id}/students", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StudentLiteDto>> findStudents(@PathVariable("id") final UUID id) {
        return ResponseEntity.ok(this.userService.findStudents(id));
    }
}
