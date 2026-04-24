package com.szzh.loggerserver.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * JSON 工具类测试。
 */
class JsonUtilTest {

    /**
     * 验证可以从 JSON 中读取必填字段。
     */
    @Test
    void shouldReadRequiredText() {
        byte[] rawData = JsonUtil.toUtf8Bytes("{\"instanceId\":\"demo-instance\"}");

        String instanceId = JsonUtil.readRequiredText(rawData, "instanceId");

        Assertions.assertEquals("demo-instance", instanceId);
    }

    /**
     * 验证缺少字段时抛出异常。
     */
    @Test
    void shouldThrowWhenFieldMissing() {
        byte[] rawData = JsonUtil.toUtf8Bytes("{\"name\":\"demo\"}");

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> JsonUtil.readRequiredText(rawData, "instanceId"));
    }
}
