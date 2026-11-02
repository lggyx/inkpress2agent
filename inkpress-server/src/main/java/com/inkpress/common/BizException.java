package com.inkpress.common;

import lombok.Getter;

/**
 * 业务异常：由全局异常处理器统一转换为 ApiResponse
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(String message) {
        return new BizException(ErrorCode.BAD_REQUEST.getCode(), message);
    }
}
