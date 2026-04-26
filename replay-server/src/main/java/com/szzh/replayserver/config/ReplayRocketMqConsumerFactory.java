package com.szzh.replayserver.config;

import com.szzh.common.topic.TopicConstants;
import org.apache.rocketmq.client.AccessChannel;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

/**
 * 回放动态 RocketMQ 消费者工厂。
 */
@Component
public class ReplayRocketMqConsumerFactory {

    private final RocketMQProperties rocketMQProperties;

    private final ReplayServerProperties replayServerProperties;

    /**
     * 创建回放动态 RocketMQ 消费者工厂。
     *
     * @param rocketMQProperties RocketMQ 配置。
     * @param replayServerProperties 回放服务配置。
     */
    public ReplayRocketMqConsumerFactory(RocketMQProperties rocketMQProperties,
                                         ReplayServerProperties replayServerProperties) {
        this.rocketMQProperties = Objects.requireNonNull(rocketMQProperties, "rocketMQProperties 不能为空");
        this.replayServerProperties = Objects.requireNonNull(replayServerProperties, "replayServerProperties 不能为空");
    }

    /**
     * 创建实例级回放控制消费者。
     *
     * @param instanceId 实例 ID。
     * @param messageListener 消息监听器。
     * @return 已配置但未启动的控制消费者。
     * @throws MQClientException 订阅配置异常。
     */
    public DefaultMQPushConsumer createInstanceBroadcastConsumer(String instanceId,
                                                                 MessageListenerConcurrently messageListener)
            throws MQClientException {
        return createConsumer(
                buildConsumerGroup("broadcast", instanceId),
                TopicConstants.buildInstanceBroadcastTopic(instanceId),
                messageListener);
    }

    /**
     * 创建并配置 PushConsumer。
     *
     * @param consumerGroup 消费组。
     * @param topic 订阅主题。
     * @param messageListener 消息监听器。
     * @return 已配置但未启动的消费者。
     * @throws MQClientException 订阅配置异常。
     */
    private DefaultMQPushConsumer createConsumer(String consumerGroup,
                                                 String topic,
                                                 MessageListenerConcurrently messageListener)
            throws MQClientException {
        if (!StringUtils.hasText(rocketMQProperties.getNameServer())) {
            throw new IllegalStateException("rocketmq.name-server 未配置");
        }
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(rocketMQProperties.getNameServer());
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(1);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setInstanceName(buildInstanceName(consumerGroup));
        applyAccessChannel(consumer);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener(Objects.requireNonNull(messageListener, "messageListener 不能为空"));
        return consumer;
    }

    /**
     * 应用访问通道配置。
     *
     * @param consumer 消费者。
     */
    private void applyAccessChannel(DefaultMQPushConsumer consumer) {
        if (!StringUtils.hasText(rocketMQProperties.getAccessChannel())) {
            return;
        }
        consumer.setAccessChannel(AccessChannel.valueOf(
                rocketMQProperties.getAccessChannel().trim().toUpperCase(Locale.ENGLISH)));
    }

    /**
     * 构建实例消费组。
     *
     * @param channelType 通道类型。
     * @param instanceId 实例 ID。
     * @return 消费组名称。
     */
    private String buildConsumerGroup(String channelType, String instanceId) {
        return replayServerProperties.getRocketmq().getInstanceConsumerGroupPrefix()
                + "-"
                + channelType
                + "-"
                + sanitize(instanceId);
    }

    /**
     * 构建消费者实例名。
     *
     * @param consumerGroup 消费组。
     * @return 实例名。
     */
    private String buildInstanceName(String consumerGroup) {
        return consumerGroup + "-" + System.nanoTime();
    }

    /**
     * 清洗实例 ID，避免非法字符污染 RocketMQ 命名。
     *
     * @param instanceId 实例 ID。
     * @return 清洗后的实例 ID。
     */
    private String sanitize(String instanceId) {
        return instanceId.trim().replaceAll("[^0-9A-Za-z_-]", "_");
    }
}
