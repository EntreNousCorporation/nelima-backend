package com.ypyit.neoelima.domain.establishment.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallmentCreationForm {

    @NotNull
    @Positive
    private BigDecimal amount;
    @NotNull
    private UUID studentFeeId;
    @NotNull
    private UUID paymentId;
}
