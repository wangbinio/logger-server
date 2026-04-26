package com.szzh.replayserver.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.szzh.common.json.JsonUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 回放时间跳转 payload。
 */
@Getter
@ToString
@EqualsAndHashCode
public class ReplayJumpPayload {

    private final long time;

    /**
     * 创建回放时间跳转 payload。
     *
     * @param time 目标仿真时间。
     */
    public ReplayJumpPayload(long time) {
        this.time = time;
    }

    /**
     * 从原始 JSON 载荷解析回放时间跳转 payload。
     *
     * @param rawData 原始 JSON 载荷。
     * @return 回放时间跳转 payload。
     */
    public static ReplayJumpPayload fromRawData(byte[] rawData) {
        JsonNode jsonNode = JsonUtil.readTree(rawData);
        JsonNode timeNode = jsonNode.get("time");
        if (timeNode == null || timeNode.isNull() || !timeNode.isNumber()) {
            throw new IllegalArgumentException("缺少必填数值字段: time");
        }
        return new ReplayJumpPayload(timeNode.asLong());
    }
}
