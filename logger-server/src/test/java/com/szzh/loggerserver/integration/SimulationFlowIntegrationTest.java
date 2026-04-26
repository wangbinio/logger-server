package com.szzh.loggerserver.integration;

import com.szzh.loggerserver.config.LoggerServerProperties;
import com.szzh.loggerserver.domain.clock.SimulationClock;
import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.model.dto.SituationRecordCommand;
import com.szzh.loggerserver.model.dto.TimeControlRecordCommand;
import com.szzh.loggerserver.mq.GlobalBroadcastListener;
import com.szzh.loggerserver.mq.InstanceBroadcastMessageHandler;
import com.szzh.loggerserver.mq.SituationMessageHandler;
import com.szzh.loggerserver.mq.TopicSubscriptionManager;
import com.szzh.loggerserver.service.SimulationControlService;
import com.szzh.loggerserver.service.SimulationLifecycleService;
import com.szzh.loggerserver.service.SituationRecordService;
import com.szzh.loggerserver.service.TdengineSchemaService;
import com.szzh.loggerserver.service.TdengineWriteService;
import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.loggerserver.support.metric.LoggerMetrics;
import com.szzh.common.protocol.ProtocolMessageUtil;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真主流程集成测试。
 */
class SimulationFlowIntegrationTest {

    private final MessageConstants messageConstants = new MessageConstants(new LoggerServerProperties());

    /**
     * 验证创建、启动、态势写入、暂停、继续、停止链路能够完整走通并更新指标。
     */
    @Test
    void shouldCompleteLifecycleFlowAndUpdateMetrics() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        SimulationSessionManager sessionManager = new SimulationSessionManager(() -> new SimulationClock(wallClock::get));
        LoggerMetrics metrics = new LoggerMetrics();
        TdengineSchemaService schemaService = Mockito.mock(TdengineSchemaService.class);
        TopicSubscriptionManager subscriptionManager = Mockito.mock(TopicSubscriptionManager.class);
        TdengineWriteService writeService = Mockito.mock(TdengineWriteService.class);
        SimulationLifecycleService lifecycleService =
                new SimulationLifecycleService(sessionManager, schemaService, subscriptionManager, metrics, writeService);
        SimulationControlService controlService = new SimulationControlService(sessionManager, metrics, writeService);
        SituationRecordService recordService = new SituationRecordService(sessionManager, writeService, metrics);
        GlobalBroadcastListener globalListener = new GlobalBroadcastListener(messageConstants);
        globalListener.setSimulationLifecycleCommandPort(lifecycleService);
        InstanceBroadcastMessageHandler controlHandler =
                new InstanceBroadcastMessageHandler(messageConstants, controlService, metrics);
        SituationMessageHandler situationHandler = new SituationMessageHandler(recordService, metrics);

        globalListener.onMessage(buildCreateMessage("instance-001"));

        SimulationSession createdSession = sessionManager.requireSession("instance-001");
        Assertions.assertEquals(SimulationSessionState.READY, createdSession.getState());
        Assertions.assertEquals(1, metrics.getActiveSessionCount());
        Mockito.verify(schemaService).createStableIfAbsent("instance-001");
        Mockito.verify(subscriptionManager).subscribe("instance-001");

        controlHandler.handle("instance-001", buildMessageExt(
                "instance-control",
                ProtocolMessageUtil.buildData(11,
                        (short) messageConstants.getInstanceControlMessageType(),
                        messageConstants.getInstanceStartMessageCode(),
                        new byte[0])));
        Assertions.assertEquals(SimulationSessionState.RUNNING, createdSession.getState());

        wallClock.set(1_250L);
        situationHandler.handle("instance-001", buildMessageExt(
                "instance-001-situation",
                ProtocolMessageUtil.buildData(7, (short) 2100, 9, "payload")));

        ArgumentCaptor<SituationRecordCommand> commandCaptor = ArgumentCaptor.forClass(SituationRecordCommand.class);
        Mockito.verify(writeService).write(commandCaptor.capture());
        Assertions.assertEquals(1_250L, commandCaptor.getValue().getSimTime());
        Assertions.assertEquals(1L, metrics.getMessagesReceivedCount());
        Assertions.assertEquals(1L, metrics.getMessagesWrittenCount());
        Assertions.assertEquals(0L, metrics.getMessagesDroppedCount());

