package com.szzh.replayserver.service;

import com.szzh.common.protocol.ProtocolData;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 回放 HTTP 控制命令适配器。
 */
@Component
public class ReplayHttpCommandFactory {

    private static final int REPLAY_HTTP_SENDER_ID = 0;

    private static final String EMPTY_JSON = "{}";

    private final ReplayMessageConstants messageConstants;

    /**
     * 创建回放 HTTP 控制命令适配器。
     *
     * @param messageConstants 回放消息常量。
     */
    public ReplayHttpCommandFactory(ReplayMessageConstants messageConstants) {
        this.messageConstants = Objects.requireNonNull(messageConstants, "messageConstants 不能为空");
    }

    /**
     * 创建启动回放命令。
     *
     * @return 启动回放命令。
     */
    public ProtocolData startCommand() {
        return emptyCommand(messageConstants.getInstanceStartMessageCode());
    }

    /**
     * 创建暂停回放命令。
     *
     * @return 暂停回放命令。
     */
    public ProtocolData pauseCommand() {
        return emptyCommand(messageConstants.getInstancePauseMessageCode());
    }

    /**
     * 创建继续回放命令。
     *
     * @return 继续回放命令。
     */
    public ProtocolData resumeCommand() {
        return emptyCommand(messageConstants.getInstanceResumeMessageCode());
    }

    /**
     * 创建倍速回放命令。
     *
     * @param rate 回放倍率。
     * @return 倍速回放命令。
     */
    public ProtocolData rateCommand(double rate) {
        return command(messageConstants.getInstanceRateMessageCode(), "{\"rate\":" + Double.toString(rate) + "}");
    }

    /**
     * 创建时间跳转命令。
     *
     * @param time 目标回放时间。
     * @return 时间跳转命令。
     */
    public ProtocolData jumpCommand(long time) {
        return command(messageConstants.getInstanceJumpMessageCode(), "{\"time\":" + Long.toString(time) + "}");
    }

    /**
     * 创建无业务载荷控制命令。
     *
     * @param messageCode 消息编号。
     * @return 控制命令。
     */
    private ProtocolData emptyCommand(int messageCode) {
        return command(messageCode, EMPTY_JSON);
    }

    /**
     * 创建控制命令。
     *
     * @param messageCode 消息编号。
     * @param rawJson 原始 JSON 字符串。
     * @return 控制命令。
     */
    private ProtocolData command(int messageCode, String rawJson) {
        ProtocolData protocolData = new ProtocolData();
        protocolData.setSenderId(REPLAY_HTTP_SENDER_ID);
        protocolData.setMessageType(messageConstants.getInstanceControlMessageType());
        protocolData.setMessageCode(messageCode);
        protocolData.setRawData(rawJson.getBytes(StandardCharsets.UTF_8));
        return protocolData;
    }
}
