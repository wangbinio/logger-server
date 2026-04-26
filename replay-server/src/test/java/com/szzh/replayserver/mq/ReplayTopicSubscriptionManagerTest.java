package com.szzh.replayserver.mq;

import com.szzh.replayserver.config.ReplayRocketMqConsumerFactory;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Collections;

/**
 * 回放动态订阅管理器测试。
 */
class ReplayTopicSubscriptionManagerTest {

    /**
     * 验证回放动态订阅只创建 broadcast 消费者，并保持幂等。
     */
    @Test
    void shouldSubscribeBroadcastTopicOnlyIdempotently() throws Exception {
        ReplayRocketMqConsumerFactory consumerFactory = Mockito.mock(ReplayRocketMqConsumerFactory.class);
        ReplayInstanceBroadcastMessageHandler handler = Mockito.mock(ReplayInstanceBroadcastMessageHandler.class);
        DefaultMQPushConsumer broadcastConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        ReplayTopicSubscriptionManager manager = new ReplayTopicSubscriptionManager(consumerFactory, handler);
        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(broadcastConsumer);

        Assertions.assertTrue(manager.subscribe("instance-001"));
        Assertions.assertFalse(manager.subscribe("instance-001"));

        Assertions.assertTrue(manager.isSubscribed("instance-001"));
        Assertions.assertEquals(1, manager.size());
        Mockito.verify(consumerFactory, Mockito.times(1))
                .createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any());
        Mockito.verify(broadcastConsumer, Mockito.times(1)).start();
        assertNoSituationConsumerFactoryMethod();
    }

    /**
     * 验证取消订阅会关闭 broadcast 消费者并保持幂等。
     */
    @Test
    void shouldUnsubscribeBroadcastConsumerIdempotently() throws Exception {
        ReplayRocketMqConsumerFactory consumerFactory = Mockito.mock(ReplayRocketMqConsumerFactory.class);
        ReplayInstanceBroadcastMessageHandler handler = Mockito.mock(ReplayInstanceBroadcastMessageHandler.class);
        DefaultMQPushConsumer broadcastConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        ReplayTopicSubscriptionManager manager = new ReplayTopicSubscriptionManager(consumerFactory, handler);
        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(broadcastConsumer);

        manager.subscribe("instance-001");

        Assertions.assertTrue(manager.unsubscribe("instance-001"));
        Assertions.assertFalse(manager.unsubscribe("instance-001"));
        Assertions.assertFalse(manager.isSubscribed("instance-001"));
        Assertions.assertEquals(0, manager.size());
        Mockito.verify(broadcastConsumer, Mockito.times(1)).shutdown();
    }

    /**
     * 验证订阅失败时会回收已创建的消费者。
     */
    @Test
    void shouldCleanupBroadcastConsumerWhenSubscribeFails() throws Exception {
        ReplayRocketMqConsumerFactory consumerFactory = Mockito.mock(ReplayRocketMqConsumerFactory.class);
        ReplayInstanceBroadcastMessageHandler handler = Mockito.mock(ReplayInstanceBroadcastMessageHandler.class);
        DefaultMQPushConsumer broadcastConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        ReplayTopicSubscriptionManager manager = new ReplayTopicSubscriptionManager(consumerFactory, handler);
        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(broadcastConsumer);
        Mockito.doThrow(new MQClientException(500, "boom")).when(broadcastConsumer).start();

        Assertions.assertThrows(IllegalStateException.class, () -> manager.subscribe("instance-001"));
        Assertions.assertFalse(manager.isSubscribed("instance-001"));
        Assertions.assertEquals(0, manager.size());
        Mockito.verify(broadcastConsumer, Mockito.times(1)).shutdown();
    }

    /**
     * 验证动态监听器会把消息派发给实例控制处理器。
     */
    @Test
    void shouldDispatchBroadcastMessagesToHandler() throws Exception {
        ReplayRocketMqConsumerFactory consumerFactory = Mockito.mock(ReplayRocketMqConsumerFactory.class);
        ReplayInstanceBroadcastMessageHandler handler = Mockito.mock(ReplayInstanceBroadcastMessageHandler.class);
        DefaultMQPushConsumer broadcastConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        ReplayTopicSubscriptionManager manager = new ReplayTopicSubscriptionManager(consumerFactory, handler);
        ArgumentCaptor<MessageListenerConcurrently> listenerCaptor =
                ArgumentCaptor.forClass(MessageListenerConcurrently.class);
        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-001"), listenerCaptor.capture()))
                .thenReturn(broadcastConsumer);
        manager.subscribe("instance-001");
        MessageExt messageExt = new MessageExt();

        ConsumeConcurrentlyStatus status = listenerCaptor.getValue()
                .consumeMessage(Collections.singletonList(messageExt), null);

        Assertions.assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, status);
        Mockito.verify(handler).handle("instance-001", messageExt);
    }

    /**
     * 验证容器销毁时关闭所有动态订阅。
     */
    @Test
    void shouldCloseSubscriptionsOnDestroy() throws Exception {
        ReplayRocketMqConsumerFactory consumerFactory = Mockito.mock(ReplayRocketMqConsumerFactory.class);
        ReplayInstanceBroadcastMessageHandler handler = Mockito.mock(ReplayInstanceBroadcastMessageHandler.class);
        DefaultMQPushConsumer firstConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumer secondConsumer = Mockito.mock(DefaultMQPushConsumer.class);
        ReplayTopicSubscriptionManager manager = new ReplayTopicSubscriptionManager(consumerFactory, handler);
        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-001"), Mockito.any()))
                .thenReturn(firstConsumer);
        Mockito.when(consumerFactory.createInstanceBroadcastConsumer(Mockito.eq("instance-002"), Mockito.any()))
                .thenReturn(secondConsumer);
        manager.subscribe("instance-001");
        manager.subscribe("instance-002");

        manager.destroy();

        Assertions.assertEquals(0, manager.size());
        Mockito.verify(firstConsumer).shutdown();
        Mockito.verify(secondConsumer).shutdown();
    }

    /**
     * 验证工厂不存在态势消费者创建方法。
     */
    private void assertNoSituationConsumerFactoryMethod() {
        for (Method method : ReplayRocketMqConsumerFactory.class.getDeclaredMethods()) {
            Assertions.assertNotEquals("createSituationConsumer", method.getName(),
                    "回放服务禁止订阅 situation-{instanceId}");
        }
    }
}
