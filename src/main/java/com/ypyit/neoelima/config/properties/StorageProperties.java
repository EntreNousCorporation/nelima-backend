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
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String url;
    private String apiKey;
    private String apiSecret;
    private String bucketName;
}
