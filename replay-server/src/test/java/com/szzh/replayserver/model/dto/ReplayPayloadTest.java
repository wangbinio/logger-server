package com.szzh.replayserver.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.szzh.common.json.JsonUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * 回放 payload 测试。
 */
class ReplayPayloadTest {

    /**
     * 验证创建 payload 能解析并标准化实例 ID。
     */
    @Test
    void shouldParseCreatePayload() {
        ReplayCreatePayload payload = ReplayCreatePayload.fromRawData(
                "{\"instanceId\":\" instance-001 \"}".getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals("instance-001", payload.getInstanceId());
    }

    /**
     * 验证元信息 payload 能序列化开始时间、结束时间和持续时间。
     */
    @Test
    void shouldSerializeMetadataPayload() {
        ReplayMetadataPayload payload = new ReplayMetadataPayload(1_000L, 2_500L);

        JsonNode jsonNode = JsonUtil.readTree(JsonUtil.toUtf8Bytes(JsonUtil.toJson(payload)));

        Assertions.assertEquals(1_000L, jsonNode.get("startTime").asLong());
        Assertions.assertEquals(2_500L, jsonNode.get("endTime").asLong());
        Assertions.assertEquals(1_500L, jsonNode.get("duration").asLong());
    }

    /**
     * 验证倍速 payload 只接受大于 0 的倍率。
     */
    @Test
    void shouldParsePositiveReplayRate() {
        ReplayRatePayload payload = ReplayRatePayload.fromRawData(
                "{\"rate\":2.5}".getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals(2.5D, payload.getRate(), 0.0001D);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ReplayRatePayload.fromRawData("{\"rate\":0}".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 验证跳转 payload 能解析目标时间。
     */
    @Test
    void shouldParseJumpTime() {
        ReplayJumpPayload payload = ReplayJumpPayload.fromRawData(
                "{\"time\":1713952800000}".getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals(1713952800000L, payload.getTime());
    }

    /**
     * 验证缺少必填字段时抛出明确异常。
     */
    @Test
    void shouldRejectMissingRequiredFields() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ReplayCreatePayload.fromRawData("{}".getBytes(StandardCharsets.UTF_8)));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ReplayRatePayload.fromRawData("{}".getBytes(StandardCharsets.UTF_8)));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ReplayJumpPayload.fromRawData("{}".getBytes(StandardCharsets.UTF_8)));
    }
}
