package com.szzh.replayserver.support.metric;

import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 回放服务内存级指标。
 */
@Component
public class ReplayMetrics {

    private final ReplaySessionManager sessionManager;

    private final AtomicLong publishedSuccessCount = new AtomicLong();

    private final AtomicLong publishedFailureCount = new AtomicLong();

    private final AtomicLong queryFailureCount = new AtomicLong();

    private final AtomicLong jumpCount = new AtomicLong();

    private final AtomicLong stateConflictCount = new AtomicLong();

    /**
     * 创建不绑定会话管理器的回放指标。
     */
    public ReplayMetrics() {
        this(null);
    }

    /**
     * 创建绑定会话管理器的回放指标。
     *
     * @param sessionManager 回放会话管理器。
     */
    @Autowired
    public ReplayMetrics(ReplaySessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 获取当前活跃回放会话数量。
     *
     * @return 当前活跃回放会话数量。
     */
    public long activeSessionCount() {
        if (sessionManager == null) {
            return 0L;
        }
        long count = 0L;
        Collection<ReplaySession> sessions = sessionManager.getAllSessions();
        for (ReplaySession session : sessions) {
            if (!session.getState().isTerminal()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 记录回放消息发布成功。
     */
    public void recordPublishSuccess() {
        publishedSuccessCount.incrementAndGet();
    }

    /**
     * 记录回放消息最终发布失败。
     */
    public void recordPublishFailure() {
        publishedFailureCount.incrementAndGet();
    }

    /**
     * 记录 TDengine 查询失败。
     */
    public void recordQueryFailure() {
        queryFailureCount.incrementAndGet();
    }

    /**
     * 记录时间跳转成功。
     */
    public void recordJump() {
        jumpCount.incrementAndGet();
    }

    /**
     * 记录状态冲突。
     */
    public void recordStateConflict() {
        stateConflictCount.incrementAndGet();
    }

    /**
     * 获取回放消息发布成功数。
     *
     * @return 回放消息发布成功数。
     */
    public long publishedSuccessCount() {
        return publishedSuccessCount.get();
    }

    /**
     * 获取回放消息发布失败数。
     *
     * @return 回放消息发布失败数。
     */
    public long publishedFailureCount() {
        return publishedFailureCount.get();
    }

    /**
     * 获取 TDengine 查询失败数。
     *
     * @return TDengine 查询失败数。
     */
    public long queryFailureCount() {
        return queryFailureCount.get();
    }

    /**
     * 获取时间跳转成功次数。
     *
     * @return 时间跳转成功次数。
     */
    public long jumpCount() {
        return jumpCount.get();
    }

    /**
     * 获取状态冲突次数。
     *
     * @return 状态冲突次数。
     */
    public long stateConflictCount() {
        return stateConflictCount.get();
    }
}