        wallClock.set(1_300L);
        controlHandler.handle("instance-001", buildMessageExt(
                "instance-control",
                ProtocolMessageUtil.buildData(11,
                        (short) messageConstants.getInstanceControlMessageType(),
                        messageConstants.getInstancePauseMessageCode(),
                        new byte[0])));
        Assertions.assertEquals(SimulationSessionState.PAUSED, createdSession.getState());

        situationHandler.handle("instance-001", buildMessageExt(
                "instance-001-situation",
                ProtocolMessageUtil.buildData(7, (short) 2100, 9, "payload")));
        Assertions.assertEquals(1L, metrics.getMessagesDroppedCount());
        Assertions.assertEquals(1L, metrics.getStateViolationCount());

        wallClock.set(1_500L);
        controlHandler.handle("instance-001", buildMessageExt(
                "instance-control",
                ProtocolMessageUtil.buildData(11,
                        (short) messageConstants.getInstanceControlMessageType(),
                        messageConstants.getInstanceResumeMessageCode(),
                        new byte[0])));
        Assertions.assertEquals(SimulationSessionState.RUNNING, createdSession.getState());

        globalListener.onMessage(buildStopMessage("instance-001"));

        Optional<SimulationSession> removedSession = sessionManager.getSession("instance-001");
        Assertions.assertFalse(removedSession.isPresent());
        Assertions.assertEquals(0, metrics.getActiveSessionCount());
        Mockito.verify(subscriptionManager).unsubscribe("instance-001");
        ArgumentCaptor<TimeControlRecordCommand> timeControlCaptor =
                ArgumentCaptor.forClass(TimeControlRecordCommand.class);
        Mockito.verify(writeService, Mockito.times(4)).writeTimeControl(timeControlCaptor.capture());
        TimeControlRecordCommand stopCommand = timeControlCaptor.getAllValues().get(3);
        Assertions.assertEquals("instance-001", stopCommand.getInstanceId());
        Assertions.assertEquals(0D, stopCommand.getRate());
        Assertions.assertEquals(messageConstants.getGlobalMessageType(), stopCommand.getMessageType());
        Assertions.assertEquals(messageConstants.getGlobalStopMessageCode(), stopCommand.getMessageCode());
    }

    /**
     * 验证协议解析失败时会被安全吞掉并累计失败指标。
     */
    @Test
    void shouldRecordProtocolFailureWithoutBreakingConsumer() {
        LoggerMetrics metrics = new LoggerMetrics();
        GlobalBroadcastListener globalListener = new GlobalBroadcastListener(messageConstants, metrics);

        globalListener.onMessage(buildMessageExt(
                "broadcast-global",
                "broken-packet".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals(1L, metrics.getProtocolParseFailureCount());
    }

    /**
     * 构造创建消息。
     *
     * @param instanceId 实例 ID。
     * @return RocketMQ 消息对象。
     */
    private MessageExt buildCreateMessage(String instanceId) {
        String payload = "{\"instanceId\":\"" + instanceId + "\"}";
        return buildMessageExt(
                "broadcast-global",
                ProtocolMessageUtil.buildData(
                        0,
                        (short) messageConstants.getGlobalMessageType(),
                        messageConstants.getGlobalCreateMessageCode(),
                        payload));
    }

    /**
     * 构造停止消息。
     *
     * @param instanceId 实例 ID。
     * @return RocketMQ 消息对象。
     */
    private MessageExt buildStopMessage(String instanceId) {
        String payload = "{\"instanceId\":\"" + instanceId + "\"}";
        return buildMessageExt(
                "broadcast-global",
                ProtocolMessageUtil.buildData(
                        0,
                        (short) messageConstants.getGlobalMessageType(),
                        messageConstants.getGlobalStopMessageCode(),
                        payload));
    }

    /**
     * 构造 RocketMQ 消息对象。
     *
     * @param topic 主题名。
     * @param body 消息体。
     * @return RocketMQ 消息对象。
     */
    private MessageExt buildMessageExt(String topic, byte[] body) {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(topic);
        messageExt.setBody(body);
        messageExt.setMsgId(topic + "-msg");
        return messageExt;
    }
}
