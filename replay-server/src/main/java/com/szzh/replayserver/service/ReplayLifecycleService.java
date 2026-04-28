package com.szzh.replayserver.service;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.model.dto.ReplayCreatePayload;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import com.szzh.replayserver.mq.ReplayLifecycleCommandPort;
import com.szzh.replayserver.repository.ReplayTableDiscoveryRepository;
import com.szzh.replayserver.repository.ReplayTimeControlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 回放生命周期服务。
 */
@Service
public class ReplayLifecycleService implements ReplayLifecycleCommandPort {

    private static final Logger log = LoggerFactory.getLogger(ReplayLifecycleService.class);

    private final ReplaySessionManager sessionManager;

    private final ReplayTimeControlRepository timeControlRepository;

    private final ReplayTableDiscoveryRepository tableDiscoveryRepository;

    private final ReplayTableClassifier tableClassifier;

    private final ReplayScheduler scheduler;

    /**
     * 创建回放生命周期服务。
     *
     * @param sessionManager 回放会话管理器。
     * @param timeControlRepository 控制时间点 Repository。
     * @param tableDiscoveryRepository 子表发现 Repository。
     * @param tableClassifier 子表分类器。
     * @param scheduler 回放调度器。
     */
    public ReplayLifecycleService(ReplaySessionManager sessionManager,
                                  ReplayTimeControlRepository timeControlRepository,
                                  ReplayTableDiscoveryRepository tableDiscoveryRepository,
                                  ReplayTableClassifier tableClassifier,
                                  ReplayScheduler scheduler) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.timeControlRepository = Objects.requireNonNull(timeControlRepository, "timeControlRepository 不能为空");
        this.tableDiscoveryRepository = Objects.requireNonNull(tableDiscoveryRepository, "tableDiscoveryRepository 不能为空");
        this.tableClassifier = Objects.requireNonNull(tableClassifier, "tableClassifier 不能为空");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler 不能为空");
    }

    /**
     * 处理创建回放任务命令。
     *
     * @param protocolData 协议数据。
     */
    @Override
    public void handleCreate(ProtocolData protocolData) {
        ReplayCreatePayload payload = ReplayCreatePayload.fromRawData(requireProtocolData(protocolData).getRawData());
        createReplay(payload.getInstanceId());
    }

    /**
     * 处理停止回放任务命令。
     *
     * @param protocolData 协议数据。
     */
    @Override
    public void handleStop(ProtocolData protocolData) {
        ReplayCreatePayload payload = ReplayCreatePayload.fromRawData(requireProtocolData(protocolData).getRawData());
        stopReplay(payload.getInstanceId());
    }

    /**
     * 创建回放任务。
     *
     * @param instanceId 实例 ID。
     * @return 回放会话。
     */
    public ReplaySession createReplay(String instanceId) {
        if (sessionManager.getSession(instanceId).filter(session -> !session.getState().isTerminal()).isPresent()) {
            return sessionManager.requireSession(instanceId);
        }

        ReplayTimeRange timeRange = timeControlRepository.resolveTimeRange(instanceId);
        List<ReplayTableDescriptor> classifiedTables =
                tableClassifier.classify(tableDiscoveryRepository.discoverTables(instanceId));
        if (classifiedTables.isEmpty()) {
            throw BusinessException.state("未发现可回放态势子表: " + instanceId);
        }

        ReplaySession session = sessionManager.createSession(
                instanceId,
                timeRange,
                filterTables(classifiedTables, ReplayTableType.EVENT),
                filterTables(classifiedTables, ReplayTableType.PERIODIC));
        session.markReady();

        log.info("result=replay_create_success instanceId={} topic=- messageType=-1 messageCode=-1 senderId=-1 currentReplayTime={} lastDispatchedSimTime={} rate={} replayState={}",
                instanceId, session.getReplayClock().currentTime(), session.getLastDispatchedSimTime(), session.getRate(), session.getState());
        return session;
    }

    /**
     * 停止回放任务并释放资源。
     *
     * @param instanceId 实例 ID。
     */
    public void stopReplay(String instanceId) {
        ReplaySession session = sessionManager.getSession(instanceId).orElse(null);

        // 停止连续回放调度并释放本地会话，HTTP 控制入口不需要释放实例级订阅。
        scheduler.cancel(instanceId);
        sessionManager.stopSession(instanceId);
        sessionManager.removeSession(instanceId);

        if (session != null) {
            log.info("result=replay_stop_success instanceId={} topic=- messageType=-1 messageCode=-1 senderId=-1 currentReplayTime={} lastDispatchedSimTime={} rate={} replayState={}",
                    instanceId, session.getReplayClock().currentTime(), session.getLastDispatchedSimTime(), session.getRate(), session.getState());
        }
    }

    /**
     * 按表类型筛选子表。
     *
     * @param tables 已分类子表。
     * @param tableType 表类型。
     * @return 指定类型子表。
     */
    private List<ReplayTableDescriptor> filterTables(List<ReplayTableDescriptor> tables, ReplayTableType tableType) {
        List<ReplayTableDescriptor> result = new ArrayList<ReplayTableDescriptor>();
        for (ReplayTableDescriptor table : tables) {
            if (table.getTableType() == tableType) {
                result.add(table);
            }
        }
        return result;
    }

    /**
     * 校验协议数据。
     *
     * @param protocolData 协议数据。
     * @return 原始协议数据。
     */
    private ProtocolData requireProtocolData(ProtocolData protocolData) {
        return Objects.requireNonNull(protocolData, "protocolData 不能为空");
    }
}
