package com.szzh.replayserver.service;

import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 回放子表分类测试。
 */
class ReplayTableClassifierTest {

    /**
     * 验证命中事件消息配置的子表会被识别为事件表。
     */
    @Test
    void shouldClassifyConfiguredEventTables() {
        ReplayServerProperties properties = new ReplayServerProperties();
        ReplayServerProperties.EventMessage eventMessage = new ReplayServerProperties.EventMessage();
        eventMessage.setMessageType(1001);
        eventMessage.setMessageCodes(Arrays.asList(1, 2, 3));
        properties.getReplay().getEventMessages().add(eventMessage);
        ReplayTableClassifier classifier = new ReplayTableClassifier(properties);

        List<ReplayTableDescriptor> result = classifier.classify(Arrays.asList(
                descriptor(1001, 2),
                descriptor(1001, 9),
                descriptor(1002, 2)));

        Assertions.assertEquals(ReplayTableType.EVENT, result.get(0).getTableType());
        Assertions.assertEquals(ReplayTableType.PERIODIC, result.get(1).getTableType());
        Assertions.assertEquals(ReplayTableType.PERIODIC, result.get(2).getTableType());
    }

    /**
     * 验证空事件配置时全部表保持周期表。
     */
    @Test
    void shouldClassifyAllTablesAsPeriodicWhenEventConfigEmpty() {
        ReplayTableClassifier classifier = new ReplayTableClassifier(new ReplayServerProperties());

        List<ReplayTableDescriptor> result = classifier.classify(Collections.singletonList(descriptor(1001, 2)));

        Assertions.assertEquals(ReplayTableType.PERIODIC, result.get(0).getTableType());
    }

    /**
     * 验证设计默认事件配置能命中事件表。
     */
    @Test
    void shouldClassifyDesignDefaultEventMessages() {
        ReplayServerProperties properties = new ReplayServerProperties();
        ReplayServerProperties.EventMessage firstEventMessage = new ReplayServerProperties.EventMessage();
        firstEventMessage.setMessageType(1001);
        firstEventMessage.setMessageCodes(Arrays.asList(1, 2, 3));
        ReplayServerProperties.EventMessage secondEventMessage = new ReplayServerProperties.EventMessage();
        secondEventMessage.setMessageType(1002);
        secondEventMessage.setMessageCodes(Collections.singletonList(8));
        properties.getReplay().getEventMessages().add(firstEventMessage);
        properties.getReplay().getEventMessages().add(secondEventMessage);
        ReplayTableClassifier classifier = new ReplayTableClassifier(properties);

        List<ReplayTableDescriptor> result = classifier.classify(Arrays.asList(
                descriptor(1001, 1),
                descriptor(1001, 3),
                descriptor(1002, 8),
                descriptor(1002, 9)));

        Assertions.assertEquals(ReplayTableType.EVENT, result.get(0).getTableType());
        Assertions.assertEquals(ReplayTableType.EVENT, result.get(1).getTableType());
        Assertions.assertEquals(ReplayTableType.EVENT, result.get(2).getTableType());
        Assertions.assertEquals(ReplayTableType.PERIODIC, result.get(3).getTableType());
    }

    /**
     * 创建测试子表描述。
     *
     * @param messageType 消息类型。
     * @param messageCode 消息编号。
     * @return 子表描述。
     */
    private ReplayTableDescriptor descriptor(int messageType, int messageCode) {
        return new ReplayTableDescriptor(
                "situation_" + messageType + "_" + messageCode + "_7_instance_001",
                7,
                messageType,
                messageCode,
                ReplayTableType.PERIODIC);
    }
}
