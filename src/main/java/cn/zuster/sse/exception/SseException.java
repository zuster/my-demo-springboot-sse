package cn.zuster.sse.exception;

/**
 * SSE异常信息
 *
 * @author zuster
 * @date 2021/1/5
 */
public class SseException extends RuntimeException {
    public SseException() {
    }

    public SseException(String message) {
        super(message);
    }

    public SseException(String message, Throwable cause) {
        super(message, cause);
    }

    public SseException(Throwable cause) {
        super(cause);
    }

    public SseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
