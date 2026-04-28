package com.szzh.replayserver.integration;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.ReplayServerApplication;
import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import com.szzh.replayserver.mq.ReplayGlobalBroadcastListener;
import com.szzh.replayserver.mq.ReplayInstanceBroadcastMessageHandler;
import com.szzh.replayserver.mq.ReplayRocketMqSender;
import com.szzh.replayserver.mq.ReplaySituationPublisher;
import com.szzh.replayserver.repository.ReplayFrameRepository;
import com.szzh.replayserver.repository.ReplayTableDiscoveryRepository;
import com.szzh.replayserver.repository.ReplayTimeControlRepository;
import com.szzh.replayserver.service.ReplayScheduler;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring 容器级回放 Mock 全链路与日志集成测试。
 */
@SpringBootTest(
        classes = ReplayServerApplication.class,
        properties = "replay-server.rocketmq.enable-global-listener=true")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
class ReplaySpringFlowIntegrationTest {

    private static final String INSTANCE_ID = "instance-001";

    private static final String SCHEDULER_LOG_INSTANCE_ID = "scheduler-log-instance";

    private final ReplayTableDescriptor rawEventTable =
            new ReplayTableDescriptor("event_table", 7, 1001, 1, ReplayTableType.PERIODIC);

    private final ReplayTableDescriptor eventTable = rawEventTable.withType(ReplayTableType.EVENT);

    private final ReplayTableDescriptor periodicTable =
            new ReplayTableDescriptor("periodic_table", 8, 1003, 3, ReplayTableType.PERIODIC);

    @Autowired
    private ReplayGlobalBroadcastListener globalListener;

    @Autowired
    private ReplayInstanceBroadcastMessageHandler instanceHandler;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReplaySessionManager sessionManager;

    @Autowired
    private ReplayMessageConstants messageConstants;

    @Autowired
    private ReplayMetrics replayMetrics;

    @Autowired
    private ReplaySituationPublisher situationPublisher;

    @SpyBean
    private ReplayScheduler replayScheduler;

    @MockBean
    private ReplayTimeControlRepository timeControlRepository;

    @MockBean
    private ReplayTableDiscoveryRepository tableDiscoveryRepository;

    @MockBean
    private ReplayFrameRepository frameRepository;

    @MockBean
    private ReplayRocketMqSender rocketMqSender;

    /**
     * 准备每个用例的 Mock 基线。
     */
    @BeforeEach
    void setUp() {
        Mockito.when(timeControlRepository.resolveTimeRange(INSTANCE_ID))
                .thenReturn(new ReplayTimeRange(1_000L, 2_000L));
        Mockito.when(tableDiscoveryRepository.discoverTables(INSTANCE_ID))
                .thenReturn(Arrays.asList(rawEventTable, periodicTable));
    }

    /**
     * 清理测试产生的会话和后台调度。
     */
    @AfterEach
    void tearDown() {
        for (ReplaySession session : sessionManager.getAllSessions()) {
            replayScheduler.cancel(session.getInstanceId());
            sessionManager.stopSession(session.getInstanceId());
            sessionManager.removeSession(session.getInstanceId());
        }
    }

