package com.szzh.loggerserver.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;

/**
 * 态势记录写入命令。
 */
@Getter
public class SituationRecordCommand {

    private final String instanceId;

    private final int senderId;

    private final int messageType;

    private final int messageCode;

    private final long simTime;

    private final byte[] rawData;

    /**
     * 创建态势记录写入命令。
     *
     * @param instanceId 仿真实例 ID。
     * @param senderId 发送方 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param simTime 仿真时间。
     * @param rawData 原始二进制数据。
     */
    @Builder
    public SituationRecordCommand(String instanceId,
                                  int senderId,
                                  int messageType,
                                  int messageCode,
                                  long simTime,
                                  byte[] rawData) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        if (rawData == null || rawData.length == 0) {
            throw new IllegalArgumentException("rawData 不能为空");
        }
        this.instanceId = instanceId.trim();
        this.senderId = senderId;
        this.messageType = messageType;
        this.messageCode = messageCode;
        this.simTime = simTime;
        this.rawData = Arrays.copyOf(rawData, rawData.length);
    }
}
