package com.szzh.replayserver.domain.session;

import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放会话测试。
 */
class ReplaySessionTest {

    /**
     * 验证回放会话按预期完成准备、启动、暂停和继续状态迁移。
     */
    @Test
    void shouldMoveThroughReadyRunningPausedAndRunning() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplaySession session = newSession(wallClock);

        Assertions.assertEquals(ReplaySessionState.PREPARING, session.getState());

        session.markReady();
        session.start();
        wallClock.set(1_500L);

        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
        Assertions.assertTrue(session.getReplayClock().isRunning());
        Assertions.assertEquals(1_500L, session.getReplayClock().currentTime());

        session.pause();
        long pausedTime = session.getReplayClock().currentTime();
        wallClock.set(5_000L);

        Assertions.assertEquals(ReplaySessionState.PAUSED, session.getState());
        Assertions.assertEquals(pausedTime, session.getReplayClock().currentTime());

        session.resume();
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
    }

    /**
     * 验证水位只能通过明确方法向前推进。
     */
    @Test
    void shouldAdvanceWatermarkOnlyForward() {
        ReplaySession session = newSession(new AtomicLong(1_000L));

        Assertions.assertEquals(999L, session.getLastDispatchedSimTime());

        session.advanceLastDispatchedSimTime(1_200L);

        Assertions.assertEquals(1_200L, session.getLastDispatchedSimTime());
        Assertions.assertThrows(IllegalArgumentException.class, () -> session.advanceLastDispatchedSimTime(1_100L));
        Assertions.assertThrows(IllegalArgumentException.class, () -> session.advanceLastDispatchedSimTime(2_500L));
    }

    /**
     * 验证初始水位位于仿真开始时间前一毫秒，确保首帧不会被左开窗口漏掉。
     */
    @Test
    void shouldInitializeWatermarkBeforeSimulationStartTime() {
        ReplaySession session = newSession(new AtomicLong(1_000L));

        Assertions.assertEquals(999L, session.getLastDispatchedSimTime());
    }

    /**
     * 验证极小开始时间不会导致初始水位下溢。
     */
    @Test
    void shouldAvoidWatermarkUnderflowWhenStartTimeIsMinimumLong() {
        ReplayTimeRange timeRange = new ReplayTimeRange(Long.MIN_VALUE, Long.MIN_VALUE + 100L);
        ReplayClock replayClock = new ReplayClock(timeRange.getStartTime(), timeRange.getEndTime(), () -> 0L);
        ReplaySession session = new ReplaySession(
                "instance-min",
                timeRange,
                Collections.emptyList(),
                Collections.emptyList(),
                replayClock);

        Assertions.assertEquals(Long.MIN_VALUE, session.getLastDispatchedSimTime());
    }

    /**
     * 验证自然完成态允许时间跳转，并在跳转后进入可继续查看的暂停态。
     */
    @Test
    void shouldAllowJumpAfterCompletedAndMoveToPaused() {
        ReplaySession session = newSession(new AtomicLong(1_000L));
        session.markReady();
        session.start();
        session.markCompleted();

        long actualTime = session.jumpTo(1_500L);

        Assertions.assertEquals(1_500L, actualTime);
        Assertions.assertEquals(ReplaySessionState.PAUSED, session.getState());
        Assertions.assertEquals(1_500L, session.getReplayClock().currentTime());
    }

    /**
     * 验证自然完成态不是释放终态，停止和失败才是不可继续操作状态。
     */
    @Test
    void shouldTreatCompletedAsNaturalStateInsteadOfReleaseTerminal() {
        Assertions.assertFalse(ReplaySessionState.COMPLETED.isTerminal());
        Assertions.assertTrue(ReplaySessionState.STOPPED.isTerminal());
        Assertions.assertTrue(ReplaySessionState.FAILED.isTerminal());
    }

    /**
     * 验证订阅句柄设置和停止操作保持幂等。
     */
    @Test
    void shouldSetBroadcastHandleAndStopIdempotently() {
        ReplaySession session = newSession(new AtomicLong(1_000L));
        Object handle = new Object();

        session.setBroadcastConsumerHandle(handle);
        session.stop();
        session.stop();

        Assertions.assertSame(handle, session.getBroadcastConsumerHandle());
        Assertions.assertEquals(ReplaySessionState.STOPPED, session.getState());
    }

    /**
     * 验证会话进入终态后不能再迁移到其他状态。
     */
    @Test
    void shouldRejectTransitionAfterTerminalState() {
        ReplaySession session = newSession(new AtomicLong(1_000L));

        session.markReady();
        session.stop();

        Assertions.assertThrows(IllegalStateException.class, session::start);
        Assertions.assertThrows(IllegalStateException.class, session::markCompleted);
        Assertions.assertEquals(ReplaySessionState.STOPPED, session.getState());
    }

    /**
     * 创建测试用回放会话。
     *
     * @param wallClock 墙钟时间。
     * @return 回放会话。
     */
    private ReplaySession newSession(AtomicLong wallClock) {
        ReplayTimeRange timeRange = new ReplayTimeRange(1_000L, 2_000L);
        ReplayClock replayClock = new ReplayClock(timeRange.getStartTime(), timeRange.getEndTime(), wallClock::get);
        ReplayTableDescriptor eventTable = new ReplayTableDescriptor(
                "situation_1001_1_7_instance_001", 7, 1001, 1, ReplayTableType.EVENT);
        ReplayTableDescriptor periodicTable = new ReplayTableDescriptor(
                "situation_2001_9_8_instance_001", 8, 2001, 9, ReplayTableType.PERIODIC);
        return new ReplaySession(
                "instance-001",
                timeRange,
                Collections.singletonList(eventTable),
                Collections.singletonList(periodicTable),
                replayClock);
    }
}
