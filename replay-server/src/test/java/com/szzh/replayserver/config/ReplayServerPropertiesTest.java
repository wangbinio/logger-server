package com.szzh.replayserver.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回放服务配置绑定测试。
 */
class ReplayServerPropertiesTest {

    /**
     * 验证回放服务默认配置与消息隔离设计一致。
     */
    @Test
    void shouldUseReplayDefaults() {
        ReplayServerProperties properties = new ReplayServerProperties();

        Assertions.assertEquals("com.taosdata.jdbc.ws.WebSocketDriver",
                properties.getTdengine().getDriverClassName());
        Assertions.assertEquals(102400, properties.getProtocol().getMaxPayloadSize());
        Assertions.assertEquals(1, properties.getProtocol().getMessages().getGlobal().getMessageType());
        Assertions.assertEquals(1200, properties.getProtocol().getMessages().getControl().getMessageType());
        Assertions.assertEquals(9, properties.getProtocol().getMessages().getControl().getMetadataMessageCode());
        Assertions.assertEquals("replay-global-consumer", properties.getRocketmq().getGlobalConsumerGroup());
        Assertions.assertEquals("replay-instance", properties.getRocketmq().getInstanceConsumerGroupPrefix());
        Assertions.assertEquals("replay-producer", properties.getRocketmq().getProducerGroup());
        Assertions.assertTrue(properties.getRocketmq().isEnableGlobalListener());
        Assertions.assertEquals(1000, properties.getReplay().getQuery().getPageSize());
        Assertions.assertEquals(50L, properties.getReplay().getScheduler().getTickMillis());
        Assertions.assertEquals(500, properties.getReplay().getPublish().getBatchSize());
        Assertions.assertEquals(3, properties.getReplay().getPublish().getRetryTimes());
    }

    /**
     * 验证 YAML 风格配置能绑定到回放服务配置模型。
     */
    @Test
    void shouldBindReplayPropertiesFromConfiguration() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("replay-server.tdengine.jdbc-url", "jdbc:TAOS-WS://example/logger");
        values.put("replay-server.tdengine.username", "root");
        values.put("replay-server.tdengine.password", "taosdata");
        values.put("replay-server.rocketmq.global-consumer-group", "custom-global");
        values.put("replay-server.rocketmq.instance-consumer-group-prefix", "custom-instance");
        values.put("replay-server.protocol.messages.global.message-type", "11");
        values.put("replay-server.protocol.messages.control.message-type", "2200");
        values.put("replay-server.protocol.messages.control.rate-message-code", "44");
        values.put("replay-server.replay.query.page-size", "200");

        ReplayServerProperties properties = new Binder(new MapConfigurationPropertySource(values))
                .bind("replay-server", Bindable.of(ReplayServerProperties.class))
                .orElseThrow(IllegalStateException::new);

        Assertions.assertEquals("jdbc:TAOS-WS://example/logger", properties.getTdengine().getJdbcUrl());
        Assertions.assertEquals("root", properties.getTdengine().getUsername());
        Assertions.assertEquals("taosdata", properties.getTdengine().getPassword());
        Assertions.assertEquals("custom-global", properties.getRocketmq().getGlobalConsumerGroup());
        Assertions.assertEquals("custom-instance", properties.getRocketmq().getInstanceConsumerGroupPrefix());
        Assertions.assertEquals(11, properties.getProtocol().getMessages().getGlobal().getMessageType());
        Assertions.assertEquals(2200, properties.getProtocol().getMessages().getControl().getMessageType());
        Assertions.assertEquals(44, properties.getProtocol().getMessages().getControl().getRateMessageCode());
        Assertions.assertEquals(200, properties.getReplay().getQuery().getPageSize());
    }
}
