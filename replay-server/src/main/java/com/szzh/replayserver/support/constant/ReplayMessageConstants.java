package com.szzh.replayserver.support.constant;

import com.szzh.replayserver.config.ReplayServerProperties;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 回放协议消息配置快照。
 */
@Getter
@Component
public class ReplayMessageConstants {

    private final int globalMessageType;

    private final int globalCreateMessageCode;

    private final int globalStopMessageCode;

    private final int instanceControlMessageType;

    private final int instanceStartMessageCode;

    private final int instancePauseMessageCode;

    private final int instanceResumeMessageCode;

    private final int instanceRateMessageCode;

    private final int instanceJumpMessageCode;

    private final int instanceMetadataMessageCode;

    /**
     * 根据回放服务配置创建消息常量快照。
     *
     * @param properties 回放服务配置。
     */
    public ReplayMessageConstants(ReplayServerProperties properties) {
        Objects.requireNonNull(properties, "properties 不能为空");
        ReplayServerProperties.Messages messages = properties.getProtocol().getMessages();
        this.globalMessageType = messages.getGlobal().getMessageType();
        this.globalCreateMessageCode = messages.getGlobal().getCreateMessageCode();
        this.globalStopMessageCode = messages.getGlobal().getStopMessageCode();
        this.instanceControlMessageType = messages.getControl().getMessageType();
        this.instanceStartMessageCode = messages.getControl().getStartMessageCode();
        this.instancePauseMessageCode = messages.getControl().getPauseMessageCode();
        this.instanceResumeMessageCode = messages.getControl().getResumeMessageCode();
        this.instanceRateMessageCode = messages.getControl().getRateMessageCode();
        this.instanceJumpMessageCode = messages.getControl().getJumpMessageCode();
        this.instanceMetadataMessageCode = messages.getControl().getMetadataMessageCode();
    }

    /**
     * 判断是否为回放全局任务管理消息。
     *
     * @param messageType 消息类型。
     * @return 是否为回放全局任务管理消息。
     */
    public boolean isGlobalLifecycleMessage(int messageType) {
        return messageType == globalMessageType;
    }

    /**
     * 判断是否为回放创建消息码。
     *
     * @param messageCode 消息编号。
     * @return 是否为回放创建消息码。
     */
    public boolean isGlobalCreateMessage(int messageCode) {
        return messageCode == globalCreateMessageCode;
    }

    /**
     * 判断是否为回放停止消息码。
     *
     * @param messageCode 消息编号。
     * @return 是否为回放停止消息码。
     */
    public boolean isGlobalStopMessage(int messageCode) {
        return messageCode == globalStopMessageCode;
    }

    /**
     * 判断是否为实例级回放控制消息。
     *
     * @param messageType 消息类型。
     * @return 是否为实例级回放控制消息。
     */
    public boolean isInstanceControlMessage(int messageType) {
        return messageType == instanceControlMessageType;
    }

    /**
     * 判断是否为启动回放消息码。
     *
     * @param messageCode 消息编号。
     * @return 是否为启动回放消息码。
     */
    public boolean isInstanceStartMessage(int messageCode) {
        return messageCode == instanceStartMessageCode;
    }

    /**
     * 判断是否为暂停回放消息码。
     *
     * @param messageCode 消息编号。
     * @return 是否为暂停回放消息码。
     */
    public boolean isInstancePauseMessage(int messageCode) {
        return messageCode == instancePauseMessageCode;
    }

    /**
     * 判断是否为继续回放消息码。
     *
     * @param messageCode 消息编号。
     * @return 是否为继续回放消息码。
     */
    public boolean isInstanceResumeMessage(int messageCode) {
        return messageCode == instanceResumeMessageCode;
    }

    /**
     * 判断是否为倍速回放消息码。
     *
     * @param messageCode 消息编号。
     * @return 是否为倍速回放消息码。
     */
    public boolean isInstanceRateMessage(int messageCode) {
        return messageCode == instanceRateMessageCode;
    }

    /**
     * 判断是否为时间跳转消息码。
     *
     * @param messageCode 消息编号。
     * @return 是否为时间跳转消息码。
     */
    public boolean isInstanceJumpMessage(int messageCode) {
        return messageCode == instanceJumpMessageCode;
    }

    /**
     * 判断是否为回放元信息通知消息码。
     *
     * @param messageCode 消息编号。
     * @return 是否为回放元信息通知消息码。
     */
    public boolean isInstanceMetadataMessage(int messageCode) {
        return messageCode == instanceMetadataMessageCode;
    }
}
