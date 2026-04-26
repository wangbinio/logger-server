package com.szzh.replayserver;

import com.szzh.replayserver.domain.session.ReplaySessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回放服务应用上下文测试。
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration")
class ReplayServerApplicationTests {

    @Autowired
    private ReplaySessionManager replaySessionManager;

    /**
     * 验证 Spring 上下文可以加载。
     */
    @Test
    void contextLoads() {
        assertNotNull(replaySessionManager);
    }
}
