package com.szzh.loggerserver.support.metric;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志服务基础指标封装。
 */
@Component
public class LoggerMetrics {

    private final AtomicLong protocolParseFailureCount = new AtomicLong();

    private final AtomicLong stateViolationCount = new AtomicLong();

    private final AtomicLong tdengineWriteFailureCount = new AtomicLong();

    private final AtomicLong messagesReceivedCount = new AtomicLong();

    private final AtomicLong messagesWrittenCount = new AtomicLong();

    private final AtomicLong messagesDroppedCount = new AtomicLong();

    private final AtomicInteger activeSessionCount = new AtomicInteger();

    /**
     * 记录一次协议解析失败。
     */
    public void recordProtocolParseFailure() {
        protocolParseFailureCount.incrementAndGet();
    }

    /**
     * 记录一次状态异常或幂等忽略。
     */
    public void recordStateViolation() {
        stateViolationCount.incrementAndGet();
    }

    /**
     * 记录一次 TDengine 写入失败。
     */
    public void recordTdengineWriteFailure() {
        tdengineWriteFailureCount.incrementAndGet();
    }

    /**
     * 记录一次消息接收。
     */
    public void recordMessageReceived() {
        messagesReceivedCount.incrementAndGet();
    }

    /**
     * 记录一次消息写入成功。
     */
    public void recordMessageWritten() {
        messagesWrittenCount.incrementAndGet();
    }

    /**
     * 记录一次消息丢弃。
     */
    public void recordMessageDropped() {
        messagesDroppedCount.incrementAndGet();
    }

    /**
     * 设置当前活跃实例数。
     *
     * @param activeSessionCount 当前活跃实例数。
     */
    public void setActiveSessionCount(int activeSessionCount) {
        this.activeSessionCount.set(Math.max(activeSessionCount, 0));
    }

    /**
     * 获取协议解析失败数。
     *
     * @return 协议解析失败数。
     */
    public long getProtocolParseFailureCount() {
        return protocolParseFailureCount.get();
    }

    /**
     * 获取状态异常数。
     *
     * @return 状态异常数。
     */
    public long getStateViolationCount() {
        return stateViolationCount.get();
    }

    /**
     * 获取 TDengine 写入失败数。
     *
     * @return TDengine 写入失败数。
     */
    public long getTdengineWriteFailureCount() {
        return tdengineWriteFailureCount.get();
    }

    /**
     * 获取收到消息数。
     *
     * @return 收到消息数。
     */
    public long getMessagesReceivedCount() {
        return messagesReceivedCount.get();
    }

    /**
     * 获取写入成功数。
     *
     * @return 写入成功数。
     */
    public long getMessagesWrittenCount() {
        return messagesWrittenCount.get();
    }

    /**
     * 获取消息丢弃数。
     *
     * @return 消息丢弃数。
     */
    public long getMessagesDroppedCount() {
        return messagesDroppedCount.get();
    }

    /**
     * 获取当前活跃实例数。
     *
     * @return 当前活跃实例数。
     */
    public int getActiveSessionCount() {
        return activeSessionCount.get();
    }
}
