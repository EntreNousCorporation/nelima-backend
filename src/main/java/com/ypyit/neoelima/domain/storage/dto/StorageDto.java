package com.ypyit.neoelima.domain.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageDto {

    private String fileName;
    private String bucketName;
    private String base64;
    private String fileExtension;
    private List<String> recursiveDirs;
}
