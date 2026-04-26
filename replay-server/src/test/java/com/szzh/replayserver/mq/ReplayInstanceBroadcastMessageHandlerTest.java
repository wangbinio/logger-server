package com.szzh.replayserver.mq;

import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * 回放实例级广播控制处理器测试。
 */
class ReplayInstanceBroadcastMessageHandlerTest {

    private final ReplayMessageConstants constants = new ReplayMessageConstants(new ReplayServerProperties());

    /**
     * 验证启动、暂停、继续、倍速和跳转命令均委派到控制端口。
     */
    @Test
    void shouldDelegateSupportedControlMessages() {
        ReplayControlCommandPort commandPort = Mockito.mock(ReplayControlCommandPort.class);
        ReplayInstanceBroadcastMessageHandler handler =
                new ReplayInstanceBroadcastMessageHandler(constants, commandPort);

        handler.handle("instance-001", buildMessage(constants.getInstanceStartMessageCode()));
        handler.handle("instance-001", buildMessage(constants.getInstancePauseMessageCode()));
        handler.handle("instance-001", buildMessage(constants.getInstanceResumeMessageCode()));
        handler.handle("instance-001", buildMessage(constants.getInstanceRateMessageCode()));
        handler.handle("instance-001", buildMessage(constants.getInstanceJumpMessageCode()));

        Mockito.verify(commandPort).handleStart(Mockito.eq("instance-001"), Mockito.any(ProtocolData.class));
        Mockito.verify(commandPort).handlePause(Mockito.eq("instance-001"), Mockito.any(ProtocolData.class));
        Mockito.verify(commandPort).handleResume(Mockito.eq("instance-001"), Mockito.any(ProtocolData.class));
        Mockito.verify(commandPort).handleRate(Mockito.eq("instance-001"), Mockito.any(ProtocolData.class));
        Mockito.verify(commandPort).handleJump(Mockito.eq("instance-001"), Mockito.any(ProtocolData.class));
    }

    /**
     * 验证元信息通知不会被回放控制处理器当成控制命令。
     */
    @Test
    void shouldIgnoreMetadataMessage() {
        ReplayControlCommandPort commandPort = Mockito.mock(ReplayControlCommandPort.class);
        ReplayInstanceBroadcastMessageHandler handler =
                new ReplayInstanceBroadcastMessageHandler(constants, commandPort);

        handler.handle("instance-001", buildMessage(constants.getInstanceMetadataMessageCode()));

        Mockito.verifyNoInteractions(commandPort);
    }

    /**
     * 验证未知回放控制消息码不会触发控制端口。
     */
    @Test
    void shouldIgnoreUnknownReplayControlMessageCode() {
        ReplayControlCommandPort commandPort = Mockito.mock(ReplayControlCommandPort.class);
        ReplayInstanceBroadcastMessageHandler handler =
                new ReplayInstanceBroadcastMessageHandler(constants, commandPort);

        handler.handle("instance-001", buildMessage(99));

        Mockito.verifyNoInteractions(commandPort);
    }

    /**
     * 验证非回放实例控制消息不会触发控制端口。
     */
    @Test
    void shouldIgnoreNonReplayControlMessageType() {
        ReplayControlCommandPort commandPort = Mockito.mock(ReplayControlCommandPort.class);
        ReplayInstanceBroadcastMessageHandler handler =
                new ReplayInstanceBroadcastMessageHandler(constants, commandPort);
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(TopicConstants.buildInstanceBroadcastTopic("instance-001"));
        messageExt.setBody(ProtocolMessageUtil.buildData(10, (short) 1100, 1, new byte[0]));

        handler.handle("instance-001", messageExt);

        Mockito.verifyNoInteractions(commandPort);
    }

    /**
     * 验证协议解析失败不会向外抛出异常。
     */
    @Test
    void shouldSwallowProtocolParseFailure() {
        ReplayControlCommandPort commandPort = Mockito.mock(ReplayControlCommandPort.class);
        ReplayInstanceBroadcastMessageHandler handler =
                new ReplayInstanceBroadcastMessageHandler(constants, commandPort);
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(TopicConstants.buildInstanceBroadcastTopic("instance-001"));
        messageExt.setBody(new byte[]{1, 2, 3});

        Assertions.assertDoesNotThrow(() -> handler.handle("instance-001", messageExt));
        Mockito.verifyNoInteractions(commandPort);
    }

    /**
     * 验证委派时保留协议消息编号。
     */
    @Test
    void shouldKeepProtocolDataWhenDelegating() {
        ReplayControlCommandPort commandPort = Mockito.mock(ReplayControlCommandPort.class);
        ReplayInstanceBroadcastMessageHandler handler =
                new ReplayInstanceBroadcastMessageHandler(constants, commandPort);

        handler.handle("instance-001", buildMessage(constants.getInstanceRateMessageCode()));

        ArgumentCaptor<ProtocolData> captor = ArgumentCaptor.forClass(ProtocolData.class);
        Mockito.verify(commandPort).handleRate(Mockito.eq("instance-001"), captor.capture());
        Assertions.assertEquals(constants.getInstanceControlMessageType(), captor.getValue().getMessageType());
        Assertions.assertEquals(constants.getInstanceRateMessageCode(), captor.getValue().getMessageCode());
    }

    /**
     * 构建实例控制消息。
     *
     * @param messageCode 消息编号。
     * @return RocketMQ 原始消息。
     */
    private MessageExt buildMessage(int messageCode) {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(TopicConstants.buildInstanceBroadcastTopic("instance-001"));
        messageExt.setBody(ProtocolMessageUtil.buildData(
                10,
                (short) constants.getInstanceControlMessageType(),
                messageCode,
                new byte[0]));
        return messageExt;
    }
}
