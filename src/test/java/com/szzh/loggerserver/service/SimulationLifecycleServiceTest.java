package com.szzh.loggerserver.service;

import com.szzh.loggerserver.domain.session.SimulationSession;
import com.szzh.loggerserver.domain.session.SimulationSessionManager;
import com.szzh.loggerserver.domain.session.SimulationSessionState;
import com.szzh.loggerserver.mq.TopicSubscriptionManager;
import com.szzh.loggerserver.util.ProtocolData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

/**
 * 仿真生命周期服务测试。
 */
class SimulationLifecycleServiceTest {

    /**
     * 验证创建命令会完成会话初始化、建表和订阅。
     */
    @Test
    void shouldCreateSessionAndInitializeResources() {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        TdengineSchemaService schemaService = Mockito.mock(TdengineSchemaService.class);
        TopicSubscriptionManager subscriptionManager = Mockito.mock(TopicSubscriptionManager.class);
        SimulationLifecycleService lifecycleService =
                new SimulationLifecycleService(sessionManager, schemaService, subscriptionManager);

        lifecycleService.handleCreate(buildCreateProtocol("instance-001"));

        SimulationSession session = sessionManager.requireSession("instance-001");
        Assertions.assertEquals(SimulationSessionState.READY, session.getState());
        Mockito.verify(schemaService).createStableIfAbsent("instance-001");
        Mockito.verify(schemaService).createTimeControlTableIfAbsent("instance-001");
        Mockito.verify(subscriptionManager).subscribe("instance-001");
    }

    /**
     * 验证重复创建同一实例时保持幂等。
     */
    @Test
    void shouldIgnoreDuplicateCreateWhenSessionAlreadyActive() {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        TdengineSchemaService schemaService = Mockito.mock(TdengineSchemaService.class);
        TopicSubscriptionManager subscriptionManager = Mockito.mock(TopicSubscriptionManager.class);
        SimulationLifecycleService lifecycleService =
                new SimulationLifecycleService(sessionManager, schemaService, subscriptionManager);

        lifecycleService.handleCreate(buildCreateProtocol("instance-001"));
        lifecycleService.handleCreate(buildCreateProtocol("instance-001"));

        Assertions.assertEquals(1, sessionManager.size());
        Assertions.assertEquals(SimulationSessionState.READY, sessionManager.requireSession("instance-001").getState());
        Mockito.verify(schemaService, Mockito.times(1)).createStableIfAbsent("instance-001");
        Mockito.verify(schemaService, Mockito.times(1)).createTimeControlTableIfAbsent("instance-001");
        Mockito.verify(subscriptionManager, Mockito.times(1)).subscribe("instance-001");
    }

    /**
     * 验证创建初始化失败时会把会话置为失败态并记录异常。
     */
    @Test
    void shouldMarkSessionFailedWhenInitializationFails() {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        TdengineSchemaService schemaService = Mockito.mock(TdengineSchemaService.class);
        TopicSubscriptionManager subscriptionManager = Mockito.mock(TopicSubscriptionManager.class);
        SimulationLifecycleService lifecycleService =
                new SimulationLifecycleService(sessionManager, schemaService, subscriptionManager);

        Mockito.doThrow(new IllegalStateException("schema boom"))
                .when(schemaService)
                .createStableIfAbsent("instance-001");

        Assertions.assertThrows(IllegalStateException.class,
                () -> lifecycleService.handleCreate(buildCreateProtocol("instance-001")));
        SimulationSession session = sessionManager.requireSession("instance-001");
        Assertions.assertEquals(SimulationSessionState.FAILED, session.getState());
        Assertions.assertEquals("schema boom", session.getLastErrorMessage());
        Mockito.verify(subscriptionManager, Mockito.never()).subscribe(Mockito.anyString());
    }

    /**
     * 验证控制时间点表创建失败时会沿用初始化失败语义。
     */
    @Test
    void shouldMarkSessionFailedWhenTimeControlTableInitializationFails() {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        TdengineSchemaService schemaService = Mockito.mock(TdengineSchemaService.class);
        TopicSubscriptionManager subscriptionManager = Mockito.mock(TopicSubscriptionManager.class);
        SimulationLifecycleService lifecycleService =
                new SimulationLifecycleService(sessionManager, schemaService, subscriptionManager);

        Mockito.doThrow(new IllegalStateException("time control schema boom"))
                .when(schemaService)
                .createTimeControlTableIfAbsent("instance-001");

        Assertions.assertThrows(IllegalStateException.class,
                () -> lifecycleService.handleCreate(buildCreateProtocol("instance-001")));
        SimulationSession session = sessionManager.requireSession("instance-001");
        Assertions.assertEquals(SimulationSessionState.FAILED, session.getState());
        Assertions.assertEquals("time control schema boom", session.getLastErrorMessage());
        Mockito.verify(subscriptionManager, Mockito.never()).subscribe(Mockito.anyString());
    }

    /**
     * 验证停止命令会取消订阅并移除已停止会话。
     */
    @Test
    void shouldStopAndRemoveSession() {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        TdengineSchemaService schemaService = Mockito.mock(TdengineSchemaService.class);
        TopicSubscriptionManager subscriptionManager = Mockito.mock(TopicSubscriptionManager.class);
        SimulationLifecycleService lifecycleService =
                new SimulationLifecycleService(sessionManager, schemaService, subscriptionManager);

        lifecycleService.handleCreate(buildCreateProtocol("instance-001"));
        lifecycleService.handleStop(buildCreateProtocol("instance-001"));

        Assertions.assertFalse(sessionManager.getSession("instance-001").isPresent());
        Mockito.verify(subscriptionManager).unsubscribe("instance-001");
    }

    /**
     * 验证停止不存在的会话时安全返回。
     */
    @Test
    void shouldIgnoreStopWhenSessionMissing() {
        SimulationSessionManager sessionManager = new SimulationSessionManager();
        TdengineSchemaService schemaService = Mockito.mock(TdengineSchemaService.class);
        TopicSubscriptionManager subscriptionManager = Mockito.mock(TopicSubscriptionManager.class);
        SimulationLifecycleService lifecycleService =
                new SimulationLifecycleService(sessionManager, schemaService, subscriptionManager);

        lifecycleService.handleStop(buildCreateProtocol("missing-instance"));

        Mockito.verify(subscriptionManager, Mockito.never()).unsubscribe(Mockito.anyString());
    }

    /**
     * 构造创建消息协议对象。
     *
     * @param instanceId 实例 ID。
     * @return 协议对象。
     */
    private ProtocolData buildCreateProtocol(String instanceId) {
        ProtocolData protocolData = new ProtocolData();
        protocolData.setRawData(("{\"instanceId\":\"" + instanceId + "\"}").getBytes(StandardCharsets.UTF_8));
        return protocolData;
    }
}
