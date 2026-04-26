package com.szzh.replayserver.service;

import com.szzh.common.json.JsonUtil;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.model.dto.ReplayMetadataPayload;
import com.szzh.replayserver.mq.ReplayRocketMqSender;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 回放元信息服务。
 */
@Service
public class ReplayMetadataService {

    private static final int REPLAY_SERVER_SENDER_ID = 0;

    private final ReplayRocketMqSender rocketMqSender;

    private final ReplayMessageConstants messageConstants;

    /**
     * 创建回放元信息服务。
     *
     * @param rocketMqSender RocketMQ 发送端口。
     * @param messageConstants 回放消息常量。
     */
    public ReplayMetadataService(ReplayRocketMqSender rocketMqSender,
                                 ReplayMessageConstants messageConstants) {
        this.rocketMqSender = Objects.requireNonNull(rocketMqSender, "rocketMqSender 不能为空");
        this.messageConstants = Objects.requireNonNull(messageConstants, "messageConstants 不能为空");
    }

    /**
     * 发布回放元信息通知。
     *
     * @param session 回放会话。
     */
    public void publishMetadata(ReplaySession session) {
        ReplaySession replaySession = Objects.requireNonNull(session, "session 不能为空");
        ReplayMetadataPayload payload = new ReplayMetadataPayload(
                replaySession.getSimulationStartTime(),
                replaySession.getSimulationEndTime());
        byte[] rawData = JsonUtil.toUtf8Bytes(JsonUtil.toJson(payload));
        byte[] body = ProtocolMessageUtil.buildData(
                REPLAY_SERVER_SENDER_ID,
                (short) messageConstants.getInstanceControlMessageType(),
                messageConstants.getInstanceMetadataMessageCode(),
                rawData);
        rocketMqSender.send(TopicConstants.buildInstanceBroadcastTopic(replaySession.getInstanceId()), body);
    }
}
