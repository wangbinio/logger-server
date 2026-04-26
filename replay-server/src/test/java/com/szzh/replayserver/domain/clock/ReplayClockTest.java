package com.szzh.replayserver.domain.clock;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放时钟测试。
 */
class ReplayClockTest {

    /**
     * 验证回放时钟从指定仿真开始时间启动。
     */
    @Test
    void shouldStartFromSimulationStartTime() {
        AtomicLong wallClock = new AtomicLong(1_000L);
        ReplayClock clock = new ReplayClock(10_000L, 20_000L, wallClock::get);

        clock.start();
        wallClock.set(1_500L);

        Assertions.assertTrue(clock.isRunning());
        Assertions.assertEquals(10_500L, clock.currentTime());
        Assertions.assertEquals(1.0D, clock.getRate());
    }

    /**
     * 验证暂停冻结当前回放时间，继续后从冻结点恢复推进。
     */
    @Test
    void shouldPauseAndResumeFromFrozenTime() {
        AtomicLong wallClock = new AtomicLong(2_000L);
        ReplayClock clock = new ReplayClock(10_000L, 20_000L, wallClock::get);
        clock.start();

        wallClock.set(2_300L);
        clock.pause();
        long pausedTime = clock.currentTime();

        wallClock.set(9_000L);
        Assertions.assertEquals(pausedTime, clock.currentTime());

        clock.resume();
        wallClock.set(9_250L);

        Assertions.assertEquals(pausedTime + 250L, clock.currentTime());
    }

    /**
     * 验证倍率调整后按新倍率推进回放时间。
     */
    @Test
    void shouldAdvanceWithUpdatedRate() {
        AtomicLong wallClock = new AtomicLong(5_000L);
        ReplayClock clock = new ReplayClock(10_000L, 20_000L, wallClock::get);
        clock.start();

        wallClock.set(5_200L);
        clock.updateRate(2.0D);
        wallClock.set(5_500L);

        Assertions.assertEquals(10_800L, clock.currentTime());
        Assertions.assertEquals(2.0D, clock.getRate());
    }

    /**
     * 验证当前时间和跳转目标都会被限制在回放时间范围内。
     */
    @Test
    void shouldClampCurrentTimeAndJumpTarget() {
        AtomicLong wallClock = new AtomicLong(10_000L);
        ReplayClock clock = new ReplayClock(1_000L, 2_000L, wallClock::get);

        clock.start();
        wallClock.set(12_500L);
        Assertions.assertEquals(2_000L, clock.currentTime());

        Assertions.assertEquals(1_000L, clock.jumpTo(500L));
        Assertions.assertEquals(1_000L, clock.currentTime());

        Assertions.assertEquals(2_000L, clock.jumpTo(5_000L));
        Assertions.assertEquals(2_000L, clock.currentTime());
    }

    /**
     * 验证非法时间范围和非法倍率会被拒绝。
     */
    @Test
    void shouldRejectInvalidRangeAndRate() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ReplayClock(2_000L, 1_000L));

        ReplayClock clock = new ReplayClock(1_000L, 2_000L);
        Assertions.assertThrows(IllegalArgumentException.class, () -> clock.updateRate(0.0D));
        Assertions.assertThrows(IllegalArgumentException.class, () -> clock.updateRate(-1.0D));
    }
}
