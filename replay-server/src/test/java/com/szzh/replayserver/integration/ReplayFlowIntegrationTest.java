package com.szzh.replayserver.integration;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import com.szzh.common.topic.TopicConstants;
import com.szzh.replayserver.config.ReplayServerProperties;
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
import com.szzh.replayserver.mq.ReplayTopicSubscriptionManager;
import com.szzh.replayserver.repository.ReplayFrameRepository;
import com.szzh.replayserver.repository.ReplayTableDiscoveryRepository;
import com.szzh.replayserver.repository.ReplayTimeControlRepository;
import com.szzh.replayserver.service.ReplayControlService;
import com.szzh.replayserver.service.ReplayFrameMergeService;
import com.szzh.replayserver.service.ReplayJumpService;
import com.szzh.replayserver.service.ReplayLifecycleService;
import com.szzh.replayserver.service.ReplayMetadataService;
import com.szzh.replayserver.service.ReplayScheduler;
import com.szzh.replayserver.service.ReplayTableClassifier;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放 Mock 全链路集成测试。
 */
class ReplayFlowIntegrationTest {

    private Fixture fixture;

    /**
     * 清理测试调度器资源。
     */
    @AfterEach
    void cleanup() {
        if (fixture != null) {
            fixture.scheduler.shutdown();
        }
    }

