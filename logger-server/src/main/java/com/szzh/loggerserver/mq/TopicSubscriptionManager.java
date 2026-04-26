package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.config.RocketMqConsumerFactory;
import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
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
 * Topic 动态订阅管理器。
 */
@Component
public class TopicSubscriptionManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TopicSubscriptionManager.class);

    private final SimulationSessionManager sessionManager;

    private final RocketMqConsumerFactory consumerFactory;

    private final InstanceBroadcastMessageHandler instanceBroadcastMessageHandler;

    private final SituationMessageHandler situationMessageHandler;

    private final ConcurrentMap<String, SubscriptionBinding> subscriptions = new ConcurrentHashMap<String, SubscriptionBinding>();

    /**
     * 创建 Topic 动态订阅管理器。
     *
     * @param sessionManager 会话管理器。
     * @param consumerFactory RocketMQ 消费者工厂。
     * @param instanceBroadcastMessageHandler 实例控制消息处理器。
     * @param situationMessageHandler 态势消息处理器。
     */
    public TopicSubscriptionManager(SimulationSessionManager sessionManager,
                                    RocketMqConsumerFactory consumerFactory,
                                    InstanceBroadcastMessageHandler instanceBroadcastMessageHandler,
                                    SituationMessageHandler situationMessageHandler) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.consumerFactory = Objects.requireNonNull(consumerFactory, "consumerFactory 不能为空");
        this.instanceBroadcastMessageHandler =
                Objects.requireNonNull(instanceBroadcastMessageHandler, "instanceBroadcastMessageHandler 不能为空");
        this.situationMessageHandler =
                Objects.requireNonNull(situationMessageHandler, "situationMessageHandler 不能为空");
    }

    /**
     * 为指定实例建立动态订阅。
     *
     * @param instanceId 实例 ID。
     * @return 是否发生了实际订阅。
     */
    public synchronized boolean subscribe(String instanceId) {
        SimulationSession session = sessionManager.requireSession(instanceId);
        if (subscriptions.containsKey(session.getInstanceId())) {
            return false;
        }

        DefaultMQPushConsumer broadcastConsumer = null;
        DefaultMQPushConsumer situationConsumer = null;
        try {
            broadcastConsumer = consumerFactory.createInstanceBroadcastConsumer(
                    session.getInstanceId(),
                    createInstanceBroadcastListener(session.getInstanceId()));
            situationConsumer = consumerFactory.createSituationConsumer(
                    session.getInstanceId(),
                    createSituationListener(session.getInstanceId()));
            broadcastConsumer.start();
            situationConsumer.start();

            SubscriptionBinding binding = new SubscriptionBinding(broadcastConsumer, situationConsumer);
            subscriptions.put(session.getInstanceId(), binding);
            session.setBroadcastConsumerHandle(broadcastConsumer);
            session.setSituationConsumerHandle(situationConsumer);
            return true;
        } catch (Exception exception) {
            shutdownQuietly(broadcastConsumer, session.getInstanceId(), "broadcast");
            shutdownQuietly(situationConsumer, session.getInstanceId(), "situation");
            session.setBroadcastConsumerHandle(null);
            session.setSituationConsumerHandle(null);
            session.recordFailure(exception.getMessage());
            throw new IllegalStateException("实例动态订阅初始化失败: " + session.getInstanceId(), exception);
        }
    }

    /**
     * 取消指定实例的动态订阅。
     *
     * @param instanceId 实例 ID。
     * @return 是否发生了实际取消。
     */
    public synchronized boolean unsubscribe(String instanceId) {
        SimulationSession session = sessionManager.getSession(instanceId).orElse(null);
        SubscriptionBinding binding = subscriptions.remove(normalize(instanceId));
        if (binding == null) {
            return false;
        }
        shutdownQuietly(binding.getBroadcastConsumer(), normalize(instanceId), "broadcast");
        shutdownQuietly(binding.getSituationConsumer(), normalize(instanceId), "situation");
        if (session != null) {
            session.setBroadcastConsumerHandle(null);
            session.setSituationConsumerHandle(null);
        }
        return true;
    }

    /**
     * 判断指定实例是否已订阅。
     *
     * @param instanceId 实例 ID。
     * @return 是否已订阅。
     */
    public boolean isSubscribed(String instanceId) {
        return subscriptions.containsKey(normalize(instanceId));
    }

    /**
     * 获取当前订阅数量。
     *
     * @return 当前订阅数量。
     */
    public int size() {
        return subscriptions.size();
    }

    /**
     * 关闭全部动态订阅。
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
             * 消费控制消息。
             *
             * @param messages 消息列表。
             * @param context 消费上下文。
             * @return 消费结果。
             */
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,
                                                            ConsumeConcurrentlyContext context) {
                return consumeMessages(instanceId, messages, true);
            }
        };
    }

    /**
     * 创建态势消息监听器。
     *
     * @param instanceId 实例 ID。
     * @return 并发消息监听器。
     */
    private MessageListenerConcurrently createSituationListener(final String instanceId) {
        return new MessageListenerConcurrently() {
            /**
             * 消费态势消息。
             *
             * @param messages 消息列表。
             * @param context 消费上下文。
             * @return 消费结果。
             */
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,
                                                            ConsumeConcurrentlyContext context) {
                return consumeMessages(instanceId, messages, false);
            }
        };
    }

    /**
     * 执行消息消费并派发到对应处理器。
     *
     * @param instanceId 实例 ID。
     * @param messages 消息列表。
     * @param controlMessage 是否为控制消息。
     * @return 消费结果。
     */
    private ConsumeConcurrentlyStatus consumeMessages(String instanceId,
                                                      List<MessageExt> messages,
                                                      boolean controlMessage) {
        for (MessageExt message : messages) {
            try {
                if (controlMessage) {
                    instanceBroadcastMessageHandler.handle(instanceId, message);
                } else {
                    situationMessageHandler.handle(instanceId, message);
                }
            } catch (Exception exception) {
                logMessageHandleFailed(instanceId, message, exception);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 静默关闭消费者。
     *
     * @param consumer 消费者。
     * @param instanceId 实例 ID。
     * @param channelType 通道类型。
     */
    private void shutdownQuietly(DefaultMQPushConsumer consumer, String instanceId, String channelType) {
        if (consumer == null) {
            return;
        }
        try {
            consumer.shutdown();
        } catch (Exception exception) {
            logConsumerShutdownFailed(instanceId, channelType, exception);
        }
    }

    /**
     * 输出 RocketMQ 消息处理失败日志。
     *
     * @param instanceId 实例 ID。
     * @param message RocketMQ 原始消息。
     * @param exception 处理异常。
     */
    private void logMessageHandleFailed(String instanceId, MessageExt message, Exception exception) {
        log.warn("RocketMQ 消息处理失败，instanceId={}, topic={}, msgId={}",
                instanceId, message.getTopic(), message.getMsgId(), exception);
    }

    /**
     * 输出 RocketMQ 消费者关闭失败日志。
     *
     * @param instanceId 实例 ID。
     * @param channelType 通道类型。
     * @param exception 关闭异常。
     */
    private void logConsumerShutdownFailed(String instanceId, String channelType, Exception exception) {
        log.warn("关闭 RocketMQ 消费者失败，instanceId={}, channelType={}", instanceId, channelType, exception);
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

    /**
     * 订阅句柄绑定。
     */
    private static final class SubscriptionBinding {

        private final DefaultMQPushConsumer broadcastConsumer;

        private final DefaultMQPushConsumer situationConsumer;

        /**
         * 创建订阅句柄绑定。
         *
         * @param broadcastConsumer 控制消费者。
         * @param situationConsumer 态势消费者。
         */
        private SubscriptionBinding(DefaultMQPushConsumer broadcastConsumer,
                                    DefaultMQPushConsumer situationConsumer) {
            this.broadcastConsumer = broadcastConsumer;
            this.situationConsumer = situationConsumer;
        }

        /**
         * 获取控制消费者。
         *
         * @return 控制消费者。
         */
        private DefaultMQPushConsumer getBroadcastConsumer() {
            return broadcastConsumer;
        }

        /**
         * 获取态势消费者。
         *
         * @return 态势消费者。
         */
        private DefaultMQPushConsumer getSituationConsumer() {
            return situationConsumer;
        }
    }
}
