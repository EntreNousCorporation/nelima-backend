package com.ypyit.neoelima.config.properties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@NoArgsConstructor
@ConfigurationProperties(prefix = "email-config")
public class EmailConfigProperties {

    private String supportEmail;
    private String platformName;
    private String platformAddress;
    private String emailFrom;
    private String resetPasswordUri;
    private String resetPasswordAdminUrl;
    private String resetPasswordPartnerUrl;
}
