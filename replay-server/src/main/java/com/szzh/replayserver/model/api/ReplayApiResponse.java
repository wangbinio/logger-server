package com.szzh.replayserver.model.api;

import lombok.Getter;

/**
 * 回放 HTTP 接口统一响应。
 *
 * @param <T> 响应数据类型。
 */
@Getter
public class ReplayApiResponse<T> {

    private final boolean success;

    private final String code;

    private final String message;

    private final T data;

    /**
     * 创建回放 HTTP 接口统一响应。
     *
     * @param success 是否成功。
     * @param code 结果码。
     * @param message 响应消息。
     * @param data 响应数据。
     */
    private ReplayApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应。
     *
     * @param data 响应数据。
     * @param <T> 响应数据类型。
     * @return 成功响应。
     */
    public static <T> ReplayApiResponse<T> ok(T data) {
        return new ReplayApiResponse<T>(true, ReplayControlResult.CODE_OK, "成功", data);
    }

    /**
     * 创建失败响应。
     *
     * @param code 结果码。
     * @param message 响应消息。
     * @param data 响应数据。
     * @param <T> 响应数据类型。
     * @return 失败响应。
     */
    public static <T> ReplayApiResponse<T> error(String code, String message, T data) {
        return new ReplayApiResponse<T>(false, code, message, data);
    }
}
