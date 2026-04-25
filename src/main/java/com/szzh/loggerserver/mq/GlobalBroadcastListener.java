package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.support.exception.BusinessException;
import com.szzh.loggerserver.support.exception.ProtocolParseException;
import com.szzh.loggerserver.support.constant.MessageConstants;
import com.szzh.loggerserver.support.constant.TopicConstants;
import com.szzh.loggerserver.support.metric.LoggerMetrics;
import com.szzh.loggerserver.util.ProtocolData;
import com.szzh.loggerserver.util.ProtocolMessageUtil;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 全局广播监听器。
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.GLOBAL_BROADCAST_TOPIC,
        consumerGroup = "${logger-server.rocketmq.global-consumer-group}",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING)
@ConditionalOnProperty(
        prefix = "logger-server.rocketmq",
        name = "enable-global-listener",
        havingValue = "true",
        matchIfMissing = true)
public class GlobalBroadcastListener implements RocketMQListener<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(GlobalBroadcastListener.class);

    private SimulationLifecycleCommandPort simulationLifecycleCommandPort;

    private LoggerMetrics loggerMetrics = new LoggerMetrics();

    /**
     * 创建全局广播监听器。
     *
     */
    public GlobalBroadcastListener() {
    }

    /**
     * 创建全局广播监听器。
     *
     * @param loggerMetrics 指标封装。
     */
    public GlobalBroadcastListener(LoggerMetrics loggerMetrics) {
        this.loggerMetrics = loggerMetrics == null ? new LoggerMetrics() : loggerMetrics;
    }

    /**
     * 注入生命周期命令委派端口。
     *
     * @param simulationLifecycleCommandPort 生命周期命令委派端口。
     */
    @Autowired(required = false)
    public void setSimulationLifecycleCommandPort(SimulationLifecycleCommandPort simulationLifecycleCommandPort) {
        this.simulationLifecycleCommandPort = simulationLifecycleCommandPort;
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
     * 处理全局广播消息。
     *
     * @param messageBody RocketMQ 消息体。
     */
    @Override
    public void onMessage(byte[] messageBody) {
        ProtocolData protocolData;
        try {
            protocolData = ProtocolMessageUtil.parseData(messageBody);
        } catch (ProtocolParseException exception) {
            loggerMetrics.recordProtocolParseFailure();
            log.warn("result=protocol_parse_failed instanceId=- topic={} messageType=-1 messageCode=-1 senderId=-1 simtime=-1 reason={}",
                    TopicConstants.GLOBAL_BROADCAST_TOPIC,
                    exception.getMessage());
            return;
        }
        if (!MessageConstants.isGlobalLifecycleMessage(protocolData.getMessageType())) {
            return;
        }
        if (simulationLifecycleCommandPort == null) {
            log.debug("result=port_missing instanceId=- topic={} messageType={} messageCode={} senderId={} simtime=-1",
                    TopicConstants.GLOBAL_BROADCAST_TOPIC,
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId());
            return;
        }
        try {
            switch (protocolData.getMessageCode()) {
                case MessageConstants.GLOBAL_CREATE_MESSAGE_CODE:
                    simulationLifecycleCommandPort.handleCreate(protocolData);
                    return;
                case MessageConstants.GLOBAL_STOP_MESSAGE_CODE:
                    simulationLifecycleCommandPort.handleStop(protocolData);
                    return;
                default:
                    loggerMetrics.recordStateViolation();
                    log.debug("result=ignored_unknown_message instanceId=- topic={} messageType={} messageCode={} senderId={} simtime=-1",
                            TopicConstants.GLOBAL_BROADCAST_TOPIC,
                            protocolData.getMessageType(),
                            protocolData.getMessageCode(),
                            protocolData.getSenderId());
            }
        } catch (BusinessException exception) {
            logByBusinessException("-", TopicConstants.GLOBAL_BROADCAST_TOPIC, protocolData, exception);
        } catch (RuntimeException exception) {
            log.error("result=unexpected_exception instanceId=- topic={} messageType={} messageCode={} senderId={} simtime=-1 reason={}",
                    TopicConstants.GLOBAL_BROADCAST_TOPIC,
                    protocolData.getMessageType(),
                    protocolData.getMessageCode(),
                    protocolData.getSenderId(),
                    exception.getMessage(),
                    exception);
        }
    }

    /**
     * 按业务异常分类输出统一日志。
     *
     * @param instanceId 实例 ID。
     * @param topic 主题名。
     * @param protocolData 协议数据。
     * @param exception 业务异常。
     */
    private void logByBusinessException(String instanceId,
                                        String topic,
                                        ProtocolData protocolData,
                                        BusinessException exception) {
        if (exception.getCategory() == BusinessException.Category.TDENGINE_WRITE) {
            log.error("result=business_exception category={} instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1 reason={}",
                    exception.getCategory(),
                    instanceId,
                    topic,
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
                topic,
                protocolData.getMessageType(),
                protocolData.getMessageCode(),
                protocolData.getSenderId(),
                exception.getMessage());
    }
}
