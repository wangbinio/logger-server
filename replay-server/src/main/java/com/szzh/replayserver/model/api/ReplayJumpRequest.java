package com.szzh.replayserver.model.api;

import lombok.Data;

/**
 * 回放时间跳转 HTTP 请求。
 */
@Data
public class ReplayJumpRequest {

    private Long time;
}
