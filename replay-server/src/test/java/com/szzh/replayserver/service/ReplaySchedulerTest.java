package com.szzh.replayserver.service;

import com.szzh.common.exception.BusinessException;
import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.query.ReplayCursor;
import com.szzh.replayserver.model.query.ReplayFrame;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import com.szzh.replayserver.mq.ReplaySituationPublisher;
import com.szzh.replayserver.repository.ReplayFrameRepository;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放调度器测试。
 */
class ReplaySchedulerTest {

    private final ReplayTableDescriptor eventTable =
            new ReplayTableDescriptor("event_table", 7, 1001, 2, ReplayTableType.EVENT);

    private final ReplayTableDescriptor periodicTable =
            new ReplayTableDescriptor("periodic_table", 8, 2001, 9, ReplayTableType.PERIODIC);

    /**
     * 验证运行中会话会查询窗口、发布归并帧并在成功后推进水位。
     */
    @Test
    void shouldQueryPublishAndAdvanceWatermarkWhenRunning() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_500L);
        ReplayFrame eventFrame = frame(eventTable, 1_200L);
        ReplayFrame periodicFrame = frame(periodicTable, 1_300L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(1_000L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(eventFrame));
        Mockito.when(repository.findWindowFrames(Mockito.eq(periodicTable), Mockito.eq(1_000L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(periodicFrame));
        ReplayScheduler scheduler = new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L);

        scheduler.tick(session);

        ArgumentCaptor<ReplayFrame> frameCaptor = ArgumentCaptor.forClass(ReplayFrame.class);
        Mockito.verify(publisher, Mockito.times(2)).publish(Mockito.eq("instance-001"), frameCaptor.capture());
        Assertions.assertEquals(Arrays.asList(eventFrame, periodicFrame), frameCaptor.getAllValues());
        Assertions.assertEquals(1_500L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(2L, session.dispatchedFrameCount());
    }

    /**
     * 验证暂停会话不会查询新窗口。
     */
    @Test
    void shouldSkipPausedSession() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        session.pause();
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        ReplayScheduler scheduler = new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L);

        scheduler.tick(session);

        Mockito.verifyNoInteractions(repository);
        Mockito.verifyNoInteractions(publisher);
        Assertions.assertEquals(1_000L, session.getLastDispatchedSimTime());
    }

    /**
     * 验证发布失败时会话失败且水位不推进。
     */
    @Test
    void shouldNotAdvanceWatermarkWhenPublishFails() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_500L);
        ReplayFrame frame = frame(eventTable, 1_200L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(1_000L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(frame));
        Mockito.when(repository.findWindowFrames(Mockito.eq(periodicTable), Mockito.eq(1_000L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.emptyList());
        Mockito.doThrow(BusinessException.state("publish boom"))
                .when(publisher)
                .publish(Mockito.anyString(), Mockito.any(ReplayFrame.class));
        ReplayScheduler scheduler = new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L);

        Assertions.assertThrows(BusinessException.class, () -> scheduler.tick(session));

        Assertions.assertEquals(ReplaySessionState.FAILED, session.getState());
        Assertions.assertEquals(1_000L, session.getLastDispatchedSimTime());
    }

    /**
     * 验证查询异常会记录查询失败指标。
     */
    @Test
    void shouldRecordQueryFailureWhenRepositoryThrows() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_500L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        ReplayMetrics metrics = new ReplayMetrics();
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(1_000L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenThrow(new IllegalStateException("tdengine boom"));
        ReplayScheduler scheduler =
                new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L, metrics);

        Assertions.assertThrows(IllegalStateException.class, () -> scheduler.tick(session));

        Assertions.assertEquals(1L, metrics.queryFailureCount());
        Assertions.assertEquals(ReplaySessionState.FAILED, session.getState());
    }

    /**
     * 验证到达结束时间后发布最后窗口并进入完成态。
     */
    @Test
    void shouldCompleteWhenWindowReachesEndTime() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        Object handle = new Object();
        session.setBroadcastConsumerHandle(handle);
        wallClock.set(2_000L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findWindowFrames(Mockito.any(ReplayTableDescriptor.class),
                        Mockito.eq(1_000L), Mockito.eq(2_000L), Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.emptyList());
        ReplayScheduler scheduler = new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L);

        scheduler.tick(session);

        Assertions.assertEquals(ReplaySessionState.COMPLETED, session.getState());
        Assertions.assertEquals(2_000L, session.getLastDispatchedSimTime());
        Assertions.assertSame(handle, session.getBroadcastConsumerHandle());
        Mockito.verifyNoInteractions(publisher);
    }

    /**
     * 创建运行中的测试会话。
     *
     * @param wallClock 墙钟时间。
     * @return 回放会话。
     */
    private ReplaySession runningSession(AtomicLong wallClock) {
        ReplayTimeRange timeRange = new ReplayTimeRange(1_000L, 2_000L);
        ReplayClock clock = new ReplayClock(1_000L, 2_000L, wallClock::get);
        ReplaySession session = new ReplaySession(
                "instance-001",
                timeRange,
                Collections.singletonList(eventTable),
                Collections.singletonList(periodicTable),
                clock);
        session.markReady();
        session.start();
        return session;
    }

    /**
     * 创建测试帧。
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
                new byte[]{1});
    }
}
