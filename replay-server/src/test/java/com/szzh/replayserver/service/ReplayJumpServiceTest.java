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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放跳转服务测试。
 */
class ReplayJumpServiceTest {

    private final ReplayTableDescriptor eventTable =
            new ReplayTableDescriptor("event_table", 7, 1001, 1, ReplayTableType.EVENT);

    private final ReplayTableDescriptor periodicTable =
            new ReplayTableDescriptor("periodic_table", 8, 2001, 9, ReplayTableType.PERIODIC);

    /**
     * 验证向后跳转补发开始到目标时间的事件和周期快照。
     */
    @Test
    void shouldPublishBackwardJumpEventsAndPeriodicSnapshot() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_500L);
        session.advanceLastDispatchedSimTime(1_500L);
        ReplayFrame eventAtStart = frame(eventTable, 1_000L);
        ReplayFrame eventAtTarget = frame(eventTable, 1_200L);
        ReplayFrame periodicSnapshot = frame(periodicTable, 1_100L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findBackwardJumpEventFrames(Mockito.eq(eventTable), Mockito.eq(1_000L),
                        Mockito.eq(1_200L), Mockito.any(ReplayCursor.class)))
                .thenReturn(Arrays.asList(eventAtStart, eventAtTarget))
                .thenReturn(Collections.emptyList());
        Mockito.when(repository.findPeriodicLastFrame(periodicTable, 1_200L))
                .thenReturn(Optional.of(periodicSnapshot));
        ReplayJumpService jumpService =
                new ReplayJumpService(repository, new ReplayFrameMergeService(), publisher, 2);

        jumpService.jump(session, 1_200L);

        ArgumentCaptor<ReplayFrame> captor = ArgumentCaptor.forClass(ReplayFrame.class);
        Mockito.verify(publisher, Mockito.times(3)).publish(Mockito.eq("instance-001"), captor.capture());
        Assertions.assertEquals(Arrays.asList(eventAtStart, eventAtTarget, periodicSnapshot), captor.getAllValues());
        Assertions.assertEquals(1_200L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
        Assertions.assertEquals(1_200L, session.getReplayClock().currentTime());
    }

    /**
     * 验证向前跳转补发当前时间到目标时间的事件和周期快照。
     */
    @Test
    void shouldPublishForwardJumpEventsAndPeriodicSnapshot() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_200L);
        session.advanceLastDispatchedSimTime(1_200L);
        ReplayFrame eventAtTarget = frame(eventTable, 1_700L);
        ReplayFrame periodicSnapshot = frame(periodicTable, 1_600L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        ReplayMetrics metrics = new ReplayMetrics();
        Mockito.when(repository.findForwardJumpEventFrames(Mockito.eq(eventTable), Mockito.eq(1_200L),
                        Mockito.eq(1_700L), Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(eventAtTarget));
        Mockito.when(repository.findPeriodicLastFrame(periodicTable, 1_700L))
                .thenReturn(Optional.of(periodicSnapshot));
        ReplayJumpService jumpService =
                new ReplayJumpService(repository, new ReplayFrameMergeService(), publisher, 2, metrics);

        jumpService.jump(session, 1_700L);

        ArgumentCaptor<ReplayFrame> captor = ArgumentCaptor.forClass(ReplayFrame.class);
        Mockito.verify(publisher, Mockito.times(2)).publish(Mockito.eq("instance-001"), captor.capture());
        Assertions.assertEquals(Arrays.asList(eventAtTarget, periodicSnapshot), captor.getAllValues());
        Assertions.assertEquals(1_700L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(1L, metrics.jumpCount());
    }

    /**
     * 验证原地跳转不补发事件，只补发周期快照。
     */
    @Test
    void shouldPublishOnlyPeriodicSnapshotWhenJumpToCurrentTime() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        ReplayFrame periodicSnapshot = frame(periodicTable, 1_000L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findPeriodicLastFrame(periodicTable, 1_000L))
                .thenReturn(Optional.of(periodicSnapshot));
        ReplayJumpService jumpService =
                new ReplayJumpService(repository, new ReplayFrameMergeService(), publisher, 2);

        jumpService.jump(session, 1_000L);

        Mockito.verify(repository, Mockito.never()).findBackwardJumpEventFrames(
                Mockito.any(ReplayTableDescriptor.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(ReplayCursor.class));
        Mockito.verify(repository, Mockito.never()).findForwardJumpEventFrames(
                Mockito.any(ReplayTableDescriptor.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any(ReplayCursor.class));
        Mockito.verify(publisher).publish("instance-001", periodicSnapshot);
    }

    /**
     * 验证暂停态跳转发布失败时不推进水位、不移动时钟并进入失败态。
     */
    @Test
    void shouldNotAdvanceWatermarkWhenJumpPublishFails() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_500L);
        session.advanceLastDispatchedSimTime(1_500L);
        session.pause();
        ReplayFrame eventFrame = frame(eventTable, 1_200L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findBackwardJumpEventFrames(Mockito.eq(eventTable), Mockito.eq(1_000L),
                        Mockito.eq(1_200L), Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(eventFrame));
        Mockito.doThrow(BusinessException.state("publish boom"))
                .when(publisher)
                .publish(Mockito.anyString(), Mockito.any(ReplayFrame.class));
        ReplayJumpService jumpService =
                new ReplayJumpService(repository, new ReplayFrameMergeService(), publisher, 2);

        Assertions.assertThrows(BusinessException.class, () -> jumpService.jump(session, 1_200L));

        Assertions.assertEquals(1_500L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(1_500L, session.getReplayClock().currentTime());
        Assertions.assertEquals(ReplaySessionState.FAILED, session.getState());
    }

    /**
     * 验证运行态跳转发布失败时不会恢复为运行态。
     */
    @Test
    void shouldNotResumeRunningSessionWhenJumpPublishFails() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_500L);
        session.advanceLastDispatchedSimTime(1_500L);
        ReplayFrame eventFrame = frame(eventTable, 1_200L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        Mockito.when(repository.findBackwardJumpEventFrames(Mockito.eq(eventTable), Mockito.eq(1_000L),
                        Mockito.eq(1_200L), Mockito.any(ReplayCursor.class)))
                .thenReturn(Collections.singletonList(eventFrame));
        Mockito.doThrow(BusinessException.state("publish boom"))
                .when(publisher)
                .publish(Mockito.anyString(), Mockito.any(ReplayFrame.class));
        ReplayJumpService jumpService =
                new ReplayJumpService(repository, new ReplayFrameMergeService(), publisher, 2);

        Assertions.assertThrows(BusinessException.class, () -> jumpService.jump(session, 1_200L));

        Assertions.assertEquals(1_500L, session.getLastDispatchedSimTime());
        Assertions.assertEquals(ReplaySessionState.FAILED, session.getState());
    }

    /**
     * 验证跳转查询异常会记录查询失败指标且不记录成功跳转。
     */
    @Test
    void shouldRecordQueryFailureWhenJumpRepositoryThrows() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = runningSession(wallClock);
        wallClock.set(1_500L);
        ReplayFrameRepository repository = Mockito.mock(ReplayFrameRepository.class);
        ReplaySituationPublisher publisher = Mockito.mock(ReplaySituationPublisher.class);
        ReplayMetrics metrics = new ReplayMetrics();
        Mockito.when(repository.findBackwardJumpEventFrames(Mockito.eq(eventTable), Mockito.eq(1_000L),
                        Mockito.eq(1_200L), Mockito.any(ReplayCursor.class)))
                .thenThrow(new IllegalStateException("tdengine boom"));
        ReplayJumpService jumpService =
                new ReplayJumpService(repository, new ReplayFrameMergeService(), publisher, 2, metrics);

        Assertions.assertThrows(IllegalStateException.class, () -> jumpService.jump(session, 1_200L));

        Assertions.assertEquals(1L, metrics.queryFailureCount());
        Assertions.assertEquals(0L, metrics.jumpCount());
        Assertions.assertEquals(ReplaySessionState.FAILED, session.getState());
    }

    /**
     * 创建运行中的测试会话。
     *
     * @param wallClock 墙钟时间。
     * @return 回放会话。
     */
    private ReplaySession runningSession(AtomicLong wallClock) {
        ReplaySession session = new ReplaySession(
                "instance-001",
                new ReplayTimeRange(1_000L, 2_000L),
                Collections.singletonList(eventTable),
                Collections.singletonList(periodicTable),
                new ReplayClock(1_000L, 2_000L, wallClock::get));
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
