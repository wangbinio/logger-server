package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.config.RocketMqConsumerFactory;
import com.szzh.loggerserver.config.LoggerServerProperties;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Topic 订阅管理测试。
 */
class TopicSubscriptionManagerTest {

    private final MessageConstants defaultMessageConstants = new MessageConstants(new LoggerServerProperties());

    /**
     * 验证重复订阅同一实例时只会真正创建一组消费者。
     */
    @Test
    void shouldSubscribeIdempotently() throws Exception {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        sessionManager.createSession("instance-001");
        RocketMqConsumerFactory consumerFactory = Mockito.mock(RocketMqConsumerFactory.class);
        DefaultMQPushConsumer broadcastConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumer situationConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        InstanceBroadcastMessageHandler instanceHandler =
                new InstanceBroadcastMessageHandler(defaultMessageConstants, new NoopSimulationControlCommandPort());
        SituationMessageHandler situationHandler =
                new SituationMessageHandler(new NoopSituationRecordIngressPort());
        TopicSubscriptionManager subscriptionManager = new TopicSubscriptionManager(
                sessionManager,
                consumerFactory,
                instanceHandler,
                situationHandler);

        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(broadcastConsumer);
        Mockito.when(consumerFactory.createSituationConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(situationConsumer);

        Assertions.assertTrue(subscriptionManager.subscribe("instance-001"));
        Assertions.assertFalse(subscriptionManager.subscribe("instance-001"));
        Assertions.assertTrue(subscriptionManager.isSubscribed("instance-001"));
        Assertions.assertEquals(1, subscriptionManager.size());
        Assertions.assertSame(broadcastConsumer,
                sessionManager.requireSession("instance-001").getBroadcastConsumerHandle());
        Assertions.assertSame(situationConsumer,
                sessionManager.requireSession("instance-001").getSituationConsumerHandle());
        Mockito.verify(consumerFactory, Mockito.times(1))
                .createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any());
        Mockito.verify(consumerFactory, Mockito.times(1))
                .createSituationConsumer(Mockito.eq("instance-001"), Mockito.any());
        Mockito.verify(broadcastConsumer, Mockito.times(1)).start();
        Mockito.verify(situationConsumer, Mockito.times(1)).start();
    }

    /**
     * 验证重复取消订阅时保持幂等。
     */
    @Test
    void shouldUnsubscribeIdempotently() throws Exception {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        sessionManager.createSession("instance-001");
        RocketMqConsumerFactory consumerFactory = Mockito.mock(RocketMqConsumerFactory.class);
        DefaultMQPushConsumer broadcastConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumer situationConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        TopicSubscriptionManager subscriptionManager = new TopicSubscriptionManager(
                sessionManager,
                consumerFactory,
                new InstanceBroadcastMessageHandler(defaultMessageConstants, new NoopSimulationControlCommandPort()),
                new SituationMessageHandler(new NoopSituationRecordIngressPort()));

        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(broadcastConsumer);
        Mockito.when(consumerFactory.createSituationConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(situationConsumer);

        subscriptionManager.subscribe("instance-001");

        Assertions.assertTrue(subscriptionManager.unsubscribe("instance-001"));
        Assertions.assertFalse(subscriptionManager.unsubscribe("instance-001"));
        Assertions.assertFalse(subscriptionManager.isSubscribed("instance-001"));
        Assertions.assertEquals(0, subscriptionManager.size());
        Assertions.assertNull(sessionManager.requireSession("instance-001").getBroadcastConsumerHandle());
        Assertions.assertNull(sessionManager.requireSession("instance-001").getSituationConsumerHandle());
        Mockito.verify(broadcastConsumer, Mockito.times(1)).shutdown();
        Mockito.verify(situationConsumer, Mockito.times(1)).shutdown();
    }

    /**
     * 验证会话不存在时拒绝订阅。
     */
    @Test
    void shouldRejectSubscribeWhenSessionMissing() {
        TopicSubscriptionManager subscriptionManager = new TopicSubscriptionManager(
                new SimulationSessionManager(),
                Mockito.mock(RocketMqConsumerFactory.class),
                new InstanceBroadcastMessageHandler(defaultMessageConstants, new NoopSimulationControlCommandPort()),
                new SituationMessageHandler(new NoopSituationRecordIngressPort()));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> subscriptionManager.subscribe("missing-instance"));
    }

