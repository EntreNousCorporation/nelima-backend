package com.ypyit.neoelima.domain.storage.service.impl;

import com.ypyit.neoelima.common.exception.StorageException;
import com.ypyit.neoelima.config.properties.StorageProperties;
import com.ypyit.neoelima.domain.storage.service.PrivateFileStore;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Slf4j
@Service
public class MinioPrivateFileStore implements PrivateFileStore {

    private final StorageProperties storageProperties;
    private final String bucket;
    private volatile MinioClient minioClient;
    private volatile boolean bucketReady;

    public MinioPrivateFileStore(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.bucket = storageProperties.getPrivateBucketName();
    }

    /**
     * Client construit au premier usage : une URL de stockage invalide ou absente ne doit pas
     * empêcher l'application de démarrer — seul le dossier élève en dépend.
     */
    private MinioClient client() {
        if (this.minioClient == null) {
            synchronized (this) {
                if (this.minioClient == null) {
                    this.minioClient = MinioClient.builder()
                            .endpoint(StringUtils.defaultIfBlank(
                                    this.storageProperties.getInternalUrl(), this.storageProperties.getUrl()))
                            .credentials(this.storageProperties.getApiKey(), this.storageProperties.getApiSecret())
                            .build();
                }
            }
        }
        return this.minioClient;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            this.ensureBucket();
            this.client().putObject(PutObjectArgs.builder()
                    .bucket(this.bucket).object(key)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public byte[] get(String key) {
        try (GetObjectResponse response = this.client().getObject(
                GetObjectArgs.builder().bucket(this.bucket).object(key).build())) {
            return response.readAllBytes();
        } catch (Exception e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteQuietly(String key) {
        try {
            this.client().removeObject(RemoveObjectArgs.builder().bucket(this.bucket).object(key).build());
        } catch (Exception e) {
            log.error("Error while deleting private file {} in bucket {}", key, this.bucket);
        }
    }

    /** Bucket créé à la première écriture, sans politique : privé, comme MinIO le crée par défaut. */
    private void ensureBucket() throws Exception {
        if (this.bucketReady) {
            return;
        }
        if (!this.client().bucketExists(BucketExistsArgs.builder().bucket(this.bucket).build())) {
            this.client().makeBucket(MakeBucketArgs.builder().bucket(this.bucket).build());
        }
        this.bucketReady = true;
    }
}
