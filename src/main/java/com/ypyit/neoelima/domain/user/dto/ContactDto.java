package com.ypyit.neoelima.domain.user.dto;

import com.ypyit.neoelima.domain.user.enums.ContactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDto {

    private UUID id;
    private ContactType type;
    private String value;
    private boolean isPrimary;
    private boolean whatsApp;
}
