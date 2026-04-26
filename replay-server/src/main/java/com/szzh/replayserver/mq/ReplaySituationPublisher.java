package com.szzh.replayserver.mq;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.support.metric.ReplayMetrics;
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

    private final ReplayMetrics replayMetrics;

    /**
     * 创建回放态势发布器。
     *
     * @param rocketMqSender RocketMQ 发送端口。
     * @param properties 回放服务配置。
     * @param replayMetrics 回放指标。
     */
    @Autowired
    public ReplaySituationPublisher(ReplayRocketMqSender rocketMqSender,
                                    ReplayServerProperties properties,
                                    ReplayMetrics replayMetrics) {
        this(rocketMqSender, properties.getReplay().getPublish().getRetryTimes(), replayMetrics);
    }

    /**
     * 创建回放态势发布器。
     *
     * @param rocketMqSender RocketMQ 发送端口。
     * @param retryTimes 重试次数。
     */
    public ReplaySituationPublisher(ReplayRocketMqSender rocketMqSender, int retryTimes) {
        this(rocketMqSender, retryTimes, new ReplayMetrics());
    }

    /**
     * 创建回放态势发布器。
     *
     * @param rocketMqSender RocketMQ 发送端口。
     * @param retryTimes 重试次数。
     * @param replayMetrics 回放指标。
     */
    public ReplaySituationPublisher(ReplayRocketMqSender rocketMqSender,
                                    int retryTimes,
                                    ReplayMetrics replayMetrics) {
        this.rocketMqSender = Objects.requireNonNull(rocketMqSender, "rocketMqSender 不能为空");
        this.retryTimes = Math.max(1, retryTimes);
        this.replayMetrics = Objects.requireNonNull(replayMetrics, "replayMetrics 不能为空");
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
                // 发送重组后的回放协议包。
                rocketMqSender.send(topic, body);

                replayMetrics.recordPublishSuccess();
                return;
            } catch (RuntimeException exception) {
                lastException = exception;

                log.warn("result=replay_publish_retry_failed instanceId={} topic={} messageType={} messageCode={} senderId={} currentReplayTime={} lastDispatchedSimTime=-1 rate=-1 simtime={} attempt={} reason={}",
                        instanceId, topic, replayFrame.getMessageType(), replayFrame.getMessageCode(), replayFrame.getSenderId(), replayFrame.getSimTime(), replayFrame.getSimTime(), attempt + 1, exception.getMessage());
            }
        }
        replayMetrics.recordPublishFailure();
        throw new BusinessException(BusinessException.Category.STATE, "RocketMQ 回放态势发布失败", lastException);
    }
}
