package com.szzh.replayserver.controller;

import com.szzh.replayserver.config.ReplayServerProperties;
import com.szzh.replayserver.domain.clock.ReplayClock;
import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.domain.session.ReplaySessionState;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import com.szzh.replayserver.model.query.ReplayTimeRange;
import com.szzh.replayserver.service.ReplayControlService;
import com.szzh.replayserver.service.ReplayHttpCommandFactory;
import com.szzh.replayserver.service.ReplayJumpService;
import com.szzh.replayserver.service.ReplayScheduler;
import com.szzh.replayserver.support.constant.ReplayMessageConstants;
import com.szzh.replayserver.support.metric.ReplayMetrics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回放控制 HTTP 接口测试。
 */
class ReplayControlControllerTest {

    /**
     * 验证元信息查询接口返回回放会话快照。
     *
     * @throws Exception MockMvc 调用异常。
     */
    @Test
    void shouldReturnReplaySessionMetadata() throws Exception {
        Fixture fixture = new Fixture();
        fixture.readySession();

        fixture.mockMvc.perform(get("/api/replay/instances/instance-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.instanceId").value("instance-001"))
                .andExpect(jsonPath("$.data.startTime").value(1_000L))
                .andExpect(jsonPath("$.data.endTime").value(2_000L))
                .andExpect(jsonPath("$.data.duration").value(1_000L))
                .andExpect(jsonPath("$.data.state").value("READY"))
                .andExpect(jsonPath("$.data.rate").value(1.0D))
                .andExpect(jsonPath("$.data.currentReplayTime").value(1_000L))
                .andExpect(jsonPath("$.data.lastDispatchedSimTime").value(999L));
    }

    /**
     * 验证会话不存在时元信息查询返回 404。
     *
     * @throws Exception MockMvc 调用异常。
     */
    @Test
    void shouldReturnNotFoundWhenSessionMissing() throws Exception {
        Fixture fixture = new Fixture();

        fixture.mockMvc.perform(get("/api/replay/instances/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REPLAY_SESSION_NOT_FOUND"));
    }

    /**
     * 验证 HTTP 控制接口能驱动启动、暂停、继续、倍速和跳转。
     *
     * @throws Exception MockMvc 调用异常。
     */
    @Test
    void shouldControlReplayThroughHttpEndpoints() throws Exception {
        Fixture fixture = new Fixture();
        ReplaySession session = fixture.readySession();
        fixture.stubJumpSyncsWatermark();

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("RUNNING"));
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());
        Mockito.verify(fixture.scheduler).schedule(session);

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PAUSED"));
        Assertions.assertEquals(ReplaySessionState.PAUSED, session.getState());
        Mockito.verify(fixture.scheduler).cancel("instance-001");

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("RUNNING"));
        Assertions.assertEquals(ReplaySessionState.RUNNING, session.getState());

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rate\":2.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate").value(2.0D));
        Assertions.assertEquals(2.0D, session.getRate(), 0.0001D);

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/jump")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"time\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastDispatchedSimTime").value(1_500L));
        Mockito.verify(fixture.jumpService).jump(session, 1_500L);
    }

    /**
     * 验证非法控制请求返回 400。
     *
     * @throws Exception MockMvc 调用异常。
     */
    @Test
    void shouldRejectInvalidControlRequests() throws Exception {
        Fixture fixture = new Fixture();
        fixture.readySession();

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rate\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/jump")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    /**
     * 验证状态冲突返回 409。
     *
     * @throws Exception MockMvc 调用异常。
     */
    @Test
    void shouldReturnConflictWhenControlRejectedByState() throws Exception {
        Fixture fixture = new Fixture();
        fixture.readySession();

        fixture.mockMvc.perform(post("/api/replay/instances/instance-001/pause"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REPLAY_STATE_CONFLICT"))
                .andExpect(jsonPath("$.data.state").value("READY"));
    }

    /**
     * 验证控制会话不存在时返回 404。
     *
     * @throws Exception MockMvc 调用异常。
     */
    @Test
    void shouldReturnNotFoundWhenControlSessionMissing() throws Exception {
        Fixture fixture = new Fixture();

        fixture.mockMvc.perform(post("/api/replay/instances/missing/start"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REPLAY_SESSION_NOT_FOUND"));
    }

    /**
     * 控制接口测试夹具。
     */
    private static final class Fixture {

        private final AtomicLong wallClock = new AtomicLong(1_000L);

        private final ReplaySessionManager sessionManager = new ReplaySessionManager(wallClock::get);

        private final ReplayScheduler scheduler = Mockito.mock(ReplayScheduler.class);

        private final ReplayJumpService jumpService = Mockito.mock(ReplayJumpService.class);

        private final ReplayMetrics metrics = new ReplayMetrics();

        private final ReplayServerProperties properties = new ReplayServerProperties();

        private final ReplayMessageConstants messageConstants = new ReplayMessageConstants(properties);

        private final ReplayControlService controlService =
                new ReplayControlService(sessionManager, scheduler, jumpService, metrics);

        private final ReplayHttpCommandFactory commandFactory = new ReplayHttpCommandFactory(messageConstants);

        private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ReplayControlController(sessionManager, controlService, commandFactory)).build();

        /**
         * 创建 READY 会话。
         *
         * @return READY 会话。
         */
        private ReplaySession readySession() {
            ReplaySession session = new ReplaySession(
                    "instance-001",
                    new ReplayTimeRange(1_000L, 2_000L),
                    Collections.singletonList(new ReplayTableDescriptor(
                            "event_table", 7, 1001, 1, ReplayTableType.EVENT)),
                    Collections.emptyList(),
                    new ReplayClock(1_000L, 2_000L, wallClock::get));
            session.markReady();
            sessionManager.createSession(session);
            return session;
        }

        /**
         * 准备跳转服务桩，使 HTTP 响应能反映跳转后的水位。
         */
        private void stubJumpSyncsWatermark() {
            Mockito.doAnswer(invocation -> {
                ReplaySession session = invocation.getArgument(0);
                Long targetTime = invocation.getArgument(1);
                session.syncLastDispatchedSimTime(targetTime.longValue());
                return null;
            }).when(jumpService).jump(Mockito.any(ReplaySession.class), Mockito.anyLong());
        }
    }
}
