package com.szzh.loggerserver.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

/**
 * 应用配置文件结构测试。
 */
class ApplicationProfileConfigurationTest {

    /**
     * 验证基础配置文件只保留通用配置，不再承载开发环境专属的 TDengine 与 RocketMQ 配置。
     */
    @Test
    void shouldKeepBaseApplicationYamlFreeOfTdengineAndRocketMqSections() {
        Properties properties = loadYaml("application.yml");

        Assertions.assertNull(properties.getProperty("logger-server.tdengine.maximum-pool-size"));
        Assertions.assertNull(properties.getProperty("logger-server.tdengine.connection-timeout-ms"));
        Assertions.assertNull(properties.getProperty("logger-server.rocketmq.global-consumer-group"));
        Assertions.assertNull(properties.getProperty("logger-server.rocketmq.instance-consumer-group-prefix"));
    }

    /**
     * 验证开发环境配置文件承载 TDengine 与 RocketMQ 相关配置。
     */
    @Test
    void shouldMoveTdengineAndRocketMqSectionsIntoDevProfile() {
        Properties properties = loadYaml("application-dev.yml");

        Assertions.assertEquals("4", properties.getProperty("logger-server.tdengine.maximum-pool-size"));
        Assertions.assertEquals("30000", properties.getProperty("logger-server.tdengine.connection-timeout-ms"));
        Assertions.assertEquals("logger-global-consumer", properties.getProperty("logger-server.rocketmq.global-consumer-group"));
        Assertions.assertEquals("logger-instance", properties.getProperty("logger-server.rocketmq.instance-consumer-group-prefix"));
    }

    /**
     * 验证记录侧实例控制消息使用 control 节点承载，避免与业务实例标识语义混淆。
     */
    @Test
    void shouldUseControlNodeForInstanceControlMessages() {
        Properties properties = loadYaml("application.yml");

        Assertions.assertNull(properties.getProperty("logger-server.protocol.messages.instance.message-type"));
        Assertions.assertEquals("1100", properties.getProperty("logger-server.protocol.messages.control.message-type"));
        Assertions.assertEquals("1", properties.getProperty("logger-server.protocol.messages.control.start-message-code"));
        Assertions.assertEquals("5", properties.getProperty("logger-server.protocol.messages.control.pause-message-code"));
        Assertions.assertEquals("6", properties.getProperty("logger-server.protocol.messages.control.resume-message-code"));
    }

    /**
     * 加载指定 YAML 配置文件。
     *
     * @param resourceName 资源文件名。
     * @return 解析后的属性集合。
     */
    private Properties loadYaml(String resourceName) {
        ClassPathResource resource = new ClassPathResource(resourceName);
        Assertions.assertTrue(resource.exists(), resourceName + " 应存在");
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(resource);
        Properties properties = factoryBean.getObject();
        Assertions.assertNotNull(properties, resourceName + " 应能解析为属性");
        return properties;
    }
}
