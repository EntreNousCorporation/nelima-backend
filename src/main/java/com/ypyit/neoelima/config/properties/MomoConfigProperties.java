package com.ypyit.neoelima.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payment.cfg.momo")
public class MomoConfigProperties {

    public static final String X_REFERENCE_ID = "X-Reference-Id";

    public static final String X_CALLBACK_URL = "X-Callback-Url";

    public static final String COLLECTION_HOST = "COLLECTION_HOST";

    public static final String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";

    public static final String X_TARGET_ENVIRONMENT = "X-Target-Environment";

    private String ocpApimSubscriptionKey;

    private String collectionHost;

    private String xTargetEnvironment;

    private String xCallbackUrl;

    private String apiKey;

    private String apiSecret;

    private String tokenUri;

    private String requestToPayUri;
}
