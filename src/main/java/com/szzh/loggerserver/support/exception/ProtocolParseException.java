package com.szzh.loggerserver.support.exception;

/**
 * 协议解析异常。
 */
public class ProtocolParseException extends BusinessException {

    /**
     * 创建协议解析异常。
     *
     * @param message 异常信息。
     */
    public ProtocolParseException(String message) {
        super(Category.PROTOCOL, message);
    }

    /**
     * 创建协议解析异常。
     *
     * @param message 异常信息。
     * @param cause 原始异常。
     */
    public ProtocolParseException(String message, Throwable cause) {
        super(Category.PROTOCOL, message, cause);
    }
}
