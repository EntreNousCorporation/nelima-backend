package com.ypyit.neoelima.domain.storage.service.impl;


import com.ypyit.neoelima.common.exception.StorageException;
import com.ypyit.neoelima.config.properties.StorageProperties;
import com.ypyit.neoelima.domain.storage.dto.StorageDto;
import com.ypyit.neoelima.domain.storage.dto.StorageResponse;
import com.ypyit.neoelima.domain.storage.enums.StorageType;
import com.ypyit.neoelima.domain.storage.service.StorageService;
import com.ypyit.neoelima.domain.utils.FunctionalUtils;
import com.ypyit.neoelima.domain.utils.StorageUtils;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.UploadObjectArgs;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@Service
public class StorageServiceImpl implements StorageService {

    private MinioClient minioClient;
    private StorageProperties storageProperties;

    public StorageServiceImpl(StorageProperties storageProperties) {
        try {
            this.storageProperties = storageProperties;
            this.minioClient = MinioClient.builder()
                    .endpoint(storageProperties.getUrl())
                    .credentials(storageProperties.getApiKey(), storageProperties.getApiSecret())
                    .build();
        } catch (Exception e) {
            log.warn("Cannot configure Minio client {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @Override
    public boolean bucketExists(String bucketName) throws StorageException {
        try {
            return this.minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void createBucket(String bucketName) throws StorageException {
        try {
            this.minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("Error while creating bucket", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteQuietly(String rootDir, String fileName) throws StorageException {
        try {
            this.minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(rootDir).object(fileName).build());
        } catch (Exception e) {
            log.error("Error while deleting file with name {} in bucket {}", fileName, rootDir);
        }
    }

    @Override
    public Optional<String> uploadDynamicFile(StorageDto storageDto) throws StorageException {
        try {
            Assert.notNull(storageDto.getBucketName(), "Bucket name must be provided");
            Assert.notNull(storageDto.getBase64(), "Base64 name must be provided");
            Assert.notNull(storageDto.getFileExtension(), "File extension must be provided");
            this.checkFileExtension(storageDto.getFileExtension());

            boolean isBucketExists = this.bucketExists(storageDto.getBucketName());
            if (!isBucketExists) {
                this.createBucket(storageDto.getBucketName());
                this.applyPolicyToBucket(storageDto.getBucketName());
            }

            String fileName = StorageUtils.generateFileName();
            String fileAbsoluteNameOnSystem = StorageUtils
                    .generateAbsoluteFileName(fileName, storageDto.getFileExtension());

            Optional<String> fileIsCreatedOnSystem = StorageUtils
                    .createFileOnSystem(storageDto, fileAbsoluteNameOnSystem);
            if (fileIsCreatedOnSystem.isEmpty()) {
                log.error("Cannot create file on system");
            }

            String finalFileName = StorageUtils.
                    joinFileNameWithExtension(fileName, storageDto.getFileExtension());

            finalFileName = StorageUtils.buildFileAbsolutePath(finalFileName, storageDto.getRecursiveDirs());

            this.minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(storageDto.getBucketName())
                            .object(finalFileName)
                            .filename(fileAbsoluteNameOnSystem)
                            .build());

            StorageUtils.deleteFileOnSystem(fileAbsoluteNameOnSystem);

            return this.buildFileUrl(storageDto.getBucketName(), finalFileName);
        } catch (Exception e) {
            log.error("Error while uploading  dynamic file", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> uploadStaticFile(StorageDto storageDto) throws StorageException {
        try {
            Assert.notNull(storageDto.getBucketName(), "Bucket name must be provided");
            Assert.notNull(storageDto.getBase64(), "Base64 name must be provided");
            Assert.notNull(storageDto.getFileExtension(), "File extension must be provided");
            Assert.notNull(storageDto.getFileName(), "File name must be provided");
            this.checkFileExtension(storageDto.getFileExtension());

            boolean isBucketExists = this.bucketExists(storageDto.getBucketName());
            if (!isBucketExists) {
                this.createBucket(storageDto.getBucketName());
                this.applyPolicyToBucket(storageDto.getBucketName());
            }

            String fileAbsoluteNameOnSystem = StorageUtils
                    .generateAbsoluteFileName(storageDto.getFileName(), storageDto.getFileExtension());

            Optional<String> fileIsCreatedOnSystem = StorageUtils
                    .createFileOnSystem(storageDto, fileAbsoluteNameOnSystem);
            if (fileIsCreatedOnSystem.isEmpty()) {
                log.error("Cannot create file on system");
            }

            String finalFileName = StorageUtils.
                    joinFileNameWithExtension(storageDto.getFileName(), storageDto.getFileExtension());

            this.minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(storageDto.getBucketName())
                            .object(finalFileName)
                            .filename(fileAbsoluteNameOnSystem)
                            .build());

            StorageUtils.deleteFileOnSystem(fileAbsoluteNameOnSystem);
            return this.buildFileUrl(storageDto.getBucketName(), finalFileName);
        } catch (Exception e) {
            log.error("Error while uploading static file", e);
        }
        return Optional.empty();
    }

    @Override
    public List<StorageResponse> getStaticFiles() throws StorageException {
        List<StorageResponse> staticFiles = new ArrayList<>();
        try {
            String staticDir = StorageType.STATIC.getValue();
            Iterable<Result<Item>> results =
                    this.minioClient.listObjects(ListObjectsArgs.builder()
                            .bucket(staticDir).build());

            for (Result<Item> result : results) {
                Item item = result.get();
                String fileName = item.objectName();
                StorageResponse file = StorageResponse.builder()
                        .fileName(FilenameUtils.getBaseName(fileName))
                        .link(this.buildFileUrl(staticDir, fileName).orElse(null))
                        .build();
                staticFiles.add(file);
            }
            return staticFiles;
        } catch (Exception e) {
            log.error("Error while getting static files", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<StorageResponse> getStaticFileByName(String fileName) throws StorageException {
        try {
            List<StorageResponse> responses = getStaticFiles();
            Predicate<StorageResponse> pFileWithName = str -> Objects.nonNull(str)
                    && Objects.nonNull(str.getFileName())
                    && str.getFileName().equals(fileName);

            return FunctionalUtils
                    .safelyGetStream(responses)
                    .filter(pFileWithName)
                    .findFirst();
        } catch (Exception e) {
            log.error("Error while getting static files", e);
            return Optional.empty();
        }
    }

    @Override
    public String getBucketName() throws StorageException {
        try {
            return this.storageProperties.getBucketName();
        } catch (Exception e) {
            throw new StorageException("Error while getting bucket name", e);
        }
    }

    private void applyPolicyToBucket(String bucketName) {
        try {
            Optional<String> policyIsLoad = StorageUtils.loadPolicy();
            if (policyIsLoad.isEmpty()) {
                throw new StorageException("Policy cannot be loaded");
            }

            String policy = String.format(policyIsLoad.get(), bucketName, bucketName);
            this.minioClient.setBucketPolicy(SetBucketPolicyArgs
                    .builder()
                    .bucket(bucketName)
                    .config(policy)
                    .build());
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    private void checkFileExtension(String extension) {
        if (!StorageUtils.isValidExtension(extension)) {
            throw new StorageException(String.format("File has not valid extension %s", extension));
        }
    }

    private Optional<String> buildFileUrl(String bucketName, String finalFileName) {
        return Optional.of(StorageUtils
                .concatWithSlash(this.storageProperties.getUrl(),
                        bucketName, finalFileName));
    }
}
