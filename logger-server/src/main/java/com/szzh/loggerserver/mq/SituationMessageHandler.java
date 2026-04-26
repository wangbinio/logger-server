package com.szzh.loggerserver.mq;

import com.szzh.common.exception.BusinessException;
import com.szzh.common.exception.ProtocolParseException;
import com.szzh.loggerserver.support.metric.LoggerMetrics;
import com.szzh.common.protocol.ProtocolData;
import com.szzh.common.protocol.ProtocolMessageUtil;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 态势消息处理器。
 */
@Component
public class SituationMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SituationMessageHandler.class);

    private SituationRecordIngressPort situationRecordIngressPort;

    private LoggerMetrics loggerMetrics = new LoggerMetrics();

    /**
     * 创建态势消息处理器。
     */
    public SituationMessageHandler() {
    }

    /**
     * 创建态势消息处理器。
     *
     * @param situationRecordIngressPort 态势入口委派端口。
     */
    public SituationMessageHandler(SituationRecordIngressPort situationRecordIngressPort) {
        this.situationRecordIngressPort = situationRecordIngressPort;
    }

    /**
     * 创建态势消息处理器。
     *
     * @param situationRecordIngressPort 态势入口委派端口。
     * @param loggerMetrics 指标封装。
     */
    public SituationMessageHandler(SituationRecordIngressPort situationRecordIngressPort,
                                   LoggerMetrics loggerMetrics) {
        this.situationRecordIngressPort = situationRecordIngressPort;
        this.loggerMetrics = loggerMetrics == null ? new LoggerMetrics() : loggerMetrics;
    }

    /**
     * 注入态势入口委派端口。
     *
     * @param situationRecordIngressPort 态势入口委派端口。
     */
    @Autowired(required = false)
    public void setSituationRecordIngressPort(SituationRecordIngressPort situationRecordIngressPort) {
        this.situationRecordIngressPort = situationRecordIngressPort;
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
     * 处理态势消息。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     */
    public void handle(String instanceId, MessageExt messageExt) {
        Objects.requireNonNull(messageExt, "messageExt 不能为空");
        ProtocolData protocolData;
        try {
            protocolData = ProtocolMessageUtil.parseData(messageExt.getBody());
        } catch (ProtocolParseException exception) {
            loggerMetrics.recordProtocolParseFailure();

            logProtocolParseFailed(instanceId, messageExt, exception);
            return;
        }
        if (situationRecordIngressPort == null) {
            logPortMissing(instanceId, messageExt, protocolData);
            return;
        }
        try {
            situationRecordIngressPort.handle(instanceId, protocolData);
        } catch (BusinessException exception) {
            logByBusinessException(instanceId, messageExt, protocolData, exception);
        } catch (RuntimeException exception) {
            logUnexpectedException(instanceId, messageExt, protocolData, exception);
        }
    }

    /**
     * 输出协议解析失败日志。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     * @param exception 协议解析异常。
     */
    private void logProtocolParseFailed(String instanceId,
                                        MessageExt messageExt,
                                        ProtocolParseException exception) {
        log.warn("result=protocol_parse_failed instanceId={} topic={} messageType=-1 messageCode=-1 senderId=-1 simtime=-1 reason={}",
                instanceId, messageExt.getTopic(), exception.getMessage());
    }

    /**
     * 输出态势入口端口缺失日志。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     * @param protocolData 协议数据。
     */
    private void logPortMissing(String instanceId, MessageExt messageExt, ProtocolData protocolData) {
        log.debug("result=port_missing instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1",
                instanceId, messageExt.getTopic(), protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId());
    }

    /**
     * 输出未预期异常日志。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     * @param protocolData 协议数据。
     * @param exception 运行时异常。
     */
    private void logUnexpectedException(String instanceId,
                                        MessageExt messageExt,
                                        ProtocolData protocolData,
                                        RuntimeException exception) {
        log.error("result=unexpected_exception instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1 reason={}",
                instanceId, messageExt.getTopic(), protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), exception.getMessage(), exception);
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
                    exception.getCategory(), instanceId, messageExt.getTopic(), protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), exception.getMessage(), exception);
            return;
        }
        log.warn("result=business_exception category={} instanceId={} topic={} messageType={} messageCode={} senderId={} simtime=-1 reason={}",
                exception.getCategory(), instanceId, messageExt.getTopic(), protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), exception.getMessage());
    }
}
