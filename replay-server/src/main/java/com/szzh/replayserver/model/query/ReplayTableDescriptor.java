package com.szzh.replayserver.model.query;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 回放态势子表描述。
 */
@Getter
@ToString
@EqualsAndHashCode
public class ReplayTableDescriptor {

    private final String tableName;

    private final int senderId;

    private final int messageType;

    private final int messageCode;

    private final ReplayTableType tableType;

    /**
     * 创建回放态势子表描述。
     *
     * @param tableName 子表名。
     * @param senderId 发送方 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param tableType 子表类型。
     */
    public ReplayTableDescriptor(String tableName,
                                 int senderId,
                                 int messageType,
                                 int messageCode,
                                 ReplayTableType tableType) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName 不能为空");
        }
        if (tableType == null) {
            throw new IllegalArgumentException("tableType 不能为空");
        }
        this.tableName = tableName.trim();
        this.senderId = senderId;
        this.messageType = messageType;
        this.messageCode = messageCode;
        this.tableType = tableType;
    }

    /**
     * 使用新的表类型重建子表描述。
     *
     * @param tableType 新表类型。
     * @return 新子表描述。
     */
    public ReplayTableDescriptor withType(ReplayTableType tableType) {
        return new ReplayTableDescriptor(tableName, senderId, messageType, messageCode, tableType);
    }
}
