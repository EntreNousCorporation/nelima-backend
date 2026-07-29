package com.ypyit.neoelima.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "security.jwt")
public class SecurityProperties {

    private String secret;
    private Long expirationDate;
    private String issuer;
}
