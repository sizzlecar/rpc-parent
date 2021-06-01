package io.kimmking.rpcfx.exception;

public class RpcfxException extends RuntimeException{

    private String code;
    private String msg;

    public RpcfxException(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public RpcfxException(String message, String code, String msg) {
        super(message);
        this.code = code;
        this.msg = msg;
    }

    public RpcfxException(String message, Throwable cause, String code, String msg) {
        super(message, cause);
        this.code = code;
        this.msg = msg;
    }

    public RpcfxException(Throwable cause, String code, String msg) {
        super(cause);
        this.code = code;
        this.msg = msg;
    }

    public RpcfxException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, String code, String msg) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = code;
        this.msg = msg;
    }
}
