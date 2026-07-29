package com.ypyit.neoelima.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "nelima.billing")
public class BillingProperties {

    /** Commission YPYit sur les paiements en ligne, en fraction (0.02 pour 2 %). */
    private BigDecimal commissionRate = new BigDecimal("0.02");

    private String currency = "XOF";

    private String paymentSuccessUrl;

    private String paymentErrorUrl;
}
