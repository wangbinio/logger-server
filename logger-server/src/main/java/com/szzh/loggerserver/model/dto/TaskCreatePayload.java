package com.szzh.loggerserver.model.dto;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.json.JsonUtil;
import com.szzh.common.protocol.ProtocolData;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/**
 * 仿真任务创建载荷。
 */
@Getter
public class TaskCreatePayload {

    private final String instanceId;

    /**
     * 创建仿真任务载荷。
     *
     * @param instanceId 仿真实例 ID。
     */
    @Builder
    public TaskCreatePayload(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        this.instanceId = instanceId.trim();
    }

    /**
     * 从协议数据中解析任务载荷。
     *
     * @param protocolData 协议数据。
     * @return 任务载荷。
     */
    public static TaskCreatePayload fromProtocolData(ProtocolData protocolData) {
        Objects.requireNonNull(protocolData, "protocolData 不能为空");
        return fromRawData(protocolData.getRawData());
    }

    /**
     * 从原始 JSON 数据中解析任务载荷。
     *
     * @param rawData 原始 JSON 字节数组。
     * @return 任务载荷。
     */
    public static TaskCreatePayload fromRawData(byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            throw BusinessException.state("创建消息载荷不能为空");
        }
        try {
            return TaskCreatePayload.builder()
                    .instanceId(JsonUtil.readRequiredText(rawData, "instanceId"))
                    .build();
        } catch (IllegalArgumentException exception) {
            throw BusinessException.state("创建消息缺少合法 instanceId");
        }
    }
}