    /**
     * 验证 Spring 容器真实装配的全链路消息流和成功路径结构化日志。
     *
     * @param output 捕获的日志输出。
     * @throws Exception MockMvc 调用异常。
     */
    @Test
    void shouldRunSpringMockReplayFlowAndEmitStructuredSuccessLogs(CapturedOutput output) throws Exception {
        Mockito.doNothing().when(replayScheduler).schedule(Mockito.any(ReplaySession.class));
        Mockito.doNothing().when(replayScheduler).cancel(Mockito.anyString());
        Mockito.when(frameRepository.findForwardJumpEventFrames(Mockito.eq(eventTable),
                        Mockito.anyLong(), Mockito.eq(1_700L), Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(frame(eventTable, 1_600L)));
        Mockito.when(frameRepository.findPeriodicLastFrame(periodicTable, 1_700L))
                .thenReturn(java.util.Optional.of(frame(periodicTable, 1_650L)));

        globalListener.onMessage(globalMessage(messageConstants.getGlobalCreateMessageCode()));

        ReplaySession session = sessionManager.requireSession(INSTANCE_ID);
        Assertions.assertEquals(ReplaySessionState.READY, session.getState());
        Assertions.assertNull(session.getBroadcastConsumerHandle());
        Mockito.verify(rocketMqSender, Mockito.never()).send(
                Mockito.eq(TopicConstants.buildInstanceBroadcastTopic(INSTANCE_ID)),
                Mockito.any(byte[].class));

        postControl("/api/replay/instances/" + INSTANCE_ID + "/start", null);
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
        Mockito.verify(replayScheduler).schedule(session);

        postControl("/api/replay/instances/" + INSTANCE_ID + "/pause", null);
        Assertions.assertEquals(ReplaySessionState.PAUSED, session.getState());

        postControl("/api/replay/instances/" + INSTANCE_ID + "/resume", null);
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());

        postControl("/api/replay/instances/" + INSTANCE_ID + "/rate", "{\"rate\":2.0}");
        Assertions.assertEquals(2.0D, session.getRate(), 0.0001D);

        postControl("/api/replay/instances/" + INSTANCE_ID + "/jump", "{\"time\":1700}");
        Assertions.assertEquals(1_700L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(1L, replayMetrics.jumpCount());

        globalListener.onMessage(globalMessage(messageConstants.getGlobalStopMessageCode()));
        Assertions.assertFalse(sessionManager.getSession(INSTANCE_ID).isPresent());

        assertLifecycleSuccessLogs(output);
        assertControlSuccessLogs(output);
    }

    /**
     * 验证 Spring 容器真实装配的失败路径结构化日志。
     *
     * @param output 捕获的日志输出。
     */
    @Test
    void shouldEmitStructuredFailureLogsInSpringContext(CapturedOutput output) {
        globalListener.onMessage(invalidMessage(TopicConstants.GLOBAL_BROADCAST_TOPIC));
        instanceHandler.handle(INSTANCE_ID, invalidMessage(TopicConstants.buildInstanceBroadcastTopic(INSTANCE_ID)));

        globalListener.onMessage(globalMessage(messageConstants.getGlobalCreateMessageCode()));

        Mockito.doThrow(BusinessException.state("send boom"))
                .when(rocketMqSender)
                .send(Mockito.eq(TopicConstants.buildInstanceSituationTopic(INSTANCE_ID)), Mockito.any(byte[].class));
        Assertions.assertThrows(BusinessException.class,
                () -> situationPublisher.publish(INSTANCE_ID, frame(eventTable, 1_600L)));

        assertFailureLogs(output);
    }

    /**
     * 验证调度后台安全入口的查询失败结构化日志。
     *
     * @param output 捕获的日志输出。
     * @throws Exception 等待日志输出异常。
     */
    @Test
    void shouldEmitStructuredSchedulerFailureLog(CapturedOutput output) throws Exception {
        ReplaySession session = runningSchedulerLogSession();
        Mockito.when(frameRepository.findWindowFrames(Mockito.eq(eventTable),
                        Mockito.anyLong(), Mockito.anyLong(), Mockito.any(ReplayCursor.class)))
                .thenThrow(new IllegalStateException("tdengine boom"));

        replayScheduler.schedule(session);

        waitUntilOutputContains(output, "result=replay_scheduler_tick_failed");
        replayScheduler.cancel(SCHEDULER_LOG_INSTANCE_ID);

        String logs = output.getOut();
        Assertions.assertTrue(logs.contains("result=replay_scheduler_tick_failed"));
        Assertions.assertTrue(logs.contains("instanceId=" + SCHEDULER_LOG_INSTANCE_ID));
        Assertions.assertTrue(logs.contains("topic=-"));
        Assertions.assertTrue(logs.contains("messageType=-1"));
        Assertions.assertTrue(logs.contains("messageCode=-1"));
        Assertions.assertTrue(logs.contains("senderId=-1"));
        Assertions.assertTrue(logs.contains("currentReplayTime="));
        Assertions.assertTrue(logs.contains("lastDispatchedSimTime="));
        Assertions.assertTrue(logs.contains("rate=1.0"));
        Assertions.assertTrue(logs.contains("replayState=FAILED"));
        Assertions.assertTrue(logs.contains("reason=tdengine boom"));
    }

    /**
     * 断言生命周期成功日志字段。
     *
     * @param output 捕获的日志输出。
     */
    private void assertLifecycleSuccessLogs(CapturedOutput output) {
        String logs = output.getOut();
        Assertions.assertTrue(logs.contains("result=replay_create_success"));
        Assertions.assertTrue(logs.contains("result=replay_stop_success"));
        Assertions.assertTrue(logs.contains("instanceId=" + INSTANCE_ID));
        Assertions.assertTrue(logs.contains("topic=-"));
        Assertions.assertTrue(logs.contains("messageType=-1"));
        Assertions.assertTrue(logs.contains("messageCode=-1"));
        Assertions.assertTrue(logs.contains("senderId=-1"));
        Assertions.assertTrue(logs.contains("currentReplayTime="));
        Assertions.assertTrue(logs.contains("lastDispatchedSimTime="));
        Assertions.assertTrue(logs.contains("rate="));
        Assertions.assertTrue(logs.contains("replayState=READY"));
        Assertions.assertTrue(logs.contains("replayState=STOPPED"));
    }

    /**
     * 断言控制成功日志字段。
     *
     * @param output 捕获的日志输出。
     */
    private void assertControlSuccessLogs(CapturedOutput output) {
        String logs = output.getOut();
        Assertions.assertTrue(logs.contains("result=replay_start_success"));
        Assertions.assertTrue(logs.contains("result=replay_pause_success"));
        Assertions.assertTrue(logs.contains("result=replay_resume_success"));
        Assertions.assertTrue(logs.contains("result=replay_rate_success"));
        Assertions.assertTrue(logs.contains("result=replay_jump_success"));
        Assertions.assertTrue(logs.contains("messageType=" + messageConstants.getInstanceControlMessageType()));
        Assertions.assertTrue(logs.contains("messageCode=" + messageConstants.getInstanceStartMessageCode()));
        Assertions.assertTrue(logs.contains("messageCode=" + messageConstants.getInstancePauseMessageCode()));
        Assertions.assertTrue(logs.contains("messageCode=" + messageConstants.getInstanceResumeMessageCode()));
        Assertions.assertTrue(logs.contains("messageCode=" + messageConstants.getInstanceRateMessageCode()));
        Assertions.assertTrue(logs.contains("messageCode=" + messageConstants.getInstanceJumpMessageCode()));
        Assertions.assertTrue(logs.contains("senderId=0"));
        Assertions.assertTrue(logs.contains("currentReplayTime="));
        Assertions.assertTrue(logs.contains("lastDispatchedSimTime="));
        Assertions.assertTrue(logs.contains("rate="));
        Assertions.assertTrue(logs.contains("replayState=RUNNING"));
        Assertions.assertTrue(logs.contains("replayState=PAUSED"));
    }

    /**
     * 断言失败路径日志字段。
     *
     * @param output 捕获的日志输出。
     */
    private void assertFailureLogs(CapturedOutput output) {
        String logs = output.getOut();
        Assertions.assertTrue(logs.contains("result=replay_protocol_parse_failed"));
        Assertions.assertTrue(logs.contains("topic=" + TopicConstants.GLOBAL_BROADCAST_TOPIC));
        Assertions.assertTrue(logs.contains("topic=" + TopicConstants.buildInstanceBroadcastTopic(INSTANCE_ID)));
        Assertions.assertTrue(logs.contains("messageType=-1"));
        Assertions.assertTrue(logs.contains("messageCode=-1"));
        Assertions.assertTrue(logs.contains("senderId=-1"));
        Assertions.assertTrue(logs.contains("reason=协议长度非法"));
        Assertions.assertTrue(logs.contains("result=replay_publish_retry_failed"));
        Assertions.assertTrue(logs.contains("topic=" + TopicConstants.buildInstanceSituationTopic(INSTANCE_ID)));
        Assertions.assertTrue(logs.contains("simtime=1600"));
        Assertions.assertTrue(logs.contains("attempt=1"));
        Assertions.assertTrue(logs.contains("reason=send boom"));
    }

    /**
     * 等待捕获日志出现指定文本。
     *
     * @param output 捕获的日志输出。
     * @param expectedText 期望文本。
     * @throws Exception 等待异常。
     */
    private void waitUntilOutputContains(CapturedOutput output, String expectedText) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5L);
        while (System.currentTimeMillis() <= deadline) {
            if (output.getOut().contains(expectedText)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50L);
        }
        Assertions.fail("等待日志输出超时: " + expectedText);
    }

    /**
     * 通过 HTTP 接口发送回放控制请求。
     *
     * @param path 请求路径。
     * @param body 请求体。
     * @throws Exception MockMvc 调用异常。
     */
    private void postControl(String path, String body) throws Exception {
        if (body == null) {
            mockMvc.perform(post(path))
                    .andExpect(status().isOk());
            return;
        }
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    /**
     * 构造全局生命周期消息。
     *
     * @param messageCode 消息编号。
     * @return RocketMQ 原始消息。
     */
    private MessageExt globalMessage(int messageCode) {
        return message(TopicConstants.GLOBAL_BROADCAST_TOPIC,
                protocolBody(0, messageConstants.getGlobalMessageType(), messageCode,
                        "{\"instanceId\":\"" + INSTANCE_ID + "\"}"));
    }

    /**
     * 构造非法协议消息。
     *
     * @param topic Topic 名称。
     * @return RocketMQ 原始消息。
     */
    private MessageExt invalidMessage(String topic) {
        return message(topic, new byte[]{1, 2, 3});
    }

    /**
     * 构造 RocketMQ 原始消息。
     *
     * @param topic Topic 名称。
     * @param body 消息体。
     * @return RocketMQ 原始消息。
     */
    private MessageExt message(String topic, byte[] body) {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(topic);
        messageExt.setBody(body);
        return messageExt;
    }

    /**
     * 构造平台协议体。
     *
     * @param senderId 发送方 ID。
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @param rawJson JSON 载荷。
     * @return 协议体。
     */
    private byte[] protocolBody(int senderId, int messageType, int messageCode, String rawJson) {
        return ProtocolMessageUtil.buildData(
                senderId,
                (short) messageType,
                messageCode,
                rawJson.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造回放帧。
     *
     * @param tableDescriptor 表描述。
     * @param simTime 仿真时间。
     * @return 回放帧。
     */
    private ReplayFrame frame(ReplayTableDescriptor tableDescriptor, long simTime) {
        return new ReplayFrame(
                tableDescriptor.getTableName(),
                tableDescriptor.getSenderId(),
                tableDescriptor.getMessageType(),
                tableDescriptor.getMessageCode(),
                simTime,
                ("{\"simTime\":" + simTime + "}").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建用于后台调度失败日志的运行中会话。
     *
     * @return 运行中会话。
     */
    private ReplaySession runningSchedulerLogSession() {
        ReplaySession session = new ReplaySession(
                SCHEDULER_LOG_INSTANCE_ID,
                new ReplayTimeRange(1_000L, 2_000L),
                Collections.singletonList(eventTable),
                Collections.singletonList(periodicTable),
                new ReplayClock(1_000L, 2_000L));
        session.markReady();
        session.start();
        return session;
    }
}
