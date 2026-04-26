package com.szzh.loggerserver;

import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LoggerServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoggerServerApplication.class, args);
    }

    /**
     * 创建仿真实例会话管理器。
     *
     * @return 会话管理器。
     */
    @Bean
    public SimulationSessionManager simulationSessionManager() {
        return new SimulationSessionManager();
    }

}
