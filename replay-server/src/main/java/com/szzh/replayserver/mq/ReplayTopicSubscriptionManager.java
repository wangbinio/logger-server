package com.szzh.replayserver.mq;

import com.szzh.replayserver.config.ReplayRocketMqConsumerFactory;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 回放 Topic 动态订阅管理器。
 */
@Component
public class ReplayTopicSubscriptionManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ReplayTopicSubscriptionManager.class);

    private final ReplayRocketMqConsumerFactory consumerFactory;

    private final ReplayInstanceBroadcastMessageHandler instanceBroadcastMessageHandler;

    private final ConcurrentMap<String, DefaultMQPushConsumer> subscriptions =
            new ConcurrentHashMap<String, DefaultMQPushConsumer>();

    /**
     * 创建回放 Topic 动态订阅管理器。
     *
     * @param consumerFactory 回放动态消费者工厂。
     * @param instanceBroadcastMessageHandler 实例级控制消息处理器。
     */
    public ReplayTopicSubscriptionManager(ReplayRocketMqConsumerFactory consumerFactory,
                                          ReplayInstanceBroadcastMessageHandler instanceBroadcastMessageHandler) {
        this.consumerFactory = Objects.requireNonNull(consumerFactory, "consumerFactory 不能为空");
        this.instanceBroadcastMessageHandler =
                Objects.requireNonNull(instanceBroadcastMessageHandler, "instanceBroadcastMessageHandler 不能为空");
    }

    /**
     * 为指定实例建立回放控制 topic 动态订阅。
     *
     * @param instanceId 实例 ID。
     * @return 是否发生了实际订阅。
     */
    public synchronized boolean subscribe(String instanceId) {
        String normalizedInstanceId = normalize(instanceId);
        if (subscriptions.containsKey(normalizedInstanceId)) {
            return false;
        }

        DefaultMQPushConsumer broadcastConsumer = null;
        try {
            broadcastConsumer = consumerFactory.createInstanceBroadcastConsumer(
                    normalizedInstanceId,
                    createInstanceBroadcastListener(normalizedInstanceId));
            broadcastConsumer.start();
            subscriptions.put(normalizedInstanceId, broadcastConsumer);
            return true;
        } catch (Exception exception) {
            shutdownQuietly(broadcastConsumer, normalizedInstanceId);
            throw new IllegalStateException("回放实例动态订阅初始化失败: " + normalizedInstanceId, exception);
        }
    }

    /**
     * 取消指定实例的回放控制 topic 动态订阅。
     *
     * @param instanceId 实例 ID。
     * @return 是否发生了实际取消。
     */
    public synchronized boolean unsubscribe(String instanceId) {
        String normalizedInstanceId = normalize(instanceId);
        DefaultMQPushConsumer broadcastConsumer = subscriptions.remove(normalizedInstanceId);
        if (broadcastConsumer == null) {
            return false;
        }
        shutdownQuietly(broadcastConsumer, normalizedInstanceId);
        return true;
    }

    /**
     * 判断指定实例是否已订阅回放控制 topic。
     *
     * @param instanceId 实例 ID。
     * @return 是否已订阅。
     */
    public boolean isSubscribed(String instanceId) {
        return subscriptions.containsKey(normalize(instanceId));
    }

    /**
     * 获取当前回放动态订阅数量。
     *
     * @return 当前订阅数量。
     */
    public int size() {
        return subscriptions.size();
    }

    /**
     * 关闭全部回放动态订阅。
     */
    @Override
    public synchronized void destroy() {
        for (String instanceId : subscriptions.keySet()) {
            unsubscribe(instanceId);
        }
    }

    /**
     * 创建实例控制消息监听器。
     *
     * @param instanceId 实例 ID。
     * @return 并发消息监听器。
     */
    private MessageListenerConcurrently createInstanceBroadcastListener(final String instanceId) {
        return new MessageListenerConcurrently() {
            /**
             * 消费回放控制消息。
             *
             * @param messages 消息列表。
             * @param context 消费上下文。
             * @return 消费结果。
             */
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,
                                                            ConsumeConcurrentlyContext context) {
                return consumeMessages(instanceId, messages);
            }
        };
    }

    /**
     * 执行消息消费并派发给回放控制处理器。
     *
     * @param instanceId 实例 ID。
     * @param messages 消息列表。
     * @return 消费结果。
     */
    private ConsumeConcurrentlyStatus consumeMessages(String instanceId, List<MessageExt> messages) {
        for (MessageExt message : messages) {
            try {
                instanceBroadcastMessageHandler.handle(instanceId, message);
            } catch (Exception exception) {
                log.warn("回放控制消息处理失败，instanceId={}, topic={}, msgId={}",
                        instanceId, message.getTopic(), message.getMsgId(), exception);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 静默关闭控制消费者。
     *
     * @param consumer 控制消费者。
     * @param instanceId 实例 ID。
     */
    private void shutdownQuietly(DefaultMQPushConsumer consumer, String instanceId) {
        if (consumer == null) {
            return;
        }
        try {
            consumer.shutdown();
        } catch (Exception exception) {
            log.warn("关闭回放 RocketMQ 控制消费者失败，instanceId={}", instanceId, exception);
        }
    }

    /**
     * 标准化实例 ID。
     *
     * @param instanceId 实例 ID。
     * @return 标准化后的实例 ID。
     */
    private String normalize(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        return instanceId.trim();
    }
}
