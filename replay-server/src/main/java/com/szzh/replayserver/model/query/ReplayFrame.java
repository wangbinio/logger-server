package com.szzh.replayserver.model.query;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Arrays;

/**
 * 回放数据帧。
 */
@ToString(exclude = "rawData")
@EqualsAndHashCode
public class ReplayFrame {

    private final String tableName;

    private final int senderId;

    private final int messageType;

    private final int messageCode;

    private final long simTime;

    private final byte[] rawData;

    /**
     * 创建回放数据帧。
     *
     * @param tableName 子表名。
     * @param senderId 发送方 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param simTime 仿真时间。
     * @param rawData 原始协议载荷。
     */
    public ReplayFrame(String tableName,
                       int senderId,
                       int messageType,
                       int messageCode,
                       long simTime,
                       byte[] rawData) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName 不能为空");
        }
        if (rawData == null) {
            throw new IllegalArgumentException("rawData 不能为空");
        }
        this.tableName = tableName.trim();
        this.senderId = senderId;
        this.messageType = messageType;
        this.messageCode = messageCode;
        this.simTime = simTime;
        this.rawData = Arrays.copyOf(rawData, rawData.length);
    }

    /**
     * 获取子表名。
     *
     * @return 子表名。
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 获取发送方 ID。
     *
     * @return 发送方 ID。
     */
    public int getSenderId() {
        return senderId;
    }

    /**
     * 获取消息类型。
     *
     * @return 消息类型。
     */
    public int getMessageType() {
        return messageType;
    }

    /**
     * 获取消息编号。
     *
     * @return 消息编号。
     */
    public int getMessageCode() {
        return messageCode;
    }

    /**
     * 获取仿真时间。
     *
     * @return 仿真时间。
     */
    public long getSimTime() {
        return simTime;
    }

    /**
     * 获取原始协议载荷副本。
     *
     * @return 原始协议载荷副本。
     */
    public byte[] getRawData() {
        return Arrays.copyOf(rawData, rawData.length);
    }
}
