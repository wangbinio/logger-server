package com.szzh.replayserver.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.szzh.common.json.JsonUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 回放倍速 payload。
 */
@Getter
@ToString
@EqualsAndHashCode
public class ReplayRatePayload {

    private final double rate;

    /**
     * 创建回放倍速 payload。
     *
     * @param rate 回放倍率。
     */
    public ReplayRatePayload(double rate) {
        if (rate <= 0D) {
            throw new IllegalArgumentException("rate 必须大于 0");
        }
        this.rate = rate;
    }

    /**
     * 从原始 JSON 载荷解析回放倍速 payload。
     *
     * @param rawData 原始 JSON 载荷。
     * @return 回放倍速 payload。
     */
    public static ReplayRatePayload fromRawData(byte[] rawData) {
        JsonNode jsonNode = JsonUtil.readTree(rawData);
        JsonNode rateNode = jsonNode.get("rate");
        if (rateNode == null || rateNode.isNull() || !rateNode.isNumber()) {
            throw new IllegalArgumentException("缺少必填数值字段: rate");
        }
        return new ReplayRatePayload(rateNode.asDouble());
    }
}
