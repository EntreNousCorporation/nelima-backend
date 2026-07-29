package com.ypyit.neoelima.common.service.email.service;

import com.ypyit.neoelima.config.properties.EmailConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

@Component
@RequiredArgsConstructor
public class EmailDefaultProperties {

    private final EmailConfigProperties emailConfigProperties;

    @Value("${main-domain:freewan-ci.com}")
    private String mainDomain;

    public Context getDefaultContext() {
        Context context = new Context();
        context.setVariable(EmailConstants.SUPPORT_EMAIL, emailConfigProperties.getSupportEmail());
        context.setVariable(EmailConstants.PLATFORM_NAME, emailConfigProperties.getPlatformName());
        context.setVariable(EmailConstants.PLATFORM_ADDRESS, emailConfigProperties.getPlatformAddress());
        context.setVariable(EmailConstants.PLATFORM_DOMAIN, mainDomain);

        return context;
    }
}
