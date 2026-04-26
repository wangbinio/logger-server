package com.szzh.replayserver.mq;

import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * 回放全局广播监听器测试。
 */
class ReplayGlobalBroadcastListenerTest {

    private final ReplayMessageConstants constants = new ReplayMessageConstants(new ReplayServerProperties());

    /**
     * 验证注解监听器直接接收 RocketMQ 原始消息。
     */
    @Test
    void shouldDeclareMessageExtAsRocketMqListenerType() {
        Type[] genericInterfaces = ReplayGlobalBroadcastListener.class.getGenericInterfaces();
        ParameterizedType listenerType = null;
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType
                    && ((ParameterizedType) genericInterface).getRawType() == RocketMQListener.class) {
                listenerType = (ParameterizedType) genericInterface;
                break;
            }
        }

        Assertions.assertNotNull(listenerType, "ReplayGlobalBroadcastListener 应声明 RocketMQListener 泛型");
        Assertions.assertEquals(MessageExt.class, listenerType.getActualTypeArguments()[0]);
    }

    /**
     * 验证回放创建消息会委派生命周期端口。
     */
    @Test
    void shouldDelegateCreateReplayMessage() {
        ReplayLifecycleCommandPort commandPort = Mockito.mock(ReplayLifecycleCommandPort.class);
        ReplayGlobalBroadcastListener listener = new ReplayGlobalBroadcastListener(constants);
        listener.setReplayLifecycleCommandPort(commandPort);
        byte[] payload = "{\"instanceId\":\"instance-001\"}".getBytes(StandardCharsets.UTF_8);

        listener.onMessage(buildMessage(constants.getGlobalMessageType(),
                constants.getGlobalCreateMessageCode(),
                payload));

        ArgumentCaptor<ProtocolData> captor = ArgumentCaptor.forClass(ProtocolData.class);
        Mockito.verify(commandPort).handleCreate(captor.capture());
        Assertions.assertEquals(constants.getGlobalMessageType(), captor.getValue().getMessageType());
        Assertions.assertEquals(constants.getGlobalCreateMessageCode(), captor.getValue().getMessageCode());
        Assertions.assertArrayEquals(payload, captor.getValue().getRawData());
    }

    /**
     * 验证回放停止消息会委派生命周期端口。
     */
    @Test
    void shouldDelegateStopReplayMessage() {
        ReplayLifecycleCommandPort commandPort = Mockito.mock(ReplayLifecycleCommandPort.class);
        ReplayGlobalBroadcastListener listener = new ReplayGlobalBroadcastListener(constants);
        listener.setReplayLifecycleCommandPort(commandPort);

        listener.onMessage(buildMessage(constants.getGlobalMessageType(),
                constants.getGlobalStopMessageCode(),
                "{\"instanceId\":\"instance-001\"}".getBytes(StandardCharsets.UTF_8)));

        Mockito.verify(commandPort).handleStop(Mockito.any(ProtocolData.class));
    }

    /**
     * 验证非回放全局消息不会触发回放端口。
     */
    @Test
    void shouldIgnoreNonReplayGlobalMessage() {
        ReplayLifecycleCommandPort commandPort = Mockito.mock(ReplayLifecycleCommandPort.class);
        ReplayGlobalBroadcastListener listener = new ReplayGlobalBroadcastListener(constants);
        listener.setReplayLifecycleCommandPort(commandPort);

        listener.onMessage(buildMessage(0, constants.getGlobalCreateMessageCode(), new byte[0]));

        Mockito.verifyNoInteractions(commandPort);
    }

    /**
     * 验证未知回放全局消息码不会触发生命周期端口。
     */
    @Test
    void shouldIgnoreUnknownReplayGlobalMessageCode() {
        ReplayLifecycleCommandPort commandPort = Mockito.mock(ReplayLifecycleCommandPort.class);
        ReplayGlobalBroadcastListener listener = new ReplayGlobalBroadcastListener(constants);
        listener.setReplayLifecycleCommandPort(commandPort);

        listener.onMessage(buildMessage(constants.getGlobalMessageType(), 99, new byte[0]));

        Mockito.verifyNoInteractions(commandPort);
    }

    /**
     * 验证协议解析失败不会导致监听线程抛出异常。
     */
    @Test
    void shouldSwallowProtocolParseFailure() {
        ReplayLifecycleCommandPort commandPort = Mockito.mock(ReplayLifecycleCommandPort.class);
        ReplayGlobalBroadcastListener listener = new ReplayGlobalBroadcastListener(constants);
        listener.setReplayLifecycleCommandPort(commandPort);
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(TopicConstants.GLOBAL_BROADCAST_TOPIC);
        messageExt.setBody(new byte[]{1, 2, 3});

        Assertions.assertDoesNotThrow(() -> listener.onMessage(messageExt));
        Mockito.verifyNoInteractions(commandPort);
    }

    /**
     * 构建测试消息。
     *
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param payload 业务载荷。
     * @return RocketMQ 原始消息。
     */
    private MessageExt buildMessage(int messageType, int messageCode, byte[] payload) {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(TopicConstants.GLOBAL_BROADCAST_TOPIC);
        messageExt.setBody(ProtocolMessageUtil.buildData(10, (short) messageType, messageCode, payload));
        return messageExt;
    }
}
