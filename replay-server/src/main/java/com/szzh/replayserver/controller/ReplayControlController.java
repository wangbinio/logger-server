package com.szzh.replayserver.controller;

import com.szzh.replayserver.domain.session.ReplaySession;
import com.szzh.replayserver.domain.session.ReplaySessionManager;
import com.szzh.replayserver.model.api.ReplayApiResponse;
import com.szzh.replayserver.model.api.ReplayControlResult;
import com.szzh.replayserver.model.api.ReplayJumpRequest;
import com.szzh.replayserver.model.api.ReplayRateRequest;
import com.szzh.replayserver.model.api.ReplaySessionResponse;
import com.szzh.replayserver.service.ReplayControlService;
import com.szzh.replayserver.service.ReplayHttpCommandFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 回放控制 HTTP 接口。
 */
@RestController
@RequestMapping("/api/replay/instances")
public class ReplayControlController {

    private final ReplaySessionManager sessionManager;

    private final ReplayControlService controlService;

    private final ReplayHttpCommandFactory commandFactory;

    /**
     * 创建回放控制 HTTP 接口。
     *
     * @param sessionManager 回放会话管理器。
     * @param controlService 回放控制服务。
     * @param commandFactory HTTP 控制命令适配器。
     */
    public ReplayControlController(ReplaySessionManager sessionManager,
                                   ReplayControlService controlService,
                                   ReplayHttpCommandFactory commandFactory) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager 不能为空");
        this.controlService = Objects.requireNonNull(controlService, "controlService 不能为空");
        this.commandFactory = Objects.requireNonNull(commandFactory, "commandFactory 不能为空");
    }

    /**
     * 查询回放会话元信息。
     *
     * @param instanceId 实例 ID。
     * @return 回放会话元信息。
     */
    @GetMapping("/{instanceId}")
    public ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> querySession(@PathVariable String instanceId) {
        Optional<ReplaySession> sessionOptional = sessionManager.getSession(instanceId);
        if (!sessionOptional.isPresent()) {
            return notFoundResponse();
        }
        return ResponseEntity.ok(ReplayApiResponse.ok(ReplaySessionResponse.fromSession(sessionOptional.get())));
    }

    /**
     * 启动回放。
     *
     * @param instanceId 实例 ID。
     * @return 控制结果。
     */
    @PostMapping("/{instanceId}/start")
    public ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> start(@PathVariable String instanceId) {
        return executeCommand(() -> controlService.startReplay(instanceId, commandFactory.startCommand()));
    }

    /**
     * 暂停回放。
     *
     * @param instanceId 实例 ID。
     * @return 控制结果。
     */
    @PostMapping("/{instanceId}/pause")
    public ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> pause(@PathVariable String instanceId) {
        return executeCommand(() -> controlService.pauseReplay(instanceId, commandFactory.pauseCommand()));
    }

    /**
     * 继续回放。
     *
     * @param instanceId 实例 ID。
     * @return 控制结果。
     */
    @PostMapping("/{instanceId}/resume")
    public ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> resume(@PathVariable String instanceId) {
        return executeCommand(() -> controlService.resumeReplay(instanceId, commandFactory.resumeCommand()));
    }

    /**
     * 更新回放倍率。
     *
     * @param instanceId 实例 ID。
     * @param request 倍率请求。
     * @return 控制结果。
     */
    @PostMapping("/{instanceId}/rate")
    public ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> updateRate(@PathVariable String instanceId,
                                                                               @RequestBody(required = false)
                                                                               ReplayRateRequest request) {
        if (!isValidRateRequest(request)) {
            return badRequestResponse("rate 必须大于 0");
        }
        return executeCommand(() -> controlService.updateReplayRate(
                instanceId, commandFactory.rateCommand(request.getRate().doubleValue())));
    }

    /**
     * 执行时间跳转。
     *
     * @param instanceId 实例 ID。
     * @param request 跳转请求。
     * @return 控制结果。
     */
    @PostMapping("/{instanceId}/jump")
    public ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> jump(@PathVariable String instanceId,
                                                                         @RequestBody(required = false)
                                                                         ReplayJumpRequest request) {
        if (request == null || request.getTime() == null) {
            return badRequestResponse("time 不能为空");
        }
        return executeCommand(() -> controlService.jumpReplay(
                instanceId, commandFactory.jumpCommand(request.getTime().longValue())));
    }

    /**
     * 处理请求体不可读异常。
     *
     * @param exception 原始异常。
     * @return 请求错误响应。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> handleUnreadableBody(
            HttpMessageNotReadableException exception) {
        return badRequestResponse("请求体不是合法 JSON");
    }

    /**
     * 执行控制命令并转换为 HTTP 响应。
     *
     * @param commandSupplier 控制命令执行器。
     * @return HTTP 响应。
     */
    private ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> executeCommand(
            Supplier<ReplayControlResult> commandSupplier) {
        try {
            return toResponse(commandSupplier.get());
        } catch (RuntimeException exception) {
            ReplayControlResult result = ReplayControlResult.internalError(exception.getMessage());
            return toResponse(result);
        }
    }

    /**
     * 将控制结果转换为 HTTP 响应。
     *
     * @param result 控制结果。
     * @return HTTP 响应。
     */
    private ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> toResponse(ReplayControlResult result) {
        ReplaySessionResponse data = ReplaySessionResponse.fromSession(result.getSession());
        if (ReplayControlResult.CODE_OK.equals(result.getCode())) {
            return ResponseEntity.ok(ReplayApiResponse.ok(data));
        }
        if (ReplayControlResult.CODE_SESSION_NOT_FOUND.equals(result.getCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReplayApiResponse.error(result.getCode(), result.getMessage(), data));
        }
        if (ReplayControlResult.CODE_STATE_CONFLICT.equals(result.getCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ReplayApiResponse.error(result.getCode(), result.getMessage(), data));
        }
        if (ReplayControlResult.CODE_BAD_REQUEST.equals(result.getCode())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ReplayApiResponse.error(result.getCode(), result.getMessage(), data));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ReplayApiResponse.error(result.getCode(), result.getMessage(), data));
    }

    /**
     * 创建会话不存在响应。
     *
     * @return 会话不存在响应。
     */
    private ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> notFoundResponse() {
        return toResponse(ReplayControlResult.sessionNotFound());
    }

    /**
     * 创建请求参数错误响应。
     *
     * @param message 错误消息。
     * @return 请求参数错误响应。
     */
    private ResponseEntity<ReplayApiResponse<ReplaySessionResponse>> badRequestResponse(String message) {
        return toResponse(ReplayControlResult.badRequest(message));
    }

    /**
     * 判断倍率请求是否合法。
     *
     * @param request 倍率请求。
     * @return 是否合法。
     */
    private boolean isValidRateRequest(ReplayRateRequest request) {
        if (request == null || request.getRate() == null) {
            return false;
        }
        double rate = request.getRate().doubleValue();
        return rate > 0D && !Double.isNaN(rate) && !Double.isInfinite(rate);
    }
}
