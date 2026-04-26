package com.szzh.replayserver.mq;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.exception.ProtocolParseException;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 回放实例级广播控制消息处理器。
 */
@Component
public class ReplayInstanceBroadcastMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ReplayInstanceBroadcastMessageHandler.class);

    private final ReplayMessageConstants messageConstants;

    private ReplayControlCommandPort replayControlCommandPort;

    /**
     * 创建回放实例级广播控制消息处理器。
     *
     * @param messageConstants 回放消息常量。
     */
    @Autowired
    public ReplayInstanceBroadcastMessageHandler(ReplayMessageConstants messageConstants) {
        this(messageConstants, null);
    }

    /**
     * 创建回放实例级广播控制消息处理器。
     *
     * @param messageConstants 回放消息常量。
     * @param replayControlCommandPort 回放控制命令委派端口。
     */
    public ReplayInstanceBroadcastMessageHandler(ReplayMessageConstants messageConstants,
                                                 ReplayControlCommandPort replayControlCommandPort) {
        this.messageConstants = Objects.requireNonNull(messageConstants, "messageConstants 不能为空");
        this.replayControlCommandPort = replayControlCommandPort;
    }

    /**
     * 注入回放控制命令委派端口。
     *
     * @param replayControlCommandPort 回放控制命令委派端口。
     */
    @Autowired(required = false)
    public void setReplayControlCommandPort(ReplayControlCommandPort replayControlCommandPort) {
        this.replayControlCommandPort = replayControlCommandPort;
    }

    /**
     * 处理实例级回放控制消息。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     */
    public void handle(String instanceId, MessageExt messageExt) {
        ProtocolData protocolData;
        try {
            protocolData = parse(messageExt);
        } catch (ProtocolParseException exception) {
            log.warn("result=replay_protocol_parse_failed instanceId={} topic={} messageType=-1 messageCode=-1 senderId=-1 reason={}",
                    instanceId, resolveTopic(messageExt), exception.getMessage());
            return;
        }

        if (!messageConstants.isInstanceControlMessage(protocolData.getMessageType())) {
            return;
        }
        if (messageConstants.isInstanceMetadataMessage(protocolData.getMessageCode())) {
            return;
        }
        if (replayControlCommandPort == null) {
            log.debug("result=replay_control_port_missing instanceId={} topic={} messageType={} messageCode={} senderId={}",
                    instanceId, resolveTopic(messageExt), protocolData.getMessageType(),
                    protocolData.getMessageCode(), protocolData.getSenderId());
            return;
        }

        try {
            dispatchControlCommand(instanceId, messageExt, protocolData);
        } catch (BusinessException exception) {
            log.warn("result=replay_business_exception category={} instanceId={} topic={} messageType={} messageCode={} senderId={} reason={}",
                    exception.getCategory(), instanceId, resolveTopic(messageExt), protocolData.getMessageType(),
                    protocolData.getMessageCode(), protocolData.getSenderId(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("result=replay_unexpected_exception instanceId={} topic={} messageType={} messageCode={} senderId={} reason={}",
                    instanceId, resolveTopic(messageExt), protocolData.getMessageType(),
                    protocolData.getMessageCode(), protocolData.getSenderId(), exception.getMessage(), exception);
        }
    }

    /**
     * 分发已解析的控制命令。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     * @param protocolData 协议数据。
     */
    private void dispatchControlCommand(String instanceId, MessageExt messageExt, ProtocolData protocolData) {
        if (messageConstants.isInstanceStartMessage(protocolData.getMessageCode())) {
            replayControlCommandPort.handleStart(instanceId, protocolData);
            return;
        }
        if (messageConstants.isInstancePauseMessage(protocolData.getMessageCode())) {
            replayControlCommandPort.handlePause(instanceId, protocolData);
            return;
        }
        if (messageConstants.isInstanceResumeMessage(protocolData.getMessageCode())) {
            replayControlCommandPort.handleResume(instanceId, protocolData);
            return;
        }
        if (messageConstants.isInstanceRateMessage(protocolData.getMessageCode())) {
            replayControlCommandPort.handleRate(instanceId, protocolData);
            return;
        }
        if (messageConstants.isInstanceJumpMessage(protocolData.getMessageCode())) {
            replayControlCommandPort.handleJump(instanceId, protocolData);
            return;
        }

        log.debug("result=replay_ignored_unknown_control_message instanceId={} topic={} messageType={} messageCode={} senderId={}",
                instanceId, resolveTopic(messageExt), protocolData.getMessageType(),
                protocolData.getMessageCode(), protocolData.getSenderId());
    }

    /**
     * 从 RocketMQ 原始消息中解析平台协议数据。
     *
     * @param messageExt RocketMQ 原始消息。
     * @return 协议数据。
     */
    private ProtocolData parse(MessageExt messageExt) {
        if (messageExt == null) {
            throw new ProtocolParseException("RocketMQ 消息不能为空");
        }
        return ProtocolMessageUtil.parseData(messageExt.getBody());
    }

    /**
     * 解析日志中的 topic 名称。
     *
     * @param messageExt RocketMQ 原始消息。
     * @return topic 名称。
     */
    private String resolveTopic(MessageExt messageExt) {
        return messageExt == null ? "-" : messageExt.getTopic();
    }
}
