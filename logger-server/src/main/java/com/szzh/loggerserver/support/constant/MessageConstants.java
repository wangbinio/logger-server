package com.szzh.loggerserver.support.constant;

import com.szzh.loggerserver.config.LoggerServerProperties;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 协议消息配置快照。
 */
@Getter
@Component
public class MessageConstants {

    /**
     * -- GETTER --
     *  获取全局生命周期消息类型。
     *
     * @return 全局生命周期消息类型。
     */
    private final int globalMessageType;

    /**
     * -- GETTER --
     *  获取全局创建消息码。
     *
     * @return 全局创建消息码。
     */
    private final int globalCreateMessageCode;

    /**
     * -- GETTER --
     *  获取全局停止消息码。
     *
     * @return 全局停止消息码。
     */
    private final int globalStopMessageCode;

    /**
     * -- GETTER --
     *  获取实例控制消息类型。
     *
     * @return 实例控制消息类型。
     */
    private final int instanceControlMessageType;

    /**
     * -- GETTER --
     *  获取实例启动消息码。
     *
     * @return 实例启动消息码。
     */
    private final int instanceStartMessageCode;

    /**
     * -- GETTER --
     *  获取实例暂停消息码。
     *
     * @return 实例暂停消息码。
     */
    private final int instancePauseMessageCode;

    /**
     * -- GETTER --
     *  获取实例继续消息码。
     *
     * @return 实例继续消息码。
     */
    private final int instanceResumeMessageCode;

    /**
     * 根据项目配置创建消息常量快照。
     *
     * @param properties 项目配置。
     */
    public MessageConstants(LoggerServerProperties properties) {
        Objects.requireNonNull(properties, "properties 不能为空");
        LoggerServerProperties.Messages messages = properties.getProtocol().getMessages();
        this.globalMessageType = messages.getGlobal().getMessageType();
        this.globalCreateMessageCode = messages.getGlobal().getCreateMessageCode();
        this.globalStopMessageCode = messages.getGlobal().getStopMessageCode();
        this.instanceControlMessageType = messages.getControl().getMessageType();
        this.instanceStartMessageCode = messages.getControl().getStartMessageCode();
        this.instancePauseMessageCode = messages.getControl().getPauseMessageCode();
        this.instanceResumeMessageCode = messages.getControl().getResumeMessageCode();
    }

    /**
     * 判断是否为全局任务管理消息。
     *
     * @param messageType 消息类型。
     * @return 是否为全局消息。
     */
    public boolean isGlobalLifecycleMessage(int messageType) {
        return messageType == globalMessageType;
    }

    /**
     * 判断是否为实例控制消息。
     *
     * @param messageType 消息类型。
     * @return 是否为实例控制消息。
     */
    public boolean isInstanceControlMessage(int messageType) {
        return messageType == instanceControlMessageType;
    }
}
