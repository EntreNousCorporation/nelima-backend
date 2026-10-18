package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.StudentDocumentKind;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class StudentDocumentDto {
    private String id;
    private StudentDocumentKind kind;
    private String label;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private Instant createdAt;
    private String createdBy;
}
