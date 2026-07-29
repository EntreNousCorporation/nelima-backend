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
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {

    private String baseUrl;

    private String tokenUri;

    private String sendUri;

    private String username;

    private String password;
}
