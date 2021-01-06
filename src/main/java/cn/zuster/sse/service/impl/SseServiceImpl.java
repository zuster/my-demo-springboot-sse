package cn.zuster.sse.service.impl;

import cn.zuster.sse.exception.SseException;
import cn.zuster.sse.service.SseService;
import cn.zuster.sse.session.SseSession;
import cn.zuster.sse.task.HeartBeatTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * SSE 相关业务实现
 *
 * @author zuster
 * @date 2021/1/5
 */
@Service
public class SseServiceImpl implements SseService {
    private static final Logger logger = LoggerFactory.getLogger(SseServiceImpl.class);

    /**
     * 发送心跳线程池
     */
    private static ScheduledExecutorService heartbeatExecutors = Executors.newScheduledThreadPool(2);

    /**
     * 新建连接
     *
     * @param clientId 客户端ID
     * @return
     */
    @Override
    public SseEmitter start(String clientId) {
        // 默认30秒超时,设置为0L则永不超时
        SseEmitter emitter = new SseEmitter(30_000L);
        logger.info("MSG: SseConnect | EmitterHash: {} | ID: {} | Date: {}", emitter.hashCode(), clientId, new Date());
        SseSession.add(clientId, emitter);
        final ScheduledFuture<?> future = heartbeatExecutors.scheduleAtFixedRate(new HeartBeatTask(clientId), 0, 10, TimeUnit.SECONDS);
        emitter.onCompletion(() -> {
            logger.info("MSG: SseConnectCompletion | EmitterHash: {} |ID: {} | Date: {}", emitter.hashCode(), clientId, new Date());
            SseSession.onCompletion(clientId, future);
        });
        emitter.onTimeout(() -> {
            logger.error("MSG: SseConnectTimeout | EmitterHash: {} |ID: {} | Date: {}", emitter.hashCode(), clientId, new Date());
            SseSession.onError(clientId, new SseException("TimeOut(clientId: " + clientId + ")"));
        });
        emitter.onError(t -> {
            logger.error("MSG: SseConnectError | EmitterHash: {} |ID: {} | Date: {}", emitter.hashCode(), clientId, new Date());
            SseSession.onError(clientId, new SseException("Error(clientId: " + clientId + ")"));
        });
        return emitter;
    }

    /**
     * 发送数据
     *
     * @param clientId 客户端ID
     * @return
     */
    @Override
    public String send(String clientId) {
        if (SseSession.send(clientId, System.currentTimeMillis())) {
            return "Succeed!";
        }
        return "error";

    }

    /**
     * 关闭连接
     *
     * @param clientId 客户端ID
     * @return
     */
    @Override
    public String close(String clientId) {
        logger.info("MSG: SseConnectClose | ID: {} | Date: {}", clientId, new Date());
        if (SseSession.del(clientId)) return "Succeed!";
        return "Error!";
    }
}
