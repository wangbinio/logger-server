package com.szzh.replayserver.mq;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.exception.ProtocolParseException;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 回放全局广播监听器。
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.GLOBAL_BROADCAST_TOPIC,
        consumerGroup = "${replay-server.rocketmq.global-consumer-group}",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING)
@ConditionalOnProperty(
        prefix = "replay-server.rocketmq",
        name = "enable-global-listener",
        havingValue = "true",
        matchIfMissing = true)
public class ReplayGlobalBroadcastListener implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(ReplayGlobalBroadcastListener.class);

    private final ReplayMessageConstants messageConstants;

    private ReplayLifecycleCommandPort replayLifecycleCommandPort;

    /**
     * 创建回放全局广播监听器。
     *
     * @param messageConstants 回放消息常量。
     */
    @Autowired
    public ReplayGlobalBroadcastListener(ReplayMessageConstants messageConstants) {
        this.messageConstants = Objects.requireNonNull(messageConstants, "messageConstants 不能为空");
    }

    /**
     * 注入回放生命周期命令委派端口。
     *
     * @param replayLifecycleCommandPort 回放生命周期命令委派端口。
     */
    @Autowired(required = false)
    public void setReplayLifecycleCommandPort(ReplayLifecycleCommandPort replayLifecycleCommandPort) {
        this.replayLifecycleCommandPort = replayLifecycleCommandPort;
    }

    /**
     * 处理全局广播消息。
     *
     * @param messageExt RocketMQ 原始消息。
     */
    @Override
    public void onMessage(MessageExt messageExt) {
        ProtocolData protocolData;
        try {
            protocolData = parse(messageExt);
        } catch (ProtocolParseException exception) {
            log.warn("result=replay_protocol_parse_failed instanceId=- topic={} messageType=-1 messageCode=-1 senderId=-1 reason={}",
                    resolveTopic(messageExt), exception.getMessage());
            return;
        }

        if (!messageConstants.isGlobalLifecycleMessage(protocolData.getMessageType())) {
            return;
        }
        if (replayLifecycleCommandPort == null) {
            log.debug("result=replay_lifecycle_port_missing instanceId=- topic={} messageType={} messageCode={} senderId={}",
                    TopicConstants.GLOBAL_BROADCAST_TOPIC, protocolData.getMessageType(),
                    protocolData.getMessageCode(), protocolData.getSenderId());
            return;
        }

        try {
            if (messageConstants.isGlobalCreateMessage(protocolData.getMessageCode())) {
                replayLifecycleCommandPort.handleCreate(protocolData);
                return;
            }
            if (messageConstants.isGlobalStopMessage(protocolData.getMessageCode())) {
                replayLifecycleCommandPort.handleStop(protocolData);
                return;
            }

            log.debug("result=replay_ignored_unknown_global_message instanceId=- topic={} messageType={} messageCode={} senderId={}",
                    TopicConstants.GLOBAL_BROADCAST_TOPIC, protocolData.getMessageType(),
                    protocolData.getMessageCode(), protocolData.getSenderId());
        } catch (BusinessException exception) {
            log.warn("result=replay_business_exception category={} instanceId=- topic={} messageType={} messageCode={} senderId={} reason={}",
                    exception.getCategory(), TopicConstants.GLOBAL_BROADCAST_TOPIC, protocolData.getMessageType(),
                    protocolData.getMessageCode(), protocolData.getSenderId(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("result=replay_unexpected_exception instanceId=- topic={} messageType={} messageCode={} senderId={} reason={}",
                    TopicConstants.GLOBAL_BROADCAST_TOPIC, protocolData.getMessageType(),
                    protocolData.getMessageCode(), protocolData.getSenderId(), exception.getMessage(), exception);
        }
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
        return messageExt == null ? TopicConstants.GLOBAL_BROADCAST_TOPIC : messageExt.getTopic();
    }
}