    /**
     * 验证创建、元信息、启动、暂停、继续、倍速、跳转、停止全链路。
     */
    @Test
    void shouldCompleteReplayControlFlowWithMockRepositoryAndRocketMq() {
        fixture = new Fixture();
        fixture.stubReplayMetadata();
        fixture.stubContinuousWindow();
        fixture.stubForwardJump();
        fixture.stubBackwardJump();

        fixture.sendGlobalCreate();

        ReplaySession session = fixture.sessionManager.requireSession("instance-001");
        Assertions.assertEquals(ReplaySessionState.READY, session.getState());
        Mockito.verify(fixture.subscriptionManager).subscribe("instance-001");
        Assertions.assertEquals(1, fixture.sender.recordsByTopic(
                TopicConstants.buildInstanceBroadcastTopic("instance-001")).size());
        ProtocolData metadataProtocol = ProtocolMessageUtil.parseData(fixture.sender.recordsByTopic(
                TopicConstants.buildInstanceBroadcastTopic("instance-001")).get(0).body);
        Assertions.assertEquals(fixture.messageConstants.getInstanceMetadataMessageCode(),
                metadataProtocol.getMessageCode());

        fixture.sendControl(fixture.messageConstants.getInstanceStartMessageCode(), "{}");
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
        Assertions.assertEquals(1, fixture.scheduler.scheduleCount.get());

        fixture.wallClock.set(1_250L);
        fixture.scheduler.tick(session);
        Assertions.assertEquals(1_250L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(2L, fixture.metrics.publishedSuccessCount());

        fixture.sendControl(fixture.messageConstants.getInstancePauseMessageCode(), "{}");
        Assertions.assertEquals(ReplaySessionState.PAUSED, session.getState());
        Assertions.assertEquals(1, fixture.scheduler.cancelCount.get());

        fixture.sendControl(fixture.messageConstants.getInstanceResumeMessageCode(), "{}");
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
        Assertions.assertEquals(2, fixture.scheduler.scheduleCount.get());

        fixture.sendControl(fixture.messageConstants.getInstanceRateMessageCode(), "{\"rate\":2.0}");
        Assertions.assertEquals(2.0D, session.getRate(), 0.0001D);

        fixture.sendControl(fixture.messageConstants.getInstanceJumpMessageCode(), "{\"time\":1700}");
        Assertions.assertEquals(1_700L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(1L, fixture.metrics.jumpCount());

        fixture.sendControl(fixture.messageConstants.getInstanceJumpMessageCode(), "{\"time\":1100}");
        Assertions.assertEquals(1_100L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(2L, fixture.metrics.jumpCount());

        fixture.sendGlobalStop();

        Assertions.assertFalse(fixture.sessionManager.getSession("instance-001").isPresent());
        Mockito.verify(fixture.subscriptionManager).unsubscribe("instance-001");
        Assertions.assertEquals(0L, fixture.metrics.activeSessionCount());
        Assertions.assertEquals(6L, fixture.sender.recordsByTopic(
                TopicConstants.buildInstanceSituationTopic("instance-001")).size());
    }

    /**
     * 验证跳转发布失败后会话进入失败态，且控制层不恢复调度。
     */
    @Test
    void shouldMarkFailedAndKeepSchedulerCancelledWhenJumpPublishFails() {
        fixture = new Fixture();
        fixture.stubReplayMetadata();
        fixture.stubContinuousWindow();
        fixture.stubForwardJump();

        fixture.sendGlobalCreate();
        ReplaySession session = fixture.sessionManager.requireSession("instance-001");
        fixture.sendControl(fixture.messageConstants.getInstanceStartMessageCode(), "{}");
        fixture.wallClock.set(1_250L);
        fixture.scheduler.tick(session);
        int scheduleCountBeforeJump = fixture.scheduler.scheduleCount.get();

        fixture.sender.failSituationSend();
        fixture.sendControl(fixture.messageConstants.getInstanceJumpMessageCode(), "{\"time\":1700}");

        Assertions.assertEquals(ReplaySessionState.FAILED, session.getState());
        Assertions.assertEquals(1_250L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(scheduleCountBeforeJump, fixture.scheduler.scheduleCount.get());
        Assertions.assertEquals(1, fixture.scheduler.cancelCount.get());
    }

    /**
     * 回放全链路测试夹具。
     */
    private static final class Fixture {

        private final AtomicLong wallClock = new AtomicLong(1_000L);

        private final ReplayServerProperties properties = properties();

        private final ReplayMessageConstants messageConstants = new ReplayMessageConstants(properties);

        private final ReplaySessionManager sessionManager = new ReplaySessionManager(wallClock::get);

        private final ReplayMetrics metrics = new ReplayMetrics(sessionManager);

        private final ReplayFrameRepository frameRepository = Mockito.mock(ReplayFrameRepository.class);

        private final ReplayTimeControlRepository timeControlRepository =
                Mockito.mock(ReplayTimeControlRepository.class);

        private final ReplayTableDiscoveryRepository tableDiscoveryRepository =
                Mockito.mock(ReplayTableDiscoveryRepository.class);

        private final ReplayTopicSubscriptionManager subscriptionManager =
                Mockito.mock(ReplayTopicSubscriptionManager.class);

        private final RecordingSender sender = new RecordingSender();

        private final ReplaySituationPublisher situationPublisher =
                new ReplaySituationPublisher(sender, 1, metrics);

        private final ReplayFrameMergeService frameMergeService = new ReplayFrameMergeService();

        private final ManualReplayScheduler scheduler =
                new ManualReplayScheduler(frameRepository, frameMergeService, situationPublisher, metrics);

        private final ReplayJumpService jumpService =
                new ReplayJumpService(frameRepository, frameMergeService, situationPublisher, 20, metrics);

        private final ReplayControlService controlService =
                new ReplayControlService(sessionManager, scheduler, jumpService, metrics);

        private final ReplayInstanceBroadcastMessageHandler instanceHandler =
                new ReplayInstanceBroadcastMessageHandler(messageConstants, controlService);

        private final ReplayMetadataService metadataService =
                new ReplayMetadataService(sender, messageConstants);

        private final ReplayLifecycleService lifecycleService = new ReplayLifecycleService(
                sessionManager,
                timeControlRepository,
                tableDiscoveryRepository,
                new ReplayTableClassifier(properties),
                subscriptionManager,
                metadataService,
                scheduler);

        private final ReplayGlobalBroadcastListener globalListener =
                new ReplayGlobalBroadcastListener(messageConstants);

        private final ReplayTableDescriptor eventTable =
                new ReplayTableDescriptor("event_table", 7, 1001, 1, ReplayTableType.PERIODIC);

        private final ReplayTableDescriptor periodicTable =
                new ReplayTableDescriptor("periodic_table", 8, 2001, 9, ReplayTableType.PERIODIC);

        /**
         * 创建回放全链路测试夹具。
         */
        private Fixture() {
            globalListener.setReplayLifecycleCommandPort(lifecycleService);
            Mockito.when(subscriptionManager.subscribe("instance-001")).thenReturn(true);
        }

        /**
         * 准备回放创建所需元数据。
         */
        private void stubReplayMetadata() {
            Mockito.when(timeControlRepository.resolveTimeRange("instance-001"))
                    .thenReturn(new ReplayTimeRange(1_000L, 2_000L));
            Mockito.when(tableDiscoveryRepository.discoverTables("instance-001"))
                    .thenReturn(Arrays.asList(eventTable, periodicTable));
        }

        /**
         * 准备连续窗口查询结果。
         */
        private void stubContinuousWindow() {
            Mockito.when(frameRepository.findWindowFrames(Mockito.eq(eventTable.withType(ReplayTableType.EVENT)),
                            Mockito.eq(999L), Mockito.eq(1_250L), Mockito.any(ReplayCursor.class)))
                    .thenReturn(Collections.singletonList(frame(eventTable, 1_100L)));
            Mockito.when(frameRepository.findWindowFrames(Mockito.eq(periodicTable),
                            Mockito.eq(999L), Mockito.eq(1_250L), Mockito.any(ReplayCursor.class)))
                    .thenReturn(Collections.singletonList(frame(periodicTable, 1_200L)));
        }

        /**
         * 准备向前跳转查询结果。
         */
        private void stubForwardJump() {
            Mockito.when(frameRepository.findForwardJumpEventFrames(Mockito.eq(eventTable.withType(ReplayTableType.EVENT)),
                            Mockito.eq(1_250L), Mockito.eq(1_700L), Mockito.any(ReplayCursor.class)))
                    .thenReturn(Collections.singletonList(frame(eventTable, 1_600L)));
            Mockito.when(frameRepository.findPeriodicLastFrame(periodicTable, 1_700L))
                    .thenReturn(java.util.Optional.of(frame(periodicTable, 1_650L)));
        }

        /**
         * 准备向后跳转查询结果。
         */
        private void stubBackwardJump() {
            Mockito.when(frameRepository.findBackwardJumpEventFrames(Mockito.eq(eventTable.withType(ReplayTableType.EVENT)),
                            Mockito.eq(1_000L), Mockito.eq(1_100L), Mockito.any(ReplayCursor.class)))
                    .thenReturn(Collections.singletonList(frame(eventTable, 1_050L)));
            Mockito.when(frameRepository.findPeriodicLastFrame(periodicTable, 1_100L))
                    .thenReturn(java.util.Optional.of(frame(periodicTable, 1_080L)));
        }

        /**
         * 发送全局创建消息。
         */
        private void sendGlobalCreate() {
            globalListener.onMessage(message(TopicConstants.GLOBAL_BROADCAST_TOPIC,
                    protocolBody(0,
                            messageConstants.getGlobalMessageType(),
                            messageConstants.getGlobalCreateMessageCode(),
                            "{\"instanceId\":\"instance-001\"}")));
        }

        /**
         * 发送全局停止消息。
         */
        private void sendGlobalStop() {
            globalListener.onMessage(message(TopicConstants.GLOBAL_BROADCAST_TOPIC,
                    protocolBody(0,
                            messageConstants.getGlobalMessageType(),
                            messageConstants.getGlobalStopMessageCode(),
                            "{\"instanceId\":\"instance-001\"}")));
        }

        /**
         * 发送实例控制消息。
         *
         * @param messageCode 消息编号。
         * @param rawJson JSON 载荷。
         */
        private void sendControl(int messageCode, String rawJson) {
            instanceHandler.handle("instance-001", message(TopicConstants.buildInstanceBroadcastTopic("instance-001"),
                    protocolBody(0, messageConstants.getInstanceControlMessageType(), messageCode, rawJson)));
        }

        /**
         * 构造测试帧。
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
         * 创建测试配置。
         *
         * @return 测试配置。
         */
        private static ReplayServerProperties properties() {
            ReplayServerProperties properties = new ReplayServerProperties();
            ReplayServerProperties.EventMessage eventMessage = new ReplayServerProperties.EventMessage();
            eventMessage.setMessageType(1001);
            eventMessage.setMessageCodes(Collections.singletonList(1));
            properties.getReplay().getEventMessages().add(eventMessage);
            return properties;
        }
    }

    /**
     * 可手动 tick 的回放调度器。
     */
    private static final class ManualReplayScheduler extends ReplayScheduler {

        private final AtomicInteger scheduleCount = new AtomicInteger();

        private final AtomicInteger cancelCount = new AtomicInteger();

        /**
         * 创建可手动 tick 的回放调度器。
         *
         * @param frameRepository 回放帧 Repository。
         * @param frameMergeService 回放帧归并服务。
         * @param situationPublisher 回放态势发布器。
         * @param replayMetrics 回放指标。
         */
        private ManualReplayScheduler(ReplayFrameRepository frameRepository,
                                      ReplayFrameMergeService frameMergeService,
                                      ReplaySituationPublisher situationPublisher,
                                      ReplayMetrics replayMetrics) {
            super(frameRepository, frameMergeService, situationPublisher, 20, 50L, replayMetrics);
        }

        /**
         * 记录调度启动次数，不启动后台线程。
         *
         * @param session 回放会话。
         */
        @Override
        public void schedule(ReplaySession session) {
            scheduleCount.incrementAndGet();
        }

        /**
         * 记录调度取消次数，不依赖后台线程。
         *
         * @param instanceId 实例 ID。
         */
        @Override
        public void cancel(String instanceId) {
            cancelCount.incrementAndGet();
        }
    }

    /**
     * 记录发送结果的 Mock RocketMQ Sender。
     */
    private static final class RecordingSender implements ReplayRocketMqSender {

        private final List<SendRecord> records = new ArrayList<SendRecord>();

        private volatile boolean failSituationSend;

        /**
         * 设置态势发送失败。
         */
        private void failSituationSend() {
            this.failSituationSend = true;
        }

        /**
         * 记录同步发送消息。
         *
         * @param topic 目标 topic。
         * @param body 消息体。
         */
        @Override
        public void send(String topic, byte[] body) {
            if (failSituationSend && topic.startsWith("situation-")) {
                throw BusinessException.state("send boom");
            }
            records.add(new SendRecord(topic, body));
        }

        /**
         * 按 Topic 查询发送记录。
         *
         * @param topic Topic 名称。
         * @return 发送记录列表。
         */
        private List<SendRecord> recordsByTopic(String topic) {
            List<SendRecord> result = new ArrayList<SendRecord>();
            for (SendRecord record : records) {
                if (topic.equals(record.topic)) {
                    result.add(record);
                }
            }
            return result;
        }
    }

    /**
     * Mock RocketMQ 发送记录。
     */
    private static final class SendRecord {

        private final String topic;

        private final byte[] body;

        /**
         * 创建发送记录。
         *
         * @param topic Topic 名称。
         * @param body 消息体。
         */
        private SendRecord(String topic, byte[] body) {
            this.topic = topic;
            this.body = body;
        }
    }
}
