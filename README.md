@[TOC]
# SpringBoot-SSE开发实践
## SSE
SSE（Server-SentEvents，即服务器发送事件）是围绕只读Comet交互推出的API或者模式。SSE API用于创建到服务器的单向连接，服务器通过这个连接可以发送任意数量的数据。服务器响应的MIME类型必须是text/event-stream，而且是浏览器中的JavaScript API能解析格式输出。SSE支持短轮询、长轮询和HTTP流，而且能在断开连接时自动确定何时重新连接。

- **SSE特点**：实现简单、 单向通信、自动重连、···
- **业务场景**：客户端与服务端建立连接后，只需要服务端给客户端发送数据，客户端无需要给服务端发送数据
---
## 开发实践
### 项目框架
```
.
├── README.md
├── pom.xml
└── src
    └── main
        ├── java
        │   └── cn
        │       └── zuster
        │           └── sse
        │               ├── SseApplication.java【启动类】
        │               ├── controller         【控制器】
        │               ├── exception          【异常】
        │               ├── service            【服务接口】
        │               │   └── impl           【服务实现】
        │               ├── session            【SESSION管理】
        │               └── task               【任务管理】
        └── resources
            └── application.properties         【配置文件】

```
### 项目依赖
SpringBoot中已经有SseEmitter了，所以不需要额外引入其他包。
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.4.1</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>cn.zuster</groupId>
    <artifactId>my-demo-springboot-sse</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>my-demo-springboot-sse</name>
    <description>Spring Boot And SSE Demo</description>

    <properties>
        <java.version>1.8</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```
### Session管理
服务端和客户端建立连接时，往往会保持很多个SSE会话，为此，我们需要统一的对会话进行管理，此处我们使用 **ConcurrentHashMap**进行会话管理，其中 key 为客户端ID，value 为 **SseEmitter** 对象。当然 value 也可以按照业务进行封装。
主要方法说明：
-	boolean **exist**(String id)：检测指定的客户端Session是否存在。
-	boolean **add**(String id, SseEmitter emitter)：添加Session，如果有相同的客户端ID，则先结束掉之前的Session，重新建立新的Session。
-	boolean **del**(String id)：删除指定客户端Session。
-	boolean **send**(String id, Object msg)：给指定的客户端发送数据，注意，此处没有指定 MediaType ，即默认发送的就是 data，如果需要发送其他类型的数据，可进行自由扩展。
-	void **onCompletion**(String id, ScheduledFuture<?> future)：当 SseEmitter 触发 onCompletion时业务中需要处理的逻辑，包括停止线程池中的线程执行（比如心跳），移除缓存的Session等。
-	void **onError**(String id, SseException e)：当 SseEmitter 触发 onError 和 onTimeout 时业务中需要处理的逻辑，这里我取到缓存的Session，然后继续触发 completeWithError()，最终还是会执行到上面的 onCompletion() 方法中。
```java
package cn.zuster.sse.session;

// 省略 import

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
```
### 业务接口
一般使用 SSE 时的业务包括：客户端建立连接、给客户端发送数据、客户端终端连接，接口如下：
```java
package cn.zuster.sse.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 相关业务接口
 *
 * @author zuster
 * @date 2021/1/5
 */
public interface SseService {
    /**
     * 新建连接
     *
     * @param clientId 客户端ID
     * @return
     */
    SseEmitter start(String clientId);

    /**
     * 发送数据
     *
     * @param clientId 客户端ID
     * @return
     */
    String send(String clientId);

    /**
     * 关闭连接
     *
     * @param clientId 客户端ID
     * @return
     */
    String close(String clientId);
}
```
### 业务实现
业务实现简要介绍：
- ScheduledExecutorService **heartbeatExecutors** ：使用线程池来管理客户端连接后给客户端发送心跳，我们的业务场景是建立连接后服务端需每隔10秒给客户单发送一个消息，若连续3次未收到心跳，则客户端中断连接，重新进行连接。很多地方使用while(true)...Thread.sleep()方式来实现此业务，但是在真实业务中问题很多，没有用线程池优雅和高效。
- SseEmitter **start**(String clientId) ：客户端建立连接，建立连接后，需要将缓存Session，同时设置心跳（如果有其他业务也可以在这里设置），另外在onCompletion、onTimeout、onError回调事件中处理相关的业务。**强调：一定要在回调中处理掉Session和之前设置的Task，否则很容易OOM！**
- String **send**(String clientId)：向指定客户端发送消息。
- String **close**(String clientId)：关闭连接。
```java
package cn.zuster.sse.service.impl;

// 省略 import

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
    private static ScheduledExecutorService heartbeatExecutors = Executors.newScheduledThreadPool(8);

    /**
     * 新建连接
     *
     * @param clientId 客户端ID
     * @return
     */
    @Override
    public SseEmitter start(String clientId) {
        // 设置为0L为永不超时
        // 次数设置30秒超时,方便测试 timeout 事件
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
```

### 任务
我们的业务为建立连接后发送心跳数据，此处我只设置了客户端ID，如果业务中有其他数据可以扩充。
```java
package cn.zuster.sse.task;

// 省略 import

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
```
### 异常
```java
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
```

### 控制器
```java
package cn.zuster.sse.controller;

// 省略 import

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
```
---
## 测试
代码就上面这么多了，启动起来测试一下吧。
- 建立连接：http://localhost:8080/sse/start?clientId=888
- 关闭连接：http://localhost:8080/sse/end?clientId=111

需要测试的点包括：
- 同时开启多个连接
- 启动两个相同的连接
- 启动后直接关掉
- 启动后等待30秒超时
- 发送消息（我controller中删了发送消息的，可以自行加上试试）
- 通过关闭连接接口关闭连接

好了，敬请的玩吧
