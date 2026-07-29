package com.ypyit.neoelima.domain.storage.service;


import com.ypyit.neoelima.common.exception.StorageException;
import com.ypyit.neoelima.domain.storage.dto.StorageDto;
import com.ypyit.neoelima.domain.storage.dto.StorageResponse;

import java.util.List;
import java.util.Optional;

public interface StorageService {

    boolean bucketExists(String bucketName) throws StorageException;

    void createBucket(String bucketName) throws StorageException;

    void deleteQuietly(String rootDir, String fileName) throws StorageException;

    Optional<String> uploadDynamicFile(StorageDto storageDto) throws StorageException;

    Optional<String> uploadStaticFile(StorageDto storageDto) throws StorageException;

    List<StorageResponse> getStaticFiles() throws StorageException;

    Optional<StorageResponse> getStaticFileByName(String fileName) throws StorageException;

    String getBucketName() throws StorageException;
}
