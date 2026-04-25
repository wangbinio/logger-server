package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.support.exception.BusinessException;
import com.szzh.loggerserver.support.exception.ProtocolParseException;
import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.loggerserver.support.metric.LoggerMetrics;
import com.szzh.loggerserver.util.ProtocolData;
import com.szzh.loggerserver.util.ProtocolMessageUtil;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 实例级控制消息处理器。
 */
@Component
public class InstanceBroadcastMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(InstanceBroadcastMessageHandler.class);

    private SimulationControlCommandPort simulationControlCommandPort;

    private LoggerMetrics loggerMetrics = new LoggerMetrics();

    /**
     * 创建实例级控制消息处理器。
     */
    public InstanceBroadcastMessageHandler() {
    }

    /**
     * 创建实例级控制消息处理器。
     *
     * @param simulationControlCommandPort 控制命令委派端口。
     */
    public InstanceBroadcastMessageHandler(SimulationControlCommandPort simulationControlCommandPort) {
        this.simulationControlCommandPort = simulationControlCommandPort;
    }

    /**
     * 创建实例级控制消息处理器。
     *
     * @param simulationControlCommandPort 控制命令委派端口。
     * @param loggerMetrics 指标封装。
     */
    public InstanceBroadcastMessageHandler(SimulationControlCommandPort simulationControlCommandPort,
                                           LoggerMetrics loggerMetrics) {
        this.simulationControlCommandPort = simulationControlCommandPort;
        this.loggerMetrics = loggerMetrics == null ? new LoggerMetrics() : loggerMetrics;
    }

    /**
     * 注入控制命令委派端口。
     *
     * @param simulationControlCommandPort 控制命令委派端口。
     */
    @Autowired(required = false)
    public void setSimulationControlCommandPort(SimulationControlCommandPort simulationControlCommandPort) {
        this.simulationControlCommandPort = simulationControlCommandPort;
    }

    /**
     * 注入日志指标封装。
     *
     * @param loggerMetrics 日志指标封装。
     */
    @Autowired(required = false)
    public void setLoggerMetrics(LoggerMetrics loggerMetrics) {
        this.loggerMetrics = loggerMetrics == null ? new LoggerMetrics() : loggerMetrics;
    }

    /**
     * 处理实例级控制消息。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     */
    public void handle(String instanceId, MessageExt messageExt) {
        ProtocolData protocolData;
        try {
            protocolData = parse(messageExt);
        } catch (ProtocolParseException exception) {
            loggerMetrics.recordProtocolParseFailure();
            log.warn("result=protocol_parse_failed instanceId={} topic={} messageType=-1 messageCode=-1 senderId=-1 simtime=-1 reason={}",
                    instanceId,
                    messageExt.getTopic(),
                    exception.getMessage());
            return;
        }
        if (!MessageConstants.isInstanceControlMessage(protocolData.getMessageType())) {
            return;
        }
        if (simulationControlCommandPort == null) {
            log.debug("result=port_missing instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1",
                    instanceId,
                    messageExt.getTopic(),
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId());
            return;
        }
        try {
            switch (protocolData.getMessageCode()) {
                case MessageConstants.INSTANCE_START_MESSAGE_CODE:
                    simulationControlCommandPort.handleStart(instanceId, protocolData);
                    return;
                case MessageConstants.INSTANCE_PAUSE_MESSAGE_CODE:
                    simulationControlCommandPort.handlePause(instanceId, protocolData);
                    return;
                case MessageConstants.INSTANCE_RESUME_MESSAGE_CODE:
                    simulationControlCommandPort.handleResume(instanceId, protocolData);
                    return;
                default:
                    loggerMetrics.recordStateViolation();
                    log.debug("result=ignored_unknown_message instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1",
                            instanceId,
                            messageExt.getTopic(),
                            protocolData.getMessageType(),
                            protocolData.getMessageCode(),
                            protocolData.getSenderId());
            }
        } catch (BusinessException exception) {
            logByBusinessException(instanceId, messageExt, protocolData, exception);
        } catch (RuntimeException exception) {
            log.error("result=unexpected_exception instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1 reason={}",
                    instanceId,
                    messageExt.getTopic(),
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    exception.getMessage(),
                    exception);
        }
    }

    /**
     * 解析协议消息。
     *
     * @param messageExt RocketMQ 原始消息。
     * @return 协议数据。
     */
    private ProtocolData parse(MessageExt messageExt) {
        Objects.requireNonNull(messageExt, "messageExt 不能为空");
        return ProtocolMessageUtil.parseData(messageExt.getBody());
    }

    /**
     * 按业务异常分类输出统一日志。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     * @param protocolData 协议数据。
     * @param exception 业务异常。
     */
    private void logByBusinessException(String instanceId,
                                        MessageExt messageExt,
                                        ProtocolData protocolData,
                                        BusinessException exception) {
        if (exception.getCategory() == BusinessException.Category.TDENGINE_WRITE) {
            log.error("result=business_exception category={} instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1 reason={}",
                    exception.getCategory(),
                    instanceId,
                    messageExt.getTopic(),
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    exception.getMessage(),
                    exception);
            return;
        }
        log.warn("result=business_exception category={} instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1 reason={}",
                exception.getCategory(),
                instanceId,
                messageExt.getTopic(),
                protocolData.getMessageType(),
                protocolData.getMessageCode(),
                protocolData.getSenderId(),
                exception.getMessage());
    }
}
