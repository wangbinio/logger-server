package com.szzh.loggerserver.model.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 控制时间点写入命令。
 */
@Getter
public class TimeControlRecordCommand {

    private final String instanceId;

    private final long simTime;

    private final double rate;

    private final int senderId;

    private final int messageType;

    private final int messageCode;

    /**
     * 创建控制时间点写入命令。
     *
     * @param instanceId 仿真实例 ID。
     * @param simTime 控制命令生效后的仿真时间。
     * @param rate 控制命令生效后的回放倍率。
     * @param senderId 发送方 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     */
    @Builder
    public TimeControlRecordCommand(String instanceId,
                                    long simTime,
                                    double rate,
                                    int senderId,
                                    int messageType,
                                    int messageCode) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        if (rate < 0D) {
            throw new IllegalArgumentException("rate 不能为负数");
        }
        this.instanceId = instanceId.trim();
        this.simTime = simTime;
        this.rate = rate;
        this.senderId = senderId;
        this.messageType = messageType;
        this.messageCode = messageCode;
    }
}
