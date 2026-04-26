package com.szzh.replayserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 回放服务应用上下文测试。
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration")
class ReplayServerApplicationTests {

    /**
     * 验证 Spring 上下文可以加载。
     */
    @Test
    void contextLoads() {
    }
}
