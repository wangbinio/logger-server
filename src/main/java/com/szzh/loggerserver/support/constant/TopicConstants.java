package com.szzh.loggerserver.support.constant;

import org.springframework.util.StringUtils;

/**
 * Topic 常量定义。
 */
public final class TopicConstants {

    public static final String GLOBAL_BROADCAST_TOPIC = "broadcast-global";

    public static final String INSTANCE_BROADCAST_PREFIX = "broadcast-";

    public static final String INSTANCE_SITUATION_PREFIX = "situation-";

    private TopicConstants() {
    }

    /**
     * 构建实例控制 topic。
     *
     * @param instanceId 仿真实例 ID。
     * @return 实例控制 topic。
     */
    public static String buildInstanceBroadcastTopic(String instanceId) {
        return INSTANCE_BROADCAST_PREFIX + requireInstanceId(instanceId);
    }

    /**
     * 构建实例态势 topic。
     *
     * @param instanceId 仿真实例 ID。
     * @return 实例态势 topic。
     */
    public static String buildInstanceSituationTopic(String instanceId) {
        return INSTANCE_SITUATION_PREFIX + requireInstanceId(instanceId);
    }

    /**
     * 校验实例 ID 非空。
     *
     * @param instanceId 仿真实例 ID。
     * @return 原始实例 ID。
     */
    private static String requireInstanceId(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        return instanceId.trim();
    }
}
