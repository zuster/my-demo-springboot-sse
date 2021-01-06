package cn.zuster.sse.task;

import cn.zuster.sse.session.SseSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * 心跳任务
 *
 * @author zuster
 * @date 2021/1/5
 */
public class HeartBeatTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(HeartBeatTask.class);

    private final String clientId;

    public HeartBeatTask(String clientId) {
        // 这里可以按照业务传入需要的数据
        this.clientId = clientId;
    }

    @Override
    public void run() {
        logger.info("MSG: SseHeartbeat | ID: {} | Date: {}", clientId, new Date());
        SseSession.send(clientId, "ping");
    }
}
