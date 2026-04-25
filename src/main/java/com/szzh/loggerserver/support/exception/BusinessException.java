package com.szzh.loggerserver.support.exception;

import lombok.Getter;

/**
 * 业务异常基类。
 */
@Getter
public class BusinessException extends IllegalStateException {

    /**
     * -- GETTER --
     *  获取异常分类。
     *
     * @return 异常分类。
     */
    private final Category category;

    /**
     * 创建业务异常。
     *
     * @param category 异常分类。
     * @param message 异常信息。
     */
    public BusinessException(Category category, String message) {
        super(message);
        this.category = requireCategory(category);
    }

    /**
     * 创建业务异常。
     *
     * @param category 异常分类。
     * @param message 异常信息。
     * @param cause 原始异常。
     */
    public BusinessException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = requireCategory(category);
    }

    /**
     * 创建状态类业务异常。
     *
     * @param message 异常信息。
     * @return 业务异常。
     */
    public static BusinessException state(String message) {
        return new BusinessException(Category.STATE, message);
    }

    /**
     * 创建 TDengine 写入异常。
     *
     * @param message 异常信息。
     * @param cause 原始异常。
     * @return 业务异常。
     */
    public static BusinessException tdengineWrite(String message, Throwable cause) {
        return new BusinessException(Category.TDENGINE_WRITE, message, cause);
    }

    /**
     * 校验异常分类。
     *
     * @param category 异常分类。
     * @return 原始分类。
     */
    private Category requireCategory(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("category 不能为空");
        }
        return category;
    }

    /**
     * 业务异常分类。
     */
    public enum Category {
        STATE,
        TDENGINE_WRITE,
        PROTOCOL
    }
}
