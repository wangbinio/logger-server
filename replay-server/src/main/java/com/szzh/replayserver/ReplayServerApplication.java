package com.szzh.replayserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 回放服务启动类。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class ReplayServerApplication {

    /**
     * 启动回放服务。
     *
     * @param args 启动参数。
     */
    public static void main(String[] args) {
        SpringApplication.run(ReplayServerApplication.class, args);
    }
}
