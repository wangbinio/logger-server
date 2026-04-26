package com.szzh.replayserver.domain.clock;

import lombok.Getter;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 回放时钟。
 */
public class ReplayClock {

    private final LongSupplier wallClockSupplier;

    @Getter
    private final long startTime;

    @Getter
    private final long endTime;

    private long baseReplayTime;

    private long baseWallClockTime;

    @Getter
    private volatile double rate = 1.0D;

    @Getter
    private volatile boolean running;

    @Getter
    private volatile boolean initialized;

    /**
     * 使用系统时间创建回放时钟。
     *
     * @param startTime 回放开始时间。
     * @param endTime 回放结束时间。
     */
    public ReplayClock(long startTime, long endTime) {
        this(startTime, endTime, System::currentTimeMillis);
    }

    /**
     * 使用指定时间源创建回放时钟。
     *
     * @param startTime 回放开始时间。
     * @param endTime 回放结束时间。
     * @param wallClockSupplier 墙钟时间提供者。
     */
    public ReplayClock(long startTime, long endTime, LongSupplier wallClockSupplier) {
        if (endTime < startTime) {
            throw new IllegalArgumentException("回放结束时间不能小于开始时间");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.wallClockSupplier = Objects.requireNonNull(wallClockSupplier, "wallClockSupplier 不能为空");
        this.baseReplayTime = startTime;
    }

    /**
     * 启动回放时钟。
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (!initialized) {
            this.baseReplayTime = startTime;
            this.initialized = true;
        }
        this.baseWallClockTime = wallClockSupplier.getAsLong();
        this.running = true;
    }

    /**
     * 暂停回放时钟。
     */
    public synchronized void pause() {
        ensureInitialized();
        if (!running) {
            throw new IllegalStateException("回放时钟未处于运行状态");
        }
        this.baseReplayTime = currentTime();
        this.running = false;
    }

    /**
     * 继续回放时钟。
     */
    public synchronized void resume() {
        ensureInitialized();
        if (running) {
            return;
        }
        this.baseWallClockTime = wallClockSupplier.getAsLong();
        this.running = true;
    }

    /**
     * 更新回放倍率。
     *
     * @param newRate 新倍率。
     */
    public synchronized void updateRate(double newRate) {
        if (newRate <= 0) {
            throw new IllegalArgumentException("回放倍率必须大于 0");
        }
        this.baseReplayTime = currentTime();
        this.baseWallClockTime = wallClockSupplier.getAsLong();
        this.rate = newRate;
        this.initialized = true;
    }

    /**
     * 跳转到指定回放仿真时间。
     *
     * @param targetTime 目标仿真时间。
     * @return 边界限制后的实际时间。
     */
    public synchronized long jumpTo(long targetTime) {
        long actualTime = clamp(targetTime);
        this.baseReplayTime = actualTime;
        this.baseWallClockTime = wallClockSupplier.getAsLong();
        this.initialized = true;
        return actualTime;
    }

    /**
     * 获取当前回放仿真时间。
     *
     * @return 当前回放仿真时间。
     */
    public synchronized long currentTime() {
        if (!initialized || !running) {
            return clamp(baseReplayTime);
        }
        long elapsedMillis = wallClockSupplier.getAsLong() - baseWallClockTime;
        long currentTime = baseReplayTime + (long) (elapsedMillis * rate);
        return clamp(currentTime);
    }

    /**
     * 将时间限制在回放范围内。
     *
     * @param time 待限制时间。
     * @return 限制后的时间。
     */
    private long clamp(long time) {
        if (time < startTime) {
            return startTime;
        }
        if (time > endTime) {
            return endTime;
        }
        return time;
    }

    /**
     * 确保回放时钟已初始化。
     */
    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("回放时钟尚未启动");
        }
    }
}
