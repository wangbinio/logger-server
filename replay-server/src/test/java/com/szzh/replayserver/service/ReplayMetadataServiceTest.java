package com.szzh.replayserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.szzh.common.json.JsonUtil;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import com.szzh.replayserver.mq.ReplayRocketMqSender;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放元信息服务测试。
 */
class ReplayMetadataServiceTest {

    /**
     * 验证创建任务成功后向实例广播 topic 发布元信息通知。
     */
    @Test
    void shouldPublishMetadataToInstanceBroadcastTopic() {
        ReplayRocketMqSender sender = Mockito.mock(ReplayRocketMqSender.class);
        ReplayMessageConstants constants = new ReplayMessageConstants(new ReplayServerProperties());
        ReplayMetadataService metadataService = new ReplayMetadataService(sender, constants);
        ReplaySession session = new ReplaySession(
                "instance-001",
                new ReplayTimeRange(1_000L, 2_500L),
                Collections.emptyList(),
                Collections.emptyList(),
                new ReplayClock(1_000L, 2_500L, new AtomicLong(1_000L)::get));

        metadataService.publishMetadata(session);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(sender).send(topicCaptor.capture(), bodyCaptor.capture());
        ProtocolData protocolData = ProtocolMessageUtil.parseData(bodyCaptor.getValue());
        JsonNode payload = JsonUtil.readTree(protocolData.getRawData());
        Assertions.assertEquals(TopicConstants.buildInstanceBroadcastTopic("instance-001"), topicCaptor.getValue());
        Assertions.assertEquals(constants.getInstanceControlMessageType(), protocolData.getMessageType());
        Assertions.assertEquals(constants.getInstanceMetadataMessageCode(), protocolData.getMessageCode());
        Assertions.assertEquals(1_000L, payload.get("startTime").asLong());
        Assertions.assertEquals(2_500L, payload.get("endTime").asLong());
        Assertions.assertEquals(1_500L, payload.get("duration").asLong());
    }
}
