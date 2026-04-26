package com.szzh.loggerserver.support.constant;

import com.szzh.loggerserver.config.LoggerServerProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 消息常量配置测试。
 */
class MessageConstantsTest {

    /**
     * 验证全局生命周期消息类型与消息码能够从配置读取。
     */
    @Test
    void shouldReadConfiguredGlobalLifecycleCodes() {
        LoggerServerProperties properties = new LoggerServerProperties();
        properties.getProtocol().getMessages().getGlobal().setMessageType(10);
        properties.getProtocol().getMessages().getGlobal().setCreateMessageCode(20);
        properties.getProtocol().getMessages().getGlobal().setStopMessageCode(30);

        MessageConstants constants = new MessageConstants(properties);

        Assertions.assertEquals(10, constants.getGlobalMessageType());
        Assertions.assertEquals(20, constants.getGlobalCreateMessageCode());
        Assertions.assertEquals(30, constants.getGlobalStopMessageCode());
        Assertions.assertTrue(constants.isGlobalLifecycleMessage(10));
        Assertions.assertFalse(constants.isGlobalLifecycleMessage(11));
    }

    /**
     * 验证实例控制消息类型与消息码能够从配置读取。
     */
    @Test
    void shouldReadConfiguredInstanceControlCodes() {
        LoggerServerProperties properties = new LoggerServerProperties();
        properties.getProtocol().getMessages().getControl().setMessageType(2100);
        properties.getProtocol().getMessages().getControl().setStartMessageCode(7);
        properties.getProtocol().getMessages().getControl().setPauseMessageCode(8);
        properties.getProtocol().getMessages().getControl().setResumeMessageCode(9);

        MessageConstants constants = new MessageConstants(properties);

        Assertions.assertEquals(2100, constants.getInstanceControlMessageType());
        Assertions.assertEquals(7, constants.getInstanceStartMessageCode());
        Assertions.assertEquals(8, constants.getInstancePauseMessageCode());
        Assertions.assertEquals(9, constants.getInstanceResumeMessageCode());
        Assertions.assertTrue(constants.isInstanceControlMessage(2100));
        Assertions.assertFalse(constants.isInstanceControlMessage(2200));
    }
}
