package cn.zuster.sse.controller;

import cn.zuster.sse.service.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE测试控制器
 *
 * @author songyh
 * @date 2021/1/5
 */
@RestController
@RequestMapping("sse")
public class SseTestController {
    private static final Logger logger = LoggerFactory.getLogger(SseTestController.class);

    @Autowired
    private SseService sseService;

    @RequestMapping("start")
    public SseEmitter start(@RequestParam String clientId) {
        return sseService.start(clientId);
    }

    /**
     * 将SseEmitter对象设置成完成
     *
     * @param clientId
     * @return
     */
    @RequestMapping("/end")
    public String close(String clientId) {
        return sseService.close(clientId);
    }

}
