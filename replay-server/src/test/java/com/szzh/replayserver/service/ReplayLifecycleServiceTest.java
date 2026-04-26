package com.szzh.replayserver.service;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import com.szzh.replayserver.mq.ReplayTopicSubscriptionManager;
import com.szzh.replayserver.repository.ReplayTableDiscoveryRepository;
import com.szzh.replayserver.repository.ReplayTimeControlRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放生命周期服务测试。
 */
class ReplayLifecycleServiceTest {

    /**
     * 验证创建回放任务会查询元数据、创建会话、订阅控制 topic 并发布元信息。
     */
    @Test
    void shouldCreateReplaySessionAndPublishMetadata() {
        Fixture fixture = new Fixture();
        fixture.stubCreateSuccess();

        fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}"));

        ReplaySession session = fixture.sessionManager.requireSession("instance-001");
        Assertions.assertEquals(ReplaySessionState.READY, session.getState());
        Assertions.assertEquals(1, session.getEventTables().size());
        Assertions.assertEquals(1, session.getPeriodicTables().size());
        Assertions.assertNotNull(session.getBroadcastConsumerHandle());
        Mockito.verify(fixture.subscriptionManager).subscribe("instance-001");
        Mockito.verify(fixture.metadataService).publishMetadata(session);
    }

    /**
     * 验证重复创建同一非终态实例时保持幂等。
     */
    @Test
    void shouldIgnoreDuplicateCreateForActiveSession() {
        Fixture fixture = new Fixture();
        fixture.stubCreateSuccess();

        fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}"));
        fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}"));

        Mockito.verify(fixture.timeControlRepository, Mockito.times(1)).resolveTimeRange("instance-001");
        Mockito.verify(fixture.metadataService, Mockito.times(1)).publishMetadata(Mockito.any(ReplaySession.class));
        Assertions.assertEquals(1, fixture.sessionManager.size());
    }

    /**
     * 验证创建过程中元信息发布失败会清理订阅并标记失败。
     */
    @Test
    void shouldCleanupSubscriptionAndMarkFailedWhenCreateFails() {
        Fixture fixture = new Fixture();
        fixture.stubCreateSuccess();
        Mockito.doThrow(BusinessException.state("metadata boom"))
                .when(fixture.metadataService)
                .publishMetadata(Mockito.any(ReplaySession.class));

        Assertions.assertThrows(BusinessException.class,
                () -> fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}")));

        ReplaySession session = fixture.sessionManager.requireSession("instance-001");
        Assertions.assertEquals(ReplaySessionState.FAILED, session.getState());
        Mockito.verify(fixture.subscriptionManager).unsubscribe("instance-001");
    }

    /**
     * 验证停止任务会停止调度、取消控制订阅并移除会话。
     */
    @Test
    void shouldStopReplayAndReleaseResources() {
        Fixture fixture = new Fixture();
        fixture.stubCreateSuccess();
        fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}"));

        fixture.service.handleStop(protocolData("{\"instanceId\":\"instance-001\"}"));

        Mockito.verify(fixture.scheduler).cancel("instance-001");
        Mockito.verify(fixture.subscriptionManager).unsubscribe("instance-001");
        Assertions.assertFalse(fixture.sessionManager.getSession("instance-001").isPresent());
    }

    /**
     * 验证停止自然完成会话会释放调度、订阅和会话对象。
     */
    @Test
    void shouldStopCompletedReplayAndReleaseResources() {
        Fixture fixture = new Fixture();
        fixture.stubCreateSuccess();
        fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}"));
        ReplaySession session = fixture.sessionManager.requireSession("instance-001");
        session.start();
        session.markCompleted();

        fixture.service.handleStop(protocolData("{\"instanceId\":\"instance-001\"}"));

        Mockito.verify(fixture.scheduler).cancel("instance-001");
        Mockito.verify(fixture.subscriptionManager).unsubscribe("instance-001");
        Assertions.assertFalse(fixture.sessionManager.getSession("instance-001").isPresent());
    }

    /**
     * 验证停止失败会话会释放调度、订阅和会话对象。
     */
    @Test
    void shouldStopFailedReplayAndReleaseResources() {
        Fixture fixture = new Fixture();
        fixture.stubCreateSuccess();
        fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}"));
        ReplaySession session = fixture.sessionManager.requireSession("instance-001");
        session.markFailed("boom");

        fixture.service.handleStop(protocolData("{\"instanceId\":\"instance-001\"}"));

        Mockito.verify(fixture.scheduler).cancel("instance-001");
        Mockito.verify(fixture.subscriptionManager).unsubscribe("instance-001");
        Assertions.assertFalse(fixture.sessionManager.getSession("instance-001").isPresent());
    }

    /**
     * 构建协议数据。
     *
     * @param json JSON 载荷。
     * @return 协议数据。
     */
    private static ProtocolData protocolData(String json) {
        ProtocolData protocolData = new ProtocolData();
        protocolData.setRawData(json.getBytes(StandardCharsets.UTF_8));
        return protocolData;
    }

    /**
     * 生命周期测试夹具。
     */
    private static final class Fixture {

        private final ReplaySessionManager sessionManager =
                new ReplaySessionManager(new AtomicLong(1_000L)::get);

        private final ReplayTimeControlRepository timeControlRepository =
                Mockito.mock(ReplayTimeControlRepository.class);

        private final ReplayTableDiscoveryRepository tableDiscoveryRepository =
                Mockito.mock(ReplayTableDiscoveryRepository.class);

        private final ReplayTableClassifier tableClassifier = Mockito.mock(ReplayTableClassifier.class);

        private final ReplayTopicSubscriptionManager subscriptionManager =
                Mockito.mock(ReplayTopicSubscriptionManager.class);

        private final ReplayMetadataService metadataService = Mockito.mock(ReplayMetadataService.class);

        private final ReplayScheduler scheduler = Mockito.mock(ReplayScheduler.class);

        private final ReplayLifecycleService service = new ReplayLifecycleService(
                sessionManager,
                timeControlRepository,
                tableDiscoveryRepository,
                tableClassifier,
                subscriptionManager,
                metadataService,
                scheduler);

        /**
         * 准备创建成功的 Repository 和服务桩。
         */
        private void stubCreateSuccess() {
            ReplayTableDescriptor eventTable =
                    new ReplayTableDescriptor("event_table", 7, 1001, 1, ReplayTableType.EVENT);
            ReplayTableDescriptor periodicTable =
                    new ReplayTableDescriptor("periodic_table", 8, 2001, 9, ReplayTableType.PERIODIC);
            Mockito.when(timeControlRepository.resolveTimeRange("instance-001"))
                    .thenReturn(new ReplayTimeRange(1_000L, 2_000L));
            Mockito.when(tableDiscoveryRepository.discoverTables("instance-001"))
                    .thenReturn(Arrays.asList(eventTable.withType(ReplayTableType.PERIODIC), periodicTable));
            Mockito.when(tableClassifier.classify(Mockito.anyList()))
                    .thenReturn(Arrays.asList(eventTable, periodicTable));
            Mockito.when(subscriptionManager.subscribe("instance-001")).thenReturn(true);
        }
    }
}
