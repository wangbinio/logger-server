package com.szzh.loggerserver.support.constant;

import org.springframework.util.StringUtils;

/**
 * TDengine 常量与命名规则。
 */
public final class TdengineConstants {

    public static final String STABLE_PREFIX = "situation_";

    public static final String CREATE_STABLE_SQL_TEMPLATE =
            "CREATE STABLE IF NOT EXISTS %s (ts TIMESTAMP, simtime BIGINT, rawdata VARBINARY(8192)) "
                    + "TAGS (sender_id INT, msgtype INT, msgcode INT)";

    public static final String INSERT_USING_SQL_TEMPLATE =
            "INSERT INTO %s USING %s TAGS (?, ?, ?) VALUES (NOW, ?, ?)";

    public static final String INSERT_STMT_SQL_TEMPLATE =
            "INSERT INTO ? USING %s TAGS (?, ?, ?) VALUES (?, ?)";

    private TdengineConstants() {
    }

    /**
     * 构建实例超级表名称。
     *
     * @param instanceId 仿真实例 ID。
     * @return 实例超级表名称。
     */
    public static String buildStableName(String instanceId) {
        return STABLE_PREFIX + sanitizeIdentifier(instanceId);
    }

    /**
     * 构建实例子表名称。
     *
     * @param instanceId 仿真实例 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param senderId 发送方 ID。
     * @return 实例子表名称。
     */
    public static String buildSubTableName(String instanceId, int messageType, int messageCode, int senderId) {
        return String.format("%s%d_%d_%d_%s",
                STABLE_PREFIX,
                messageType,
                messageCode,
                senderId,
                sanitizeIdentifier(instanceId));
    }

    /**
     * 构建创建超级表 SQL。
     *
     * @param instanceId 仿真实例 ID。
     * @return 创建超级表 SQL。
     */
    public static String buildCreateStableSql(String instanceId) {
        return String.format(CREATE_STABLE_SQL_TEMPLATE, buildStableName(instanceId));
    }

    /**
     * 构建标准 JDBC 写入 SQL。
     *
     * @param instanceId 仿真实例 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param senderId 发送方 ID。
     * @return 标准写入 SQL。
     */
    public static String buildInsertUsingSql(String instanceId, int messageType, int messageCode, int senderId) {
        return String.format(INSERT_USING_SQL_TEMPLATE,
                buildSubTableName(instanceId, messageType, messageCode, senderId),
                buildStableName(instanceId));
    }

    /**
     * 构建 stmt 批量写入 SQL。
     *
     * @param instanceId 仿真实例 ID。
     * @return stmt 批量写入 SQL。
     */
    public static String buildInsertStmtSql(String instanceId) {
        return String.format(INSERT_STMT_SQL_TEMPLATE, buildStableName(instanceId));
    }

    /**
     * 将业务标识转换为 TDengine 可接受的对象名。
     *
     * @param identifier 原始标识。
     * @return 清洗后的标识。
     */
    public static String sanitizeIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            throw new IllegalArgumentException("标识不能为空");
        }
        return identifier.trim().replaceAll("[^0-9a-zA-Z_]", "_");
    }
}
