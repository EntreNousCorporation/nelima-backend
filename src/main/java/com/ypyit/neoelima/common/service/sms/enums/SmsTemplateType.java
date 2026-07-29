package com.ypyit.neoelima.common.service.sms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public enum SmsTemplateType {

    WELCOME_MOBILE_USER("welcome-mobile-user-sms"),

    INVITE_STUDENT_PARENT_SMS("invite-student-parent-sms");

    private final String value;
}
