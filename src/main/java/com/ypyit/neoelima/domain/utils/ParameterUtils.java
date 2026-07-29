package com.ypyit.neoelima.domain.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.ypyit.neoelima.domain.user.dto.TranslateDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

public final class ParameterUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ParameterUtils() {
        throw new UnsupportedOperationException("ParameterUtils may not be instantiated");
    }

    public static JsonNode getJsonNode(String url) throws IOException {
        Resource srcRegion = new ClassPathResource(url);
        byte[] jsonData = ByteStreams.toByteArray(srcRegion.getInputStream());
        return MAPPER.readTree(jsonData);
    }

    public static TranslateDto getTranslateFromJsonNode(JsonNode jsonNode) {
        return MAPPER.convertValue(jsonNode, new TypeReference<>() {
        });
    }

    public static <T> T getValueWithType(JsonNode jsonNode, Class<T> clazz) {
        return MAPPER.convertValue(jsonNode, clazz);
    }

    public static <T> List<T> getValuesWithType(JsonNode jsonNode, Class<T> clazz) {
        return MAPPER.convertValue(jsonNode, MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
    }
}
