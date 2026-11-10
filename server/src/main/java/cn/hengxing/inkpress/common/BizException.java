package cn.hengxing.inkpress.common;

public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }
}
