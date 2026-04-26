package com.szzh.replayserver;

import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 回放服务启动类。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties({ReplayServerProperties.class, RocketMQProperties.class})
public class ReplayServerApplication {

    /**
     * 启动回放服务。
     *
     * @param args 启动参数。
     */
    public static void main(String[] args) {
        SpringApplication.run(ReplayServerApplication.class, args);
    }

    /**
     * 创建回放会话管理器。
     *
     * @return 回放会话管理器。
     */
    @Bean
    public ReplaySessionManager replaySessionManager() {
        return new ReplaySessionManager();
    }
}
