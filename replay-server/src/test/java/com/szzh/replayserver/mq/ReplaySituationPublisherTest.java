package com.szzh.replayserver.mq;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * 回放态势发布器测试。
 */
class ReplaySituationPublisherTest {

    /**
     * 验证发布器使用实例态势 topic，并按帧元数据重组协议包。
     */
    @Test
    void shouldPublishFrameToSituationTopicWithRebuiltProtocolBody() {
        ReplayRocketMqSender sender = Mockito.mock(ReplayRocketMqSender.class);
        ReplayMetrics metrics = new ReplayMetrics();
        ReplaySituationPublisher publisher = new ReplaySituationPublisher(sender, 1, metrics);
        ReplayFrame frame = new ReplayFrame("situation_1001_2_7_instance_001",
                7, 1001, 2, 1_200L, new byte[]{1, 2, 3});

        publisher.publish("instance-001", frame);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(sender).send(topicCaptor.capture(), bodyCaptor.capture());
        ProtocolData protocolData = ProtocolMessageUtil.parseData(bodyCaptor.getValue());
        Assertions.assertEquals(TopicConstants.buildInstanceSituationTopic("instance-001"), topicCaptor.getValue());
        Assertions.assertEquals(7, protocolData.getSenderId());
        Assertions.assertEquals(1001, protocolData.getMessageType());
        Assertions.assertEquals(2, protocolData.getMessageCode());
        Assertions.assertArrayEquals(new byte[]{1, 2, 3}, protocolData.getRawData());
        Assertions.assertEquals(1L, metrics.publishedSuccessCount());
        Assertions.assertEquals(0L, metrics.publishedFailureCount());
    }

    /**
     * 验证首次发送失败后会按配置重试。
     */
    @Test
    void shouldRetryWhenSendFailsOnce() {
        ReplayRocketMqSender sender = Mockito.mock(ReplayRocketMqSender.class);
        Mockito.doThrow(new IllegalStateException("send boom"))
                .doNothing()
                .when(sender)
                .send(Mockito.anyString(), Mockito.any(byte[].class));
        ReplaySituationPublisher publisher = new ReplaySituationPublisher(sender, 2);

        publisher.publish("instance-001", new ReplayFrame("table_a", 7, 1001, 2, 1_200L, new byte[]{1}));

        Mockito.verify(sender, Mockito.times(2)).send(Mockito.anyString(), Mockito.any(byte[].class));
    }

    /**
     * 验证重试耗尽后抛出业务异常。
     */
    @Test
    void shouldThrowBusinessExceptionWhenRetryExhausted() {
        ReplayRocketMqSender sender = Mockito.mock(ReplayRocketMqSender.class);
        ReplayMetrics metrics = new ReplayMetrics();
        Mockito.doThrow(new IllegalStateException("send boom"))
                .when(sender)
                .send(Mockito.anyString(), Mockito.any(byte[].class));
        ReplaySituationPublisher publisher = new ReplaySituationPublisher(sender, 3, metrics);

        Assertions.assertThrows(BusinessException.class,
                () -> publisher.publish("instance-001",
                        new ReplayFrame("table_a", 7, 1001, 2, 1_200L, new byte[]{1})));
        Mockito.verify(sender, Mockito.times(3)).send(Mockito.anyString(), Mockito.any(byte[].class));
        Assertions.assertEquals(0L, metrics.publishedSuccessCount());
        Assertions.assertEquals(1L, metrics.publishedFailureCount());
    }
}
