package com.szzh.loggerserver.mq;

import com.szzh.loggerserver.util.ProtocolData;
import com.szzh.loggerserver.util.ProtocolMessageUtil;
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
     * 注入态势入口委派端口。
     *
     * @param situationRecordIngressPort 态势入口委派端口。
     */
    @Autowired(required = false)
    public void setSituationRecordIngressPort(SituationRecordIngressPort situationRecordIngressPort) {
        this.situationRecordIngressPort = situationRecordIngressPort;
    }

    /**
     * 处理态势消息。
     *
     * @param instanceId 实例 ID。
     * @param messageExt RocketMQ 原始消息。
     */
    public void handle(String instanceId, MessageExt messageExt) {
        Objects.requireNonNull(messageExt, "messageExt 不能为空");
        ProtocolData protocolData = ProtocolMessageUtil.parseData(messageExt.getBody());
        if (protocolData == null) {
            log.warn("态势消息协议解析失败，instanceId={}, topic={}, msgId={}",
                    instanceId,
                    messageExt.getTopic(),
                    messageExt.getMsgId());
            return;
        }
        if (situationRecordIngressPort == null) {
            log.debug("态势消息入口端口尚未接入，instanceId={}", instanceId);
            return;
        }
        situationRecordIngressPort.handle(instanceId, protocolData);
    }
}
