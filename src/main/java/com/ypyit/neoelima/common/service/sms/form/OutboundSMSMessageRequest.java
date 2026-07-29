package com.ypyit.neoelima.common.service.sms.form;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundSMSMessageRequest {

    private String address;
    private String senderAddress;
    private OutboundSMSTextMessage outboundSMSTextMessage;
}
