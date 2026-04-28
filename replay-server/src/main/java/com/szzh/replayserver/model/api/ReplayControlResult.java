package com.szzh.replayserver.model.api;

import com.szzh.replayserver.domain.session.ReplaySession;
import lombok.Getter;

/**
 * 回放控制命令处理结果。
 */
@Getter
public class ReplayControlResult {

    public static final String CODE_OK = "OK";

    public static final String CODE_BAD_REQUEST = "BAD_REQUEST";

    public static final String CODE_SESSION_NOT_FOUND = "REPLAY_SESSION_NOT_FOUND";

    public static final String CODE_STATE_CONFLICT = "REPLAY_STATE_CONFLICT";

    public static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    private final boolean accepted;

    private final String code;

    private final String message;

    private final ReplaySession session;

    /**
     * 创建回放控制命令处理结果。
     *
     * @param accepted 命令是否被业务接受。
     * @param code 结果码。
     * @param message 结果消息。
     * @param session 命令处理后的会话。
     */
    private ReplayControlResult(boolean accepted, String code, String message, ReplaySession session) {
        this.accepted = accepted;
        this.code = code;
        this.message = message;
        this.session = session;
    }

    /**
     * 创建成功结果。
     *
     * @param session 命令处理后的会话。
     * @return 成功结果。
     */
    public static ReplayControlResult ok(ReplaySession session) {
        return new ReplayControlResult(true, CODE_OK, "成功", session);
    }

    /**
     * 创建请求参数错误结果。
     *
     * @param message 错误消息。
     * @return 请求参数错误结果。
     */
    public static ReplayControlResult badRequest(String message) {
        return new ReplayControlResult(false, CODE_BAD_REQUEST, message, null);
    }

    /**
     * 创建回放会话不存在结果。
     *
     * @return 回放会话不存在结果。
     */
    public static ReplayControlResult sessionNotFound() {
        return new ReplayControlResult(false, CODE_SESSION_NOT_FOUND, "回放会话不存在", null);
    }

    /**
     * 创建状态冲突结果。
     *
     * @param session 当前回放会话。
     * @return 状态冲突结果。
     */
    public static ReplayControlResult stateConflict(ReplaySession session) {
        return new ReplayControlResult(false, CODE_STATE_CONFLICT, "当前回放状态不接受该控制命令", session);
    }

    /**
     * 创建非预期异常结果。
     *
     * @param message 错误消息。
     * @return 非预期异常结果。
     */
    public static ReplayControlResult internalError(String message) {
        return new ReplayControlResult(false, CODE_INTERNAL_ERROR, message, null);
    }
}
