package com.szzh.replayserver.service;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
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
     * 验证创建回放任务会查询元数据并创建 READY 会话，但不再订阅控制 topic 或发布元信息。
     */
    @Test
    void shouldCreateReplaySessionWithoutControlSubscriptionOrMetadata() {
        Fixture fixture = new Fixture();
        fixture.stubCreateSuccess();

        fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}"));

        ReplaySession session = fixture.sessionManager.requireSession("instance-001");
        Assertions.assertEquals(ReplaySessionState.READY, session.getState());
        Assertions.assertEquals(1, session.getEventTables().size());
        Assertions.assertEquals(1, session.getPeriodicTables().size());
        Assertions.assertNull(session.getBroadcastConsumerHandle());
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
        Assertions.assertEquals(1, fixture.sessionManager.size());
    }

    /**
     * 验证创建过程中表发现失败会抛出异常且不保留会话。
     */
    @Test
    void shouldNotKeepSessionWhenCreateMetadataDiscoveryFails() {
        Fixture fixture = new Fixture();
        Mockito.when(fixture.timeControlRepository.resolveTimeRange("instance-001"))
                .thenReturn(new ReplayTimeRange(1_000L, 2_000L));
        Mockito.when(fixture.tableDiscoveryRepository.discoverTables("instance-001"))
                .thenReturn(Collections.emptyList());
        Mockito.when(fixture.tableClassifier.classify(Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        Assertions.assertThrows(BusinessException.class,
                () -> fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}")));

        Assertions.assertFalse(fixture.sessionManager.getSession("instance-001").isPresent());
    }

    /**
     * 验证停止任务会停止调度并移除会话。
     */
    @Test
    void shouldStopReplayAndReleaseResources() {
        Fixture fixture = new Fixture();
        fixture.stubCreateSuccess();
        fixture.service.handleCreate(protocolData("{\"instanceId\":\"instance-001\"}"));

        fixture.service.handleStop(protocolData("{\"instanceId\":\"instance-001\"}"));

        Mockito.verify(fixture.scheduler).cancel("instance-001");
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

        private final ReplayScheduler scheduler = Mockito.mock(ReplayScheduler.class);

        private final ReplayLifecycleService service = new ReplayLifecycleService(
                sessionManager,
                timeControlRepository,
                tableDiscoveryRepository,
                tableClassifier,
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
        }
    }
}
