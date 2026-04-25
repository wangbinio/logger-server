package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.config.LoggerServerProperties;
import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.loggerserver.support.constant.TopicConstants;
import com.szzh.loggerserver.util.ProtocolData;
import com.szzh.loggerserver.util.ProtocolMessageUtil;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 全局广播监听器测试。
 */
class GlobalBroadcastListenerTest {

    private final MessageConstants defaultMessageConstants = new MessageConstants(new LoggerServerProperties());

    /**
     * 验证监听器显式声明为接收原始 MessageExt，避免注解容器错误地先转成 String。
     */
    @Test
    void shouldDeclareMessageExtAsRocketMqListenerType() {
        Type[] genericInterfaces = GlobalBroadcastListener.class.getGenericInterfaces();
        ParameterizedType listenerType = null;
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType
                    && ((ParameterizedType) genericInterface).getRawType() == RocketMQListener.class) {
                listenerType = (ParameterizedType) genericInterface;
                break;
            }
        }

        Assertions.assertNotNull(listenerType, "GlobalBroadcastListener 应声明 RocketMQListener 泛型");
        Assertions.assertEquals(MessageExt.class,
                listenerType.getActualTypeArguments()[0],
                "注解监听模式下必须接收原始 MessageExt");
    }

    /**
     * 验证监听器能够从原始 MessageExt 的 body 中解析协议并委派创建命令。
     */
    @Test
    void shouldHandleCreateMessageFromRawMessageExt() {
        SimulationLifecycleCommandPort commandPort = Mockito.mock(SimulationLifecycleCommandPort.class);
        GlobalBroadcastListener listener = new GlobalBroadcastListener(defaultMessageConstants);
        listener.setSimulationLifecycleCommandPort(commandPort);

        byte[] payload = "{\"instanceId\":\"instance-001\"}".getBytes(StandardCharsets.UTF_8);
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(TopicConstants.GLOBAL_BROADCAST_TOPIC);
        messageExt.setBody(ProtocolMessageUtil.buildData(
                0,
                (short) defaultMessageConstants.getGlobalMessageType(),
                defaultMessageConstants.getGlobalCreateMessageCode(),
                payload));

        listener.onMessage(messageExt);

        ArgumentCaptor<ProtocolData> protocolDataCaptor = ArgumentCaptor.forClass(ProtocolData.class);
        Mockito.verify(commandPort).handleCreate(protocolDataCaptor.capture());
        ProtocolData protocolData = protocolDataCaptor.getValue();
        Assertions.assertEquals(defaultMessageConstants.getGlobalMessageType(), protocolData.getMessageType());
        Assertions.assertEquals(defaultMessageConstants.getGlobalCreateMessageCode(), protocolData.getMessageCode());
        Assertions.assertTrue(Arrays.equals(payload, protocolData.getRawData()), "原始载荷应保持不变");
    }

    /**
     * 验证监听器会使用配置化的全局消息类型与消息码进行委派。
     */
    @Test
    void shouldHandleConfiguredGlobalCreateMessage() {
        LoggerServerProperties properties = new LoggerServerProperties();
        properties.getProtocol().getMessages().getGlobal().setMessageType(12);
        properties.getProtocol().getMessages().getGlobal().setCreateMessageCode(34);
        SimulationLifecycleCommandPort commandPort = Mockito.mock(SimulationLifecycleCommandPort.class);
        GlobalBroadcastListener listener = new GlobalBroadcastListener(new MessageConstants(properties));
        listener.setSimulationLifecycleCommandPort(commandPort);

        byte[] payload = "{\"instanceId\":\"instance-002\"}".getBytes(StandardCharsets.UTF_8);
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(TopicConstants.GLOBAL_BROADCAST_TOPIC);
        messageExt.setBody(ProtocolMessageUtil.buildData(
                0,
                (short) 12,
                34,
                payload));

        listener.onMessage(messageExt);

        ArgumentCaptor<ProtocolData> protocolDataCaptor = ArgumentCaptor.forClass(ProtocolData.class);
        Mockito.verify(commandPort).handleCreate(protocolDataCaptor.capture());
        Assertions.assertEquals(12, protocolDataCaptor.getValue().getMessageType());
        Assertions.assertEquals(34, protocolDataCaptor.getValue().getMessageCode());
    }
}
