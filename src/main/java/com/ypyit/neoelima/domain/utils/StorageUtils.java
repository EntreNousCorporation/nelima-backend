package com.ypyit.neoelima.domain.utils;

import com.ypyit.neoelima.domain.storage.dto.StorageDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
public final class StorageUtils {

    public static final String STUDENTS_SHEET_NAME = "Students";

    public static final int STUDENT_FIRST_NAME = 1;

    public static final int STUDENT_LAST_NAME = 2;

    public static final int STUDENT_REGISTRATION_NUMBER = 3;

    public static final int STUDENT_BIRTHDAY = 4;

    public static final int STUDENT_LEVEL_OF_STUDY = 5;

    public static final int PLACE_OF_BIRTH = 6;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMHHmmss");

    private static final String TMP_DIR = System.getProperty("java.io.tmpdir", "/tmp");

    private static final String DELIMITER_SLASH = "/";


    private static final String DELIMITER_DOT = ".";

    private static final String POLICY_FILE = "storage/policy";

    private static final String IMAGE_PATTERN = "([^\\s]+(\\.(?i)(jpe?g|png|gif|bmp|tiff|svg|tif))$)";

    private static final String VIDEO_PATTERN = "([^\\s]+(\\.(?i)(flv|mp4|m3u8|ts|3gp|mov|avi|wmv))$)";

    private StorageUtils() {
        throw new UnsupportedOperationException("StorageUtils amy not be instantiated");
    }

    public static Optional<String> createFileOnSystem(StorageDto storageDto, String fileName) {
        try {
            if (StringUtils.isBlank(storageDto.getBase64())) {
                throw new IllegalArgumentException("Base64 name must be provided");
            }
            Assert.notNull(storageDto.getFileExtension(), "File extension must be provided");
            Assert.notNull(fileName, "File name must be provided");

            File file = new File(fileName);
            byte[] bytes = Base64.decodeBase64(storageDto.getBase64());
            FileUtils.writeByteArrayToFile(file, bytes);
            return Optional.of(fileName);
        } catch (Exception e) {
            log.error("Cannot create file on system ", e);
        }

        return Optional.empty();
    }


    public static void deleteFileOnSystem(String fileName) {
        boolean fileIsDeleted = FileUtils.deleteQuietly(new File(fileName));
        if (!fileIsDeleted) {
            log.error("File {} cannot be deleted", fileName);
        }
    }

    public static String generateFileName() {
        return UUID.randomUUID().toString()
                .concat("-" + FORMATTER.format(LocalDateTime.now(ZoneOffset.UTC)));
    }

    public static String generateAbsoluteFileName(String fileName, String fileExtension) {
        Assert.notNull(fileName, "File name must be provided");
        Assert.notNull(fileExtension, "File extension must be provided");

        return Paths.get(TMP_DIR, joinFileNameWithExtension(fileName, fileExtension)).toString();
    }

    public static String concatWithSlash(String... args) {
        return String.join(DELIMITER_SLASH, args);
    }

    public static Optional<String> loadPolicy() {
        try {
            InputStream inputStream = new ClassPathResource(POLICY_FILE).getInputStream();
            return Optional.of(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Cannot load policy file", e);
        }
        return Optional.empty();
    }

    public static String joinFileNameWithExtension(String fileName, String fileExtension) {
        return new StringJoiner(DELIMITER_DOT)
                .add(fileName)
                .add(fileExtension)
                .toString();
    }

    public static boolean isImageFile(String fileExtension) {
        if (StringUtils.isBlank(fileExtension)) {
            return false;
        }

        return Pattern.compile(IMAGE_PATTERN)
                .matcher(fileExtension.toLowerCase())
                .matches();
    }

    public static boolean isVideoFile(String fileExtension) {
        if (StringUtils.isBlank(fileExtension)) {
            return false;
        }

        return Pattern.compile(VIDEO_PATTERN)
                .matcher(fileExtension.toLowerCase())
                .matches();
    }

    public static boolean isValidExtension(String fileExtension) {
        if (StringUtils.isBlank(fileExtension)) {
            return false;
        }
        String generatedFileName = generateFileName()
                .concat(DELIMITER_DOT)
                .concat(fileExtension);

        return isVideoFile(generatedFileName) || isImageFile(generatedFileName);
    }

    public static String buildFileAbsolutePath(String finalFileName, List<String> recursiveDirs) {
        if (CollectionUtils.isEmpty(recursiveDirs)) {
            return finalFileName;
        }

        List<String> dirsToConcat = new ArrayList<>(recursiveDirs);
        dirsToConcat.add(finalFileName);

        return String.join(DELIMITER_SLASH, dirsToConcat);
    }
}
