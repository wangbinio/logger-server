package com.szzh.replayserver.mq;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.model.query.ReplayFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 回放态势发布器。
 */
@Component
public class ReplaySituationPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReplaySituationPublisher.class);

    private final ReplayRocketMqSender rocketMqSender;

    private final int retryTimes;

    /**
     * 创建回放态势发布器。
     *
     * @param rocketMqSender RocketMQ 发送端口。
     * @param properties 回放服务配置。
     */
    @Autowired
    public ReplaySituationPublisher(ReplayRocketMqSender rocketMqSender, ReplayServerProperties properties) {
        this(rocketMqSender, properties.getReplay().getPublish().getRetryTimes());
    }

    /**
     * 创建回放态势发布器。
     *
     * @param rocketMqSender RocketMQ 发送端口。
     * @param retryTimes 重试次数。
     */
    public ReplaySituationPublisher(ReplayRocketMqSender rocketMqSender, int retryTimes) {
        this.rocketMqSender = Objects.requireNonNull(rocketMqSender, "rocketMqSender 不能为空");
        this.retryTimes = Math.max(1, retryTimes);
    }

    /**
     * 发布单帧回放态势消息。
     *
     * @param instanceId 实例 ID。
     * @param frame 回放帧。
     */
    public void publish(String instanceId, ReplayFrame frame) {
        ReplayFrame replayFrame = Objects.requireNonNull(frame, "frame 不能为空");
        String topic = TopicConstants.buildInstanceSituationTopic(instanceId);
        byte[] body = ProtocolMessageUtil.buildData(
                replayFrame.getSenderId(),
                (short) replayFrame.getMessageType(),
                replayFrame.getMessageCode(),
                replayFrame.getRawData());

        RuntimeException lastException = null;
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            try {
                rocketMqSender.send(topic, body);
                return;
            } catch (RuntimeException exception) {
                lastException = exception;
                log.warn("result=replay_publish_retry_failed instanceId={} topic={} messageType={} messageCode={} senderId={} simtime={} attempt={} reason={}",
                        instanceId, topic, replayFrame.getMessageType(), replayFrame.getMessageCode(), replayFrame.getSenderId(), replayFrame.getSimTime(), attempt + 1, exception.getMessage());
            }
        }
        throw new BusinessException(BusinessException.Category.STATE, "RocketMQ 回放态势发布失败", lastException);
    }
}
