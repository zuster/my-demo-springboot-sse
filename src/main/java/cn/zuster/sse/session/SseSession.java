package cn.zuster.sse.session;

import cn.zuster.sse.exception.SseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * SSE Session
 *
 * @author zuster
 * @date 2021/1/5
 */
public class SseSession {
    private static final Logger logger = LoggerFactory.getLogger(SseSession.class);

    /**
     * Session维护Map
     */
    private static Map<String, SseEmitter> SESSION = new ConcurrentHashMap<>();

    /**
     * 判断Session是否存在
     *
     * @param id 客户端ID
     * @return
     */
    public static boolean exist(String id) {
        return SESSION.get(id) == null;
    }

    /**
     * 增加Session
     *
     * @param id      客户端ID
     * @param emitter SseEmitter
     */
    public static void add(String id, SseEmitter emitter) {
        final SseEmitter oldEmitter = SESSION.get(id);
        if (oldEmitter != null) {
            oldEmitter.completeWithError(new SseException("RepeatConnect(Id:" + id + ")"));
        }
        SESSION.put(id, emitter);
    }


    /**
     * 删除Session
     *
     * @param id 客户端ID
     * @return
     */
    public static boolean del(String id) {
        final SseEmitter emitter = SESSION.remove(id);
        if (emitter != null) {
            emitter.complete();
            return true;
        }
        return false;
    }

    /**
     * 发送消息
     *
     * @param id  客户端ID
     * @param msg 发送的消息
     * @return
     */
    public static boolean send(String id, Object msg) {
        final SseEmitter emitter = SESSION.get(id);
        if (emitter != null) {
            try {
                emitter.send(msg);
                return true;
            } catch (IOException e) {
                logger.error("MSG: SendMessageError-IOException | ID: " + id + " | Date: " + new Date() + " |", e);
                return false;
            }
        }
        return false;
    }

    /**
     * SseEmitter onCompletion 后执行的逻辑
     *
     * @param id     客户端ID
     * @param future
     */
    public static void onCompletion(String id, ScheduledFuture<?> future) {
        SESSION.remove(id);
        if (future != null) {
            // SseEmitter断开后需要中断心跳发送
            future.cancel(true);
        }
    }

    /**
     * SseEmitter onTimeout 或 onError 后执行的逻辑
     *
     * @param id
     * @param e
     */
    public static void onError(String id, SseException e) {
        final SseEmitter emitter = SESSION.get(id);
        if (emitter != null) {
            emitter.completeWithError(e);
        }
    }
}