    /**
     * 验证启动异常时会回收已创建的消费者句柄。
     */
    @Test
    void shouldCleanupConsumersWhenSubscribeFails() throws Exception {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        sessionManager.createSession("instance-001");
        RocketMqConsumerFactory consumerFactory = Mockito.mock(RocketMqConsumerFactory.class);
        DefaultMQPushConsumer broadcastConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumer situationConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        TopicSubscriptionManager subscriptionManager = new TopicSubscriptionManager(
                sessionManager,
                consumerFactory,
                new InstanceBroadcastMessageHandler(defaultMessageConstants, new NoopSimulationControlCommandPort()),
                new SituationMessageHandler(new NoopSituationRecordIngressPort()));

        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(broadcastConsumer);
        Mockito.when(consumerFactory.createSituationConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(situationConsumer);
        Mockito.doThrow(new MQClientException(500, "boom")).when(situationConsumer).start();

        Assertions.assertThrows(IllegalStateException.class,
                () -> subscriptionManager.subscribe("instance-001"));
        Assertions.assertFalse(subscriptionManager.isSubscribed("instance-001"));
        Assertions.assertEquals(0, subscriptionManager.size());
        Assertions.assertNull(sessionManager.requireSession("instance-001").getBroadcastConsumerHandle());
        Assertions.assertNull(sessionManager.requireSession("instance-001").getSituationConsumerHandle());
        Mockito.verify(broadcastConsumer, Mockito.times(1)).shutdown();
        Mockito.verify(situationConsumer, Mockito.times(1)).shutdown();
    }

    /**
     * 验证真实 RocketMQ 环境下可完成实例级动态订阅和消息投递。
     */
    @Test
    void shouldConsumeMessagesWithRealRocketMq() throws Exception {
        Properties properties = loadApplicationProperties();
        RocketMQProperties rocketMQProperties = buildRocketMqProperties(properties);
        LoggerServerProperties loggerServerProperties = buildLoggerServerProperties(properties);
        MessageConstants messageConstants = new MessageConstants(loggerServerProperties);
        RocketMqConsumerFactory consumerFactory =
                new RocketMqConsumerFactory(rocketMQProperties, loggerServerProperties);
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        String instanceId = "it-" + System.currentTimeMillis();
        CountDownLatch controlLatch = new CountDownLatch(1);
        CountDownLatch situationLatch = new CountDownLatch(1);
        AtomicReference<ProtocolData> controlDataRef = new AtomicReference<ProtocolData>();
        AtomicReference<ProtocolData> situationDataRef = new AtomicReference<ProtocolData>();
        TopicSubscriptionManager subscriptionManager = new TopicSubscriptionManager(
                sessionManager,
                consumerFactory,
                new InstanceBroadcastMessageHandler(messageConstants, new RecordingSimulationControlCommandPort(
                        controlLatch,
                        controlDataRef)),
                new SituationMessageHandler(new RecordingSituationRecordIngressPort(
                        situationLatch,
                        situationDataRef)));
        DefaultMQProducer producer = createProducer(rocketMQProperties.getNameServer());

        try {
            sessionManager.createSession(instanceId);
            ensureTopicExistsOrSkip(producer, TopicConstants.buildInstanceBroadcastTopic(instanceId));
            ensureTopicExistsOrSkip(producer, TopicConstants.buildInstanceSituationTopic(instanceId));
            TimeUnit.SECONDS.sleep(1);
            try {
                Assertions.assertTrue(subscriptionManager.subscribe(instanceId));
            } catch (IllegalStateException exception) {
                Assumptions.assumeTrue(false,
                        "真实 RocketMQ 环境当前不可用于动态订阅，通常是 broker 对外注册地址不可达: " + exception.getMessage());
            }
            TimeUnit.SECONDS.sleep(2);

            producer.send(new Message(
                    TopicConstants.buildInstanceBroadcastTopic(instanceId),
                    ProtocolMessageUtil.buildData(
                            101,
                            (short) messageConstants.getInstanceControlMessageType(),
                            messageConstants.getInstanceStartMessageCode(),
                            "start".getBytes(StandardCharsets.UTF_8))));
            producer.send(new Message(
                    TopicConstants.buildInstanceSituationTopic(instanceId),
                    ProtocolMessageUtil.buildData(
                            102,
                            (short) 2100,
                            7,
                            "situation".getBytes(StandardCharsets.UTF_8))));

            Assertions.assertTrue(controlLatch.await(15, TimeUnit.SECONDS), "实例控制消息未在预期时间内消费");
            Assertions.assertTrue(situationLatch.await(15, TimeUnit.SECONDS), "态势消息未在预期时间内消费");
            Assertions.assertEquals(messageConstants.getInstanceControlMessageType(),
                    Objects.requireNonNull(controlDataRef.get()).getMessageType());
            Assertions.assertEquals(messageConstants.getInstanceStartMessageCode(), controlDataRef.get().getMessageCode());
            Assertions.assertEquals(2100, Objects.requireNonNull(situationDataRef.get()).getMessageType());
            Assertions.assertEquals(7, situationDataRef.get().getMessageCode());
        } finally {
            subscriptionManager.unsubscribe(instanceId);
            producer.shutdown();
        }
    }

    /**
     * 加载应用配置文件。
     *
     * @return 配置属性。
     */
    private Properties loadApplicationProperties() {
        YamlPropertiesFactoryBean yamlPropertiesFactoryBean = new YamlPropertiesFactoryBean();
        yamlPropertiesFactoryBean.setResources(
                new ClassPathResource("application.yml"),
                new ClassPathResource("application-dev.yml"));
        Properties properties = yamlPropertiesFactoryBean.getObject();
        Assertions.assertNotNull(properties, "未能加载 application 配置");
        return properties;
    }

    /**
     * 构建 RocketMQ 配置对象。
     *
     * @param properties 原始配置属性。
     * @return RocketMQ 配置对象。
     */
    private RocketMQProperties buildRocketMqProperties(Properties properties) {
        Binder binder = new Binder(new MapConfigurationPropertySource(toMap(properties)));
        RocketMQProperties rocketMQProperties = binder.bind("rocketmq", Bindable.of(RocketMQProperties.class))
                .orElseGet(RocketMQProperties::new);
        Assertions.assertNotNull(rocketMQProperties.getNameServer(), "rocketmq.name-server 未绑定到测试配置");
        return rocketMQProperties;
    }

    /**
     * 构建项目自定义配置对象。
     *
     * @param properties 原始配置属性。
     * @return 项目配置对象。
     */
    private LoggerServerProperties buildLoggerServerProperties(Properties properties) {
        Binder binder = new Binder(new MapConfigurationPropertySource(toMap(properties)));
        LoggerServerProperties loggerServerProperties = binder.bind("logger-server", Bindable.of(LoggerServerProperties.class))
                .orElseGet(LoggerServerProperties::new);
        Assertions.assertNotNull(
                loggerServerProperties.getRocketmq().getInstanceConsumerGroupPrefix(),
                "logger-server.rocketmq.instance-consumer-group-prefix 未绑定到测试配置");
        return loggerServerProperties;
    }

    /**
     * 将 Properties 转换为字符串键值映射。
     *
     * @param properties 原始属性。
     * @return 字符串键值映射。
     */
    private Map<String, Object> toMap(Properties properties) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String propertyName : properties.stringPropertyNames()) {
            result.put(propertyName, properties.getProperty(propertyName));
        }
        return result;
    }

    /**
     * 创建测试消息生产者。
     *
     * @param nameServer RocketMQ namesrv 地址。
     * @return 已启动的生产者。
     * @throws MQClientException 启动异常。
     */
    private DefaultMQProducer createProducer(String nameServer) throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer("logger-phase03-test-producer-" + System.nanoTime());
        producer.setNamesrvAddr(nameServer);
        producer.setInstanceName("logger-phase03-test-instance-" + System.nanoTime());
        producer.start();
        return producer;
    }

    /**
     * 确保测试主题已存在。
     *
     * @param producer 测试生产者。
     * @param topic 主题名称。
     * @throws MQClientException RocketMQ 异常。
     */
    private void ensureTopicExistsOrSkip(DefaultMQProducer producer, String topic) throws MQClientException {
        try {
            producer.createTopic("TBW102", topic, 4, null);
        } catch (MQClientException exception) {
            Assumptions.assumeTrue(false,
                    "真实 RocketMQ 环境当前不可用于创建动态 topic，通常是 broker 对外注册地址不可达: " + exception.getMessage());
        }
    }

    /**
     * 空实现的控制命令委派端口。
     */
    private static class NoopSimulationControlCommandPort implements SimulationControlCommandPort {

        /**
         * 处理开始命令。
         *
         * @param instanceId 实例 ID。
         * @param protocolData 协议数据。
         */
        @Override
        public void handleStart(String instanceId, ProtocolData protocolData) {
        }

        /**
         * 处理暂停命令。
         *
         * @param instanceId 实例 ID。
         * @param protocolData 协议数据。
         */
        @Override
        public void handlePause(String instanceId, ProtocolData protocolData) {
        }

        /**
         * 处理继续命令。
         *
         * @param instanceId 实例 ID。
         * @param protocolData 协议数据。
         */
        @Override
        public void handleResume(String instanceId, ProtocolData protocolData) {
        }
    }

    /**
     * 空实现的态势入口委派端口。
     */
    private static class NoopSituationRecordIngressPort implements SituationRecordIngressPort {

        /**
         * 处理态势消息。
         *
         * @param instanceId 实例 ID。
         * @param protocolData 协议数据。
         */
        @Override
        public void handle(String instanceId, ProtocolData protocolData) {
        }
    }

    /**
     * 记录实例控制消息的委派端口。
     */
    private static class RecordingSimulationControlCommandPort implements SimulationControlCommandPort {

        private final CountDownLatch latch;

        private final AtomicReference<ProtocolData> protocolDataRef;

        /**
         * 创建记录型端口。
         *
         * @param latch 倒计时器。
         * @param protocolDataRef 协议数据引用。
         */
        private RecordingSimulationControlCommandPort(CountDownLatch latch,
                                                      AtomicReference<ProtocolData> protocolDataRef) {
            this.latch = latch;
            this.protocolDataRef = protocolDataRef;
        }

        /**
         * 处理开始命令。
         *
         * @param instanceId 实例 ID。
         * @param protocolData 协议数据。
         */
        @Override
        public void handleStart(String instanceId, ProtocolData protocolData) {
            protocolDataRef.set(protocolData);
            latch.countDown();
        }

        /**
         * 处理暂停命令。
         *
         * @param instanceId 实例 ID。
         * @param protocolData 协议数据。
         */
        @Override
        public void handlePause(String instanceId, ProtocolData protocolData) {
        }

        /**
         * 处理继续命令。
         *
         * @param instanceId 实例 ID。
         * @param protocolData 协议数据。
         */
        @Override
        public void handleResume(String instanceId, ProtocolData protocolData) {
        }
    }

    /**
     * 记录态势消息的委派端口。
     */
    private static class RecordingSituationRecordIngressPort implements SituationRecordIngressPort {

        private final CountDownLatch latch;

        private final AtomicReference<ProtocolData> protocolDataRef;

        /**
         * 创建记录型端口。
         *
         * @param latch 倒计时器。
         * @param protocolDataRef 协议数据引用。
         */
        private RecordingSituationRecordIngressPort(CountDownLatch latch,
                                                    AtomicReference<ProtocolData> protocolDataRef) {
            this.latch = latch;
            this.protocolDataRef = protocolDataRef;
        }

        /**
         * 处理态势消息。
         *
         * @param instanceId 实例 ID。
         * @param protocolData 协议数据。
         */
        @Override
        public void handle(String instanceId, ProtocolData protocolData) {
            protocolDataRef.set(protocolData);
            latch.countDown();
        }
    }
}
