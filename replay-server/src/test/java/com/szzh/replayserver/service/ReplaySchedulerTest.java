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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(999L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(eventFrame));
        Mockito.when(repository.findWindowFrames(Mockito.eq(periodicTable), Mockito.eq(999L), Mockito.eq(1_500L),
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
        Assertions.assertEquals(999L, session.getLastDispatchedSimTime());
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
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(999L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(frame));
        Mockito.when(repository.findWindowFrames(Mockito.eq(periodicTable), Mockito.eq(999L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.emptyList());
        Mockito.doThrow(BusinessException.state("publish boom"))
                .when(publisher)
                .publish(Mockito.anyString(), Mockito.any(ReplayFrame.class));
        ReplayScheduler scheduler = new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L);

        Assertions.assertThrows(BusinessException.class, () -> scheduler.tick(session));

        Assertions.assertEquals(ReplaySessionState.FAILED, session.getState());
        Assertions.assertEquals(999L, session.getLastDispatchedSimTime());
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
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(999L), Mockito.eq(1_500L),
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
                        Mockito.eq(999L), Mockito.eq(2_000L), Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.emptyList());
        ReplayScheduler scheduler = new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L);

        scheduler.tick(session);

        Assertions.assertEquals(ReplaySessionState.COMPLETED, session.getState());
        Assertions.assertEquals(2_000L, session.getLastDispatchedSimTime());
        Assertions.assertSame(handle, session.getBroadcastConsumerHandle());
        Mockito.verifyNoInteractions(publisher);
    }

    /**
     * 验证连续回放首次窗口包含仿真开始时间帧。
     */
    @Test
    void shouldPublishFrameAtSimulationStartTimeOnFirstTick() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        ReplayFrame firstFrame = frame(eventTable, 1_000L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(999L), Mockito.eq(1_000L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(firstFrame));
        Mockito.when(repository.findWindowFrames(Mockito.eq(periodicTable), Mockito.eq(999L), Mockito.eq(1_000L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.emptyList());
        ReplayScheduler scheduler = new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L);

        scheduler.tick(session);

        Mockito.verify(publisher).publish("instance-001", firstFrame);
        Assertions.assertEquals(1_000L, session.getLastDispatchedSimTime());
    }

    /**
     * 验证连续调度查询、发布和水位推进期间会阻塞同一会话的时间跳转。
     *
     * @throws Exception 并发等待异常。
     */
    @Test
    void shouldBlockJumpWhileSchedulerTickIsInProgress() throws Exception {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_200L);
        CountDownLatch tickQueryStarted = new CountDownLatch(1);
        CountDownLatch releaseTickQuery = new CountDownLatch(1);
        AtomicBoolean jumpQueriedBeforeTickReleased = new AtomicBoolean(false);
        AtomicReference<Throwable> tickError = new AtomicReference<Throwable>();
        AtomicReference<Throwable> jumpError = new AtomicReference<Throwable>();
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(999L), Mockito.eq(1_200L),
                        Mockito.any(ReplayCursor.class)))
                .thenAnswer(invocation -> {
                    tickQueryStarted.countDown();
                    if (!releaseTickQuery.await(2L, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待释放 tick 查询超时");
                    }
                    return Collections.emptyList();
                });
        Mockito.when(repository.findWindowFrames(Mockito.eq(periodicTable), Mockito.eq(999L), Mockito.eq(1_200L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.emptyList());
        Mockito.when(repository.findForwardJumpEventFrames(Mockito.eq(eventTable), Mockito.eq(1_200L),
                        Mockito.eq(1_500L), Mockito.any(ReplayCursor.class)))
                .thenAnswer(invocation -> {
                    if (releaseTickQuery.getCount() > 0L) {
                        jumpQueriedBeforeTickReleased.set(true);
                    }
                    return Collections.emptyList();
                });
        Mockito.when(repository.findPeriodicLastFrame(periodicTable, 1_500L))
                .thenReturn(java.util.Optional.empty());
        ReplayScheduler scheduler = new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L);
        ReplayJumpService jumpService = new ReplayJumpService(repository, new ReplayFrameMergeService(), publisher, 10);
        Thread tickThread = new Thread(() -> runAndCapture(() -> scheduler.tick(session), tickError), "test-replay-tick");
        Thread jumpThread = new Thread(() -> runAndCapture(() -> jumpService.jump(session, 1_500L), jumpError), "test-replay-jump");

        tickThread.start();
        Assertions.assertTrue(tickQueryStarted.await(2L, TimeUnit.SECONDS));
        jumpThread.start();
        Thread.sleep(200L);

        Assertions.assertFalse(jumpQueriedBeforeTickReleased.get());

        releaseTickQuery.countDown();
        tickThread.join(2_000L);
        jumpThread.join(2_000L);
        assertNoThreadError(tickError);
        assertNoThreadError(jumpError);
    }

    /**
     * 验证连续回放按 batch-size 分批发布，并只在批次之间检查会话状态。
     */
    @Test
    void shouldCheckSessionStateBetweenSchedulerPublishBatches() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_500L);
        ReplayFrame firstFrame = frame(eventTable, 1_100L);
        ReplayFrame secondFrame = frame(eventTable, 1_200L);
        ReplayFrame thirdFrame = frame(eventTable, 1_300L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findWindowFrames(Mockito.eq(eventTable), Mockito.eq(999L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Arrays.asList(firstFrame, secondFrame, thirdFrame));
        Mockito.when(repository.findWindowFrames(Mockito.eq(periodicTable), Mockito.eq(999L), Mockito.eq(1_500L),
                        Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.emptyList());
        Mockito.doAnswer(invocation -> {
                    session.pause();
                    return null;
                })
                .when(publisher)
                .publish(Mockito.eq("instance-001"), Mockito.eq(firstFrame));
        ReplayScheduler scheduler =
                new ReplayScheduler(repository, new ReplayFrameMergeService(), publisher, 10, 50L, 2);

        scheduler.tick(session);

        ArgumentCaptor<ReplayFrame> frameCaptor = ArgumentCaptor.forClass(ReplayFrame.class);
        Mockito.verify(publisher, Mockito.times(2)).publish(Mockito.eq("instance-001"), frameCaptor.capture());
        Assertions.assertEquals(Arrays.asList(firstFrame, secondFrame), frameCaptor.getAllValues());
        Assertions.assertEquals(1_200L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(ReplaySessionState.PAUSED, session.getState());
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

    /**
     * 执行并发测试动作并捕获异常。
     *
     * @param action 测试动作。
     * @param error 异常引用。
     */
    private void runAndCapture(TestAction action, AtomicReference<Throwable> error) {
        try {
            action.run();
        } catch (Throwable throwable) {
            error.set(throwable);
        }
    }

    /**
     * 断言线程没有抛出异常。
     *
     * @param error 异常引用。
     */
    private void assertNoThreadError(AtomicReference<Throwable> error) {
        Throwable throwable = error.get();
        if (throwable != null) {
            Assertions.fail(throwable);
        }
    }

    /**
     * 并发测试动作。
     */
    private interface TestAction {

        /**
         * 执行测试动作。
         *
         * @throws Exception 执行异常。
         */
        void run() throws Exception;
    }
}
