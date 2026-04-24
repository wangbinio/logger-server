package com.szzh.loggerserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootTest(
        classes = LoggerServerApplicationTests.TestApplication.class,
        properties = "logger-server.rocketmq.enable-global-listener=false")
class LoggerServerApplicationTests {

    @Test
    void contextLoads() {
    }

    /**
     * 测试专用应用配置。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = LoggerServerApplication.class)
    static class TestApplication {
    }

}
