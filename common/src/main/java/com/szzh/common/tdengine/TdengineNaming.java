package com.szzh.common.tdengine;

/**
 * TDengine 命名规则。
 */
public final class TdengineNaming {

    public static final String STABLE_PREFIX = "situation_";

    public static final String TIME_CONTROL_PREFIX = "time_control_";

    private TdengineNaming() {
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
     * 构建控制时间点表名称。
     *
     * @param instanceId 仿真实例 ID。
     * @return 控制时间点表名称。
     */
    public static String buildTimeControlTableName(String instanceId) {
        return TIME_CONTROL_PREFIX + sanitizeIdentifier(instanceId);
    }

    /**
     * 将业务标识转换为 TDengine 可接受的对象名。
     *
     * @param identifier 原始标识。
     * @return 清洗后的标识。
     */
    public static String sanitizeIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("标识不能为空");
        }
        return identifier.trim().replaceAll("[^0-9a-zA-Z_]", "_");
    }
}
