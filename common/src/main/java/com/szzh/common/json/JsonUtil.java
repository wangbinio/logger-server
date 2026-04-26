package com.szzh.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * JSON 处理工具类。
 */
public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    /**
     * 解析字节数组为 JSON 节点。
     *
     * @param rawData 原始 JSON 字节数组。
     * @return JSON 节点。
     */
    public static JsonNode readTree(byte[] rawData) {
        try {
            return OBJECT_MAPPER.readTree(rawData);
        } catch (IOException exception) {
            throw new IllegalArgumentException("JSON 解析失败", exception);
        }
    }

    /**
     * 解析字节数组为 Map。
     *
     * @param rawData 原始 JSON 字节数组。
     * @return JSON 对应的 Map。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(byte[] rawData) {
        try {
            return OBJECT_MAPPER.readValue(rawData, Map.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("JSON Map 解析失败", exception);
        }
    }

    /**
     * 从原始 JSON 中读取必填文本字段。
     *
     * @param rawData 原始 JSON 字节数组。
     * @param fieldName 字段名。
     * @return 字段文本值。
     */
    public static String readRequiredText(byte[] rawData, String fieldName) {
        return readRequiredText(readTree(rawData), fieldName);
    }

    /**
     * 从 JSON 节点中读取必填文本字段。
     *
     * @param jsonNode JSON 节点。
     * @param fieldName 字段名。
     * @return 字段文本值。
     */
    public static String readRequiredText(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            throw new IllegalArgumentException("缺少必填字段: " + fieldName);
        }
        String value = fieldNode.asText();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("字段值不能为空: " + fieldName);
        }
        return value.trim();
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 目标对象。
     * @return JSON 字符串。
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 序列化失败", exception);
        }
    }

    /**
     * 把文本转换为 UTF-8 字节数组。
     *
     * @param value 文本值。
     * @return UTF-8 字节数组。
     */
    public static byte[] toUtf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
