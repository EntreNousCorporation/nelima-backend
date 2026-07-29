package com.ypyit.neoelima.common.service.email.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EmailTemplateType {

    RESET_PASSWORD("reset-password"),
    WELCOME_USER("welcome-user"),
    WELCOME_MOBILE_USER("welcome-mobile-user"),
    RESET_MOBILE_PASSWORD("reset-mobile-password"),
    RESEND_MOBILE_OTP("resend-mobile-otp"),
    INVITE_STUDENT_PARENT("invite-student-parent"),
    RECEIPT("receipt");

    private final String value;
}
