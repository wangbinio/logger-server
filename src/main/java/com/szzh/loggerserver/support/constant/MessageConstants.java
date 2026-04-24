package com.szzh.loggerserver.support.constant;

/**
 * 协议消息常量定义。
 */
public final class MessageConstants {

    public static final int GLOBAL_MESSAGE_TYPE = 0;

    public static final int GLOBAL_CREATE_MESSAGE_CODE = 0;

    public static final int GLOBAL_STOP_MESSAGE_CODE = 1;

    public static final int INSTANCE_CONTROL_MESSAGE_TYPE = 1100;

    public static final int INSTANCE_START_MESSAGE_CODE = 1;

    public static final int INSTANCE_PAUSE_MESSAGE_CODE = 5;

    public static final int INSTANCE_RESUME_MESSAGE_CODE = 6;

    private MessageConstants() {
    }

    /**
     * 判断是否为全局任务管理消息。
     *
     * @param messageType 消息类型。
     * @return 是否为全局消息。
     */
    public static boolean isGlobalLifecycleMessage(int messageType) {
        return messageType == GLOBAL_MESSAGE_TYPE;
    }

    /**
     * 判断是否为实例控制消息。
     *
     * @param messageType 消息类型。
     * @return 是否为实例控制消息。
     */
    public static boolean isInstanceControlMessage(int messageType) {
        return messageType == INSTANCE_CONTROL_MESSAGE_TYPE;
    }
}
