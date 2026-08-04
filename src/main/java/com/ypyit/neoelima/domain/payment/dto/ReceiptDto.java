package com.ypyit.neoelima.domain.payment.dto;

import com.ypyit.neoelima.common.dto.BaseDto;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDto extends BaseDto {

    private String number;
    private BigDecimal amount;
    private Instant issuedAt;
    private String studentLabel;
    private String studentRegistrationNumber;
    private String payerLabel;
    private PaymentChannel channel;
}
