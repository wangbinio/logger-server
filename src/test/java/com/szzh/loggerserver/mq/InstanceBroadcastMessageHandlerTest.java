package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.config.LoggerServerProperties;
import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.loggerserver.support.metric.LoggerMetrics;
import com.szzh.loggerserver.util.ProtocolData;
import com.szzh.loggerserver.util.ProtocolMessageUtil;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * 实例级广播消息处理器测试。
 */
class InstanceBroadcastMessageHandlerTest {

    /**
     * 验证处理器会使用配置化的实例控制消息类型与消息码委派启动命令。
     */
    @Test
    void shouldHandleConfiguredInstanceStartMessage() {
        LoggerServerProperties properties = new LoggerServerProperties();
        properties.getProtocol().getMessages().getInstance().setMessageType(2100);
        properties.getProtocol().getMessages().getInstance().setStartMessageCode(7);
        MessageConstants messageConstants = new MessageConstants(properties);
        SimulationControlCommandPort commandPort = Mockito.mock(SimulationControlCommandPort.class);
        InstanceBroadcastMessageHandler handler =
                new InstanceBroadcastMessageHandler(messageConstants, commandPort, new LoggerMetrics());

        MessageExt messageExt = new MessageExt();
        messageExt.setTopic("instance-control");
        messageExt.setBody(ProtocolMessageUtil.buildData(11, (short) 2100, 7, new byte[0]));

        handler.handle("instance-001", messageExt);

        ArgumentCaptor<ProtocolData> protocolDataCaptor = ArgumentCaptor.forClass(ProtocolData.class);
        Mockito.verify(commandPort).handleStart(Mockito.eq("instance-001"), protocolDataCaptor.capture());
        Assertions.assertEquals(2100, protocolDataCaptor.getValue().getMessageType());
        Assertions.assertEquals(7, protocolDataCaptor.getValue().getMessageCode());
    }
}
