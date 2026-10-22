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
    RECEIPT("receipt"),
    DEMO_REQUEST("demo-request"),
    /** Suggestion déposée par un parent depuis l'application. */
    FEEDBACK("feedback"),
    /** Assurance ou visite technique d'un véhicule de transport qui arrive à échéance. */
    TRANSPORT_VEHICLE_EXPIRY("transport-vehicle-expiry");

    private final String value;
}
