package com.szzh.replayserver.model.dto;

import com.szzh.common.json.JsonUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 回放创建或停止任务 payload。
 */
@Getter
@ToString
@EqualsAndHashCode
public class ReplayCreatePayload {

    private final String instanceId;

    /**
     * 创建回放任务 payload。
     *
     * @param instanceId 实例 ID。
     */
    public ReplayCreatePayload(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        this.instanceId = instanceId.trim();
    }

    /**
     * 从原始 JSON 载荷解析回放任务 payload。
     *
     * @param rawData 原始 JSON 载荷。
     * @return 回放任务 payload。
     */
    public static ReplayCreatePayload fromRawData(byte[] rawData) {
        return new ReplayCreatePayload(JsonUtil.readRequiredText(rawData, "instanceId"));
    }
}
