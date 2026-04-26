package com.szzh.replayserver.support.constant;

import com.szzh.replayserver.config.ReplayServerProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 回放消息常量配置测试。
 */
class ReplayMessageConstantsTest {

    /**
     * 验证默认消息类型和消息码与回放协议设计一致。
     */
    @Test
    void shouldExposeDefaultReplayMessageCodes() {
        ReplayMessageConstants constants = new ReplayMessageConstants(new ReplayServerProperties());

        Assertions.assertEquals(1, constants.getGlobalMessageType());
        Assertions.assertEquals(0, constants.getGlobalCreateMessageCode());
        Assertions.assertEquals(1, constants.getGlobalStopMessageCode());
        Assertions.assertEquals(1200, constants.getInstanceControlMessageType());
        Assertions.assertEquals(1, constants.getInstanceStartMessageCode());
        Assertions.assertEquals(2, constants.getInstancePauseMessageCode());
        Assertions.assertEquals(3, constants.getInstanceResumeMessageCode());
        Assertions.assertEquals(4, constants.getInstanceRateMessageCode());
        Assertions.assertEquals(5, constants.getInstanceJumpMessageCode());
        Assertions.assertEquals(9, constants.getInstanceMetadataMessageCode());
    }

    /**
     * 验证消息常量判断方法按配置快照工作。
     */
    @Test
    void shouldIdentifyConfiguredReplayMessages() {
        ReplayServerProperties properties = new ReplayServerProperties();
        properties.getProtocol().getMessages().getGlobal().setMessageType(11);
        properties.getProtocol().getMessages().getGlobal().setCreateMessageCode(21);
        properties.getProtocol().getMessages().getGlobal().setStopMessageCode(22);
        properties.getProtocol().getMessages().getControl().setMessageType(2200);
        properties.getProtocol().getMessages().getControl().setStartMessageCode(31);
        properties.getProtocol().getMessages().getControl().setPauseMessageCode(32);
        properties.getProtocol().getMessages().getControl().setResumeMessageCode(33);
        properties.getProtocol().getMessages().getControl().setRateMessageCode(34);
        properties.getProtocol().getMessages().getControl().setJumpMessageCode(35);
        properties.getProtocol().getMessages().getControl().setMetadataMessageCode(39);

        ReplayMessageConstants constants = new ReplayMessageConstants(properties);

        Assertions.assertTrue(constants.isGlobalLifecycleMessage(11));
        Assertions.assertFalse(constants.isGlobalLifecycleMessage(1));
        Assertions.assertTrue(constants.isGlobalCreateMessage(21));
        Assertions.assertTrue(constants.isGlobalStopMessage(22));
        Assertions.assertTrue(constants.isInstanceControlMessage(2200));
        Assertions.assertFalse(constants.isInstanceControlMessage(1200));
        Assertions.assertTrue(constants.isInstanceStartMessage(31));
        Assertions.assertTrue(constants.isInstancePauseMessage(32));
        Assertions.assertTrue(constants.isInstanceResumeMessage(33));
        Assertions.assertTrue(constants.isInstanceRateMessage(34));
        Assertions.assertTrue(constants.isInstanceJumpMessage(35));
        Assertions.assertTrue(constants.isInstanceMetadataMessage(39));
    }
}
