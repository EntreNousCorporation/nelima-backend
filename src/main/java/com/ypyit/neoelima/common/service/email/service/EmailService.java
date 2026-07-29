package com.ypyit.neoelima.common.service.email.service;

import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import org.springframework.lang.NonNull;
import org.thymeleaf.context.Context;

public interface EmailService {

    void send(@NonNull Context context, @NonNull EmailTemplateType type);
}
